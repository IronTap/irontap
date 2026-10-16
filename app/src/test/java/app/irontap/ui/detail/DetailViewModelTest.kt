package app.irontap.ui.detail

import android.content.Context
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.Horloge
import app.irontap.ui.ViewModelsDeTest
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Détail d'un entraînement et édition de ses séries (spec §4.4, §4.5), sur une base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DetailViewModelTest {

    private val vms = ViewModelsDeTest()
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao
    private var id = 0L

    private val horloge = object : Horloge {
        override fun maintenant(): Instant = Instant.parse("2026-10-13T18:00:00Z")
        override fun realtime() = 0L
    }

    @Before
    fun ouvrir() = runTest {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BaseIronTap::class.java)
            .build()
        dao = base.entrainements()
        // Tirage vertical : 40 × 15, 45 × 12, 45 × 10 ; Curl haltères : 12 × 12.
        id = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 12), "Dos / biceps", Instant.EPOCH))
        listOf(40.0 to 15, 45.0 to 12, 45.0 to 10).forEach { (poids, reps) ->
            dao.enregistrerSerie(id, "Tirage vertical", poids, reps, Suite.NouvelleSerie, Instant.EPOCH, 0)
        }
        dao.enregistrerSerie(id, "Tirage vertical", 45.0, 8, Suite.NouvelExercice, Instant.EPOCH, 0)
        dao.supprimerSerie(dao.lireComplet(id)!!.exercices[0].series[3].id)
        dao.enregistrerSerie(id, "Curl haltères", 12.0, 12, Suite.NouvelleSerie, Instant.EPOCH, 0)
        dao.terminer(id, Instant.EPOCH)
    }

    @After
    fun fermer() {
        vms.vider()
        base.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.ecran(): DetailViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return vms.garder(DetailViewModel(dao, horloge, id)).also { vm ->
            backgroundScope.launch { vm.etat.collect {} }
            backgroundScope.launch { vm.suggestions.collect {} }
        }
    }

    private suspend fun <T> StateFlow<T>.attendre(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(condition) } }

    private suspend fun DetailViewModel.pret() = etat.attendre { it is EtatDetail.Pret } as EtatDetail.Pret

    private suspend fun valeurs() = dao.lireComplet(id)!!.exercices.map { e -> e.exercice.nom to e.series.map { it.poidsKg to it.repetitions } }

    @Test
    fun `modifier une serie, pre-remplie, modifie_le renseigne`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        vm.ouvrirEdition(tirage.exercice, tirage.series[1])
        assertEquals("45" to "12", vm.poids.text.toString() to vm.repetitions.text.toString())

        vm.ajouterAuPoids(5)
        vm.repetitions.setTextAndPlaceCursorAtEnd("10")
        vm.enregistrerEdition()

        vm.panneaux.attendre { it.edition == null }
        val serie = dao.lireComplet(id)!!.exercices[0].series[1]
        assertEquals(50.0 to 10, serie.poidsKg to serie.repetitions)
        assertEquals(horloge.maintenant(), serie.modifieLe)
    }

    @Test
    fun `repetitions obligatoires, poids facultatif`() = runTest {
        val vm = ecran()
        val curl = vm.pret().complet.exercices[1]
        vm.ouvrirEdition(curl.exercice, serie = null)
        assertEquals("" to "", vm.poids.text.toString() to vm.repetitions.text.toString())

        vm.enregistrerEdition()
        assertTrue(vm.panneaux.value.erreurRepetitions)
        assertEquals(1, dao.lireComplet(id)!!.exercices[1].series.size)

        // L'erreur disparaît dès que le champ est modifié ; sans poids, c'est le poids du corps.
        vm.ajouterAuxRepetitions(10)
        Snapshot.sendApplyNotifications()
        assertEquals(false, vm.panneaux.value.erreurRepetitions)
        vm.enregistrerEdition()
        vm.panneaux.attendre { it.edition == null }
        assertEquals(listOf(12.0 to 12, null to 10), valeurs()[1].second)
    }

    @Test
    fun `supprimer une serie apres confirmation, les suivantes sont renumerotees`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        vm.demanderSuppression(tirage.exercice, tirage.series[1])
        assertEquals("45 kg × 12", vm.panneaux.value.suppression?.description)
        vm.annulerSuppression()
        assertNull(vm.panneaux.value.suppression)

        vm.demanderSuppression(tirage.exercice, tirage.series[1])
        vm.confirmerSuppression()
        // La ligne reste ouverte le temps que la série disparaisse, sans dialogue.
        assertEquals(tirage.series[1].id, vm.panneaux.value.serieEnSuppression)
        assertNull(vm.panneaux.value.suppression)

        val apres = vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.get(0)?.series?.size == 2 } as EtatDetail.Pret
        assertEquals(listOf(1, 2), apres.complet.exercices[0].series.map { it.ordre })
        assertEquals(listOf(40.0 to 15, 45.0 to 10), apres.complet.exercices[0].series.map { it.poidsKg to it.repetitions })
    }

    @Test
    fun `supprimer depuis la feuille d'edition la referme, un exercice vide disparait`() = runTest {
        val vm = ecran()
        val curl = vm.pret().complet.exercices[1]
        vm.ouvrirEdition(curl.exercice, curl.series[0])
        vm.demanderSuppression(curl.exercice, curl.series[0])
        vm.confirmerSuppression()

        assertNull(vm.panneaux.value.edition)
        vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.size == 1 }
        assertEquals(listOf("Tirage vertical"), valeurs().map { it.first })
    }

    @Test
    fun `renommer un exercice, avec autocompletion et nom obligatoire`() = runTest {
        val vm = ecran()
        val curl = vm.pret().complet.exercices[1]
        vm.ouvrirRenommage(curl.exercice)
        Snapshot.sendApplyNotifications()
        assertTrue("pas de suggestions sur le nom actuel", vm.suggestions.value.isEmpty())

        vm.nom.setTextAndPlaceCursorAtEnd("   ")
        Snapshot.sendApplyNotifications()
        vm.renommer()
        assertTrue(vm.panneaux.value.erreurNom)

        vm.nom.setTextAndPlaceCursorAtEnd("tir")
        Snapshot.sendApplyNotifications()
        val suggestion = vm.suggestions.attendre { it.isNotEmpty() }.single()
        assertEquals("Tirage vertical", suggestion.nom)
        vm.choisir(suggestion)
        vm.nom.setTextAndPlaceCursorAtEnd("Tirage vertical prise serrée ")
        vm.renommer()

        vm.panneaux.attendre { it.renommage == null }
        assertEquals("Tirage vertical prise serrée", valeurs()[1].first)
    }

    @Test
    fun `supprimer l'entrainement ferme l'ecran`() = runTest {
        val vm = ecran()
        vm.pret()
        vm.demanderSuppressionEntrainement()
        assertTrue(vm.panneaux.value.suppressionEntrainement)
        vm.annulerSuppressionEntrainement()
        assertEquals(false, vm.panneaux.value.suppressionEntrainement)

        vm.demanderSuppressionEntrainement()
        vm.supprimerEntrainement()
        assertEquals(false, vm.panneaux.value.suppressionEntrainement)
        vm.etat.attendre { it == EtatDetail.Introuvable }
        assertNull(dao.lireComplet(id))
    }
}
