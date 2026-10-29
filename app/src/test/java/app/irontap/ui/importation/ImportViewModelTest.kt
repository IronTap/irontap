package app.irontap.ui.importation

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.Horloge
import app.irontap.export.FichierTropVolumineux
import app.irontap.export.Importeur
import app.irontap.logique.ErreurImport
import app.irontap.logique.exporterCsv
import app.irontap.logique.exporterTexte
import app.irontap.ui.ViewModelsDeTest
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Import d'un export CSV (DECISIONS.md « Import »), sur une base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vms = ViewModelsDeTest()
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao
    private var enCours = 0L

    private val horloge = object : Horloge {
        override fun maintenant(): Instant = Instant.parse("2026-10-28T18:00:00Z")
        override fun realtime() = 0L
    }

    private val document = Uri.parse("content://test/irontap-historique.csv")

    private fun ressource(nom: String) = checkNotNull(javaClass.getResource("/import/$nom")).readText()

    @Before
    fun ouvrir() = runTest {
        base = Room.inMemoryDatabaseBuilder(context, BaseIronTap::class.java).build()
        dao = base.entrainements()
        val jambes = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 9), "Jambes", Instant.EPOCH))
        dao.enregistrerSerie(jambes, "Squat", 80.0, 8, Suite.NouvelleSerie, Instant.EPOCH, 0)
        dao.terminer(jambes, Instant.EPOCH)
        enCours = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 28), null, Instant.EPOCH))
        dao.enregistrerSerie(enCours, "Développé couché", 60.0, 10, Suite.NouvelleSerie, Instant.EPOCH, 0)
    }

    @After
    fun fermer() {
        vms.vider()
        base.close()
        Dispatchers.resetMain()
    }

    /** ViewModel dont le document choisi a pour contenu [contenu] (ou lève l'exception de [lire]). */
    private fun TestScope.import(lire: (Uri) -> String): ImportViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return vms.garder(ImportViewModel(dao, horloge, lire))
    }

    private suspend fun <T> StateFlow<T>.attendre(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(condition) } }

    private suspend fun ImportViewModel.resultat() = etat.attendre { it != EtatImport.EnCours && it != EtatImport.Aucun }

    @Test
    fun `fichier d'exemple, confirmation puis ajout, aller-retour exact, entrainement en cours intact`() = runTest {
        val exemple = ressource("exemple-import.csv")
        val vm = import { exemple }
        val avant = dao.lireEnCoursComplet()

        vm.analyser(document)
        val confirmation = vm.resultat() as EtatImport.Confirmation
        assertEquals(4, confirmation.entrainements.size)
        assertEquals(0, confirmation.dejaPresents)
        assertEquals(LocalDate.of(2026, 9, 20) to LocalDate.of(2026, 9, 24), confirmation.debut to confirmation.fin)

        vm.confirmer()
        assertEquals(EtatImport.Termine(4), vm.resultat())
        vm.fermer()
        assertEquals(EtatImport.Aucun, vm.etat.value)

        val historique = dao.lireHistoriqueComplet()
        assertEquals(5, historique.size)
        assertEquals(avant, dao.lireEnCoursComplet())
        // Réexporté, l'import redonne le fichier à l'identique : supersets, tours qui sautent, ordre des séances du même jour.
        val importes = historique.filter { it.entrainement.libelle.orEmpty().startsWith("Test import") }
        assertEquals(exemple.removePrefix("\uFEFF"), exporterCsv(importes))
        assertTrue(importes.all { it.entrainement.termineLe == horloge.maintenant() })
        val supersets = importes.flatMap { e -> e.exercices.mapNotNull { it.exercice.supersetId } }.groupBy { it }
        assertEquals(listOf(2, 2, 2), supersets.values.map { it.size })
    }

    @Test
    fun `reimporter le meme fichier n'ajoute rien, un fichier en partie deja present ajoute le reste`() = runTest {
        val exemple = ressource("exemple-import.csv")
        val vm = import { exemple }
        vm.analyser(document)
        vm.resultat()
        vm.confirmer()
        vm.resultat()
        vm.fermer()

        vm.analyser(document)
        assertEquals(EtatImport.RienAImporter(4), vm.resultat())
        vm.fermer()

        // Tout l'historique réexporté, plus l'ancien format : seul celui-ci est nouveau.
        val tout = exporterCsv(dao.lireHistoriqueComplet())
        val ancien = ressource("exemple-import-ancien.csv")
        val autre = import { if (it == document) tout else ancien }
        autre.analyser(document)
        assertEquals(EtatImport.RienAImporter(5), autre.resultat())
        autre.fermer()
        autre.analyser(Uri.parse("content://test/ancien.csv"))
        val confirmation = autre.resultat() as EtatImport.Confirmation
        assertEquals(1 to 0, confirmation.entrainements.size to confirmation.dejaPresents)
    }

    @Test
    fun `fichier en erreur, rien n'est importe`() = runTest {
        val vm = import { ressource("exemple-import-erreurs.csv") }
        vm.analyser(document)
        val echec = vm.resultat() as EtatImport.Echec
        assertEquals(listOf(3, 4, 5, 7), echec.erreurs.map { it.ligne })
        assertEquals(1, dao.lireHistoriqueComplet().size)

        val export = exporterTexte(dao.lireHistoriqueComplet())
        val texte = import { export }
        texte.analyser(document)
        assertTrue((texte.resultat() as EtatImport.Echec).erreurs.single().message.contains("Seul le format CSV s’importe"))
    }

    @Test
    fun `fichier illisible ou trop gros`() = runTest {
        val illisible = import { throw IOException("refusé") }
        illisible.analyser(document)
        assertEquals(EtatImport.Echec(listOf(ErreurImport(null, "Le fichier n’a pas pu être lu."))), illisible.resultat())

        val gros = import { throw FichierTropVolumineux() }
        gros.analyser(document)
        assertEquals("Ce fichier est trop volumineux pour être un export d’IronTap.", (gros.resultat() as EtatImport.Echec).erreurs.single().message)
    }

    @Test
    fun `lecture du document choisi, UTF-8 ou Windows-1252`() {
        val utf8 = File(context.cacheDir, "utf8.csv").apply { writeText("\uFEFFÉcarté;1", Charsets.UTF_8) }
        assertEquals("\uFEFFÉcarté;1", Importeur(context).lire(Uri.fromFile(utf8)))
        // CSV réenregistré par Excel en « CSV (séparateur : point-virgule) ».
        val windows = File(context.cacheDir, "windows.csv").apply { writeBytes("Écarté;1".toByteArray(charset("windows-1252"))) }
        assertEquals("Écarté;1", Importeur(context).lire(Uri.fromFile(windows)))
        assertNotEquals("Écarté;1", String("Écarté;1".toByteArray(charset("windows-1252")), Charsets.UTF_8))
    }
}
