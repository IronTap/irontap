package app.irontap.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.Horloge
import app.irontap.export.Exporteur
import app.irontap.logique.FormatExport
import app.irontap.ui.ViewModelsDeTest
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Export (spec §5) : portée, formats, partage et enregistrement, sur une base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vms = ViewModelsDeTest()
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao
    private var dos = 0L

    private val horloge = object : Horloge {
        override fun maintenant(): Instant = Instant.parse("2026-10-14T18:00:00Z")
        override fun realtime() = 0L
    }

    @Before
    fun ouvrir() = runTest {
        base = Room.inMemoryDatabaseBuilder(context, BaseIronTap::class.java).build()
        dao = base.entrainements()
        val jambes = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 9), null, Instant.EPOCH))
        dao.enregistrerSerie(jambes, "Squat", 80.0, 8, Suite.NouvelleSerie, Instant.EPOCH, 0)
        dao.terminer(jambes, Instant.EPOCH)
        dos = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 12), "Dos / biceps", Instant.EPOCH))
        dao.enregistrerSerie(dos, "Tirage vertical", 40.0, 15, Suite.NouvelleSerie, Instant.EPOCH, 0)
        dao.enregistrerSerie(dos, "Tirage vertical", 12.5, 12, Suite.NouvelleSerie, Instant.EPOCH, 0)
        dao.terminer(dos, Instant.EPOCH)
        // L'entraînement en cours n'est pas exporté.
        val enCours = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 14), null, Instant.EPOCH))
        dao.enregistrerSerie(enCours, "Développé couché", 60.0, 10, Suite.NouvelleSerie, Instant.EPOCH, 0)
    }

    @After
    fun fermer() {
        vms.vider()
        base.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.export(id: Long?, etatSauve: SavedStateHandle = SavedStateHandle()): ExportViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return vms.garder(ExportViewModel(dao, horloge, Exporteur(context), id, etatSauve)).also { vm ->
            backgroundScope.launch { vm.etat.collect {} }
        }
    }

    private suspend fun <T> hors(bloc: suspend () -> T): T = withContext(Dispatchers.Default) { withTimeout(5_000) { bloc() } }

    private suspend fun <T> StateFlow<T>.attendre(condition: (T) -> Boolean): T = hors { first(condition) }

    private suspend fun ExportViewModel.pret() = etat.attendre { it is EtatExport.Pret } as EtatExport.Pret

    @Test
    fun `depuis l'accueil, tout l'historique termine, du plus recent au plus ancien`() = runTest {
        val vm = export(id = null)
        val pret = vm.pret()
        assertEquals("Tout l’historique · 2 entraînements", pret.portee)
        assertNull(pret.dateEntrainement)
        val texte = pret.contenus.getValue(FormatExport.Texte)
        assertTrue(texte.startsWith("Lun. 12 oct. 2026 — Dos / biceps"))
        assertTrue(texte.contains("Ven. 9 oct. 2026\n\nSquat\n  80 kg × 8"))
        assertFalse(texte.contains("Développé"))
        assertEquals("irontap-historique-2026-10-14.txt", vm.nomFichier(pret))
    }

    @Test
    fun `depuis le detail, cet entrainement seul`() = runTest {
        val vm = export(dos)
        val pret = vm.pret()
        assertEquals("Lun. 12 oct. 2026 · Dos / biceps", pret.portee)
        assertEquals(
            "date;libelle_entrainement;exercice;ordre_exercice;ordre_serie;palier;poids_kg;repetitions;entrainement;superset;role_superset\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;1;1;40;15;1;;\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;2;1;12.5;12;1;;\r\n",
            pret.contenus.getValue(FormatExport.Csv),
        )
        vm.choisir(FormatExport.Csv)
        assertEquals("irontap-2026-10-12.csv", vm.nomFichier(pret))
    }

    @Test
    fun `historique vide`() = runTest {
        dao.supprimerEntrainement(dos)
        val vm = export(dos)
        assertEquals(EtatExport.Vide, vm.etat.attendre { it != EtatExport.Chargement })
        assertNull(hors { vm.partager() })
        assertEquals("Rien à exporter", vm.operation.value.erreur)
    }

    @Test
    fun `partager du texte court l'envoie tel quel`() = runTest {
        val vm = export(dos)
        vm.pret()
        val intention = hors { vm.partager() }!!
        assertEquals(Intent.ACTION_SEND, intention.action)
        assertEquals("text/plain", intention.type)
        assertTrue(intention.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("Lun. 12 oct. 2026 — Dos / biceps\n\nTirage vertical"))
        assertNull(IntentCompat.getParcelableExtra(intention, Intent.EXTRA_STREAM, Uri::class.java))
        // Réussi : les boutons restent inactifs pendant la fermeture de la feuille, qui libère l'opération.
        assertTrue(vm.operation.value.occupe)
        assertNull(hors { vm.partager() })
        vm.reinitialiser()
        assertFalse(vm.operation.value.occupe)
    }

    @Test
    fun `partager du CSV joint un fichier du cache, lisible par le destinataire seulement`() = runTest {
        val vm = export(dos)
        vm.pret()
        // Un export précédent est effacé.
        val ancien = File(context.cacheDir, "exports/ancien.csv").apply { parentFile!!.mkdirs(); writeText("x") }
        vm.choisir(FormatExport.Csv)
        val intention = hors { vm.partager() }!!
        assertEquals("text/csv", intention.type)
        val uri = IntentCompat.getParcelableExtra(intention, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.exports", uri.authority)
        assertEquals(uri, intention.clipData!!.getItemAt(0).uri)
        assertTrue(intention.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNull(intention.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(ancien.exists())
        val fichier = File(context.cacheDir, "exports/irontap-2026-10-12.csv")
        assertEquals("\uFEFFdate;", fichier.readText().take(6))
    }

    @Test
    fun `enregistrer remplace le contenu du document choisi`() = runTest {
        val vm = export(dos)
        vm.pret()
        val document = File(context.filesDir, "choisi.csv").apply { writeText("ancien contenu bien plus long que le nouveau ".repeat(50)) }
        vm.choisir(FormatExport.Csv)
        assertTrue(vm.reserver())
        assertFalse("Double appui", vm.reserver())
        assertTrue(hors { vm.enregistrer(Uri.fromFile(document)) })
        assertEquals(
            "\uFEFFdate;libelle_entrainement;exercice;ordre_exercice;ordre_serie;palier;poids_kg;repetitions;entrainement;superset;role_superset\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;1;1;40;15;1;;\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;2;1;12.5;12;1;;\r\n",
            document.readText(),
        )
        assertEquals(OperationExport(occupe = true), vm.operation.value)
    }

    @Test
    fun `un enregistrement impossible affiche une erreur et libere la feuille`() = runTest {
        val vm = export(dos)
        vm.pret()
        assertTrue(vm.reserver())
        val introuvable = Uri.fromFile(File(context.filesDir, "dossier/absent/choisi.txt"))
        assertFalse(hors { vm.enregistrer(introuvable) })
        assertEquals(OperationExport(occupe = false, erreur = "Enregistrement impossible : le fichier n’a pas pu être écrit"), vm.operation.value)
        // Choisir un format efface l'erreur.
        vm.choisir(FormatExport.Csv)
        assertNull(vm.operation.value.erreur)
    }

    @Test
    fun `selecteur ferme sans choix, puis feuille refermee`() = runTest {
        val etatSauve = SavedStateHandle()
        val vm = export(dos, etatSauve)
        vm.choisir(FormatExport.Csv)
        assertTrue(vm.reserver())
        vm.abandonner()
        assertEquals(OperationExport(), vm.operation.value)
        assertEquals(FormatExport.Csv, vm.format.value)
        // Le format choisi survit à la mort du processus (sélecteur de fichier ouvert)…
        assertEquals(FormatExport.Csv, export(dos, etatSauve).format.value)
        // … mais la feuille rouverte repart du texte, le format par défaut.
        vm.reinitialiser()
        assertEquals(FormatExport.Texte, vm.format.value)
    }
}
