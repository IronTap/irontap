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
import app.irontap.donnees.ValeursPalier
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
        dao.supprimerSerie(dao.lireComplet(id)!!.exercices[0].series[3].serie.id)
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

    private suspend fun valeurs() = dao.lireComplet(id)!!.exercices.map { e -> e.exercice.nom to e.series.map { it.valeurs.single().let { p -> p.poidsKg to p.repetitions } } }

    @Test
    fun `modifier une serie, pre-remplie, modifie_le renseigne`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        vm.ouvrirEdition(tirage.exercice, tirage.series[1])
        assertEquals("45" to "12", vm.paliers.single().let { it.poids.text.toString() to it.repetitions.text.toString() })

        vm.ajouterAuPoids(5)
        vm.paliers.single().repetitions.setTextAndPlaceCursorAtEnd("10")
        vm.enregistrerEdition()

        vm.panneaux.attendre { it.edition == null }
        val serie = dao.lireComplet(id)!!.exercices[0].series[1]
        assertEquals(listOf(ValeursPalier(50.0, 10)), serie.valeurs)
        assertEquals(horloge.maintenant(), serie.serie.modifieLe)
    }

    @Test
    fun `repetitions obligatoires, poids facultatif`() = runTest {
        val vm = ecran()
        val curl = vm.pret().complet.exercices[1]
        vm.ouvrirEdition(curl.exercice, serie = null)
        assertEquals("" to "", vm.paliers.single().let { it.poids.text.toString() to it.repetitions.text.toString() })

        vm.enregistrerEdition()
        assertEquals(setOf(vm.paliers.single().cle), vm.panneaux.value.paliersEnErreur.keys)
        assertEquals(1, dao.lireComplet(id)!!.exercices[1].series.size)

        // L'erreur disparaît dès que le champ est modifié ; sans poids, c'est le poids du corps.
        vm.ajouterAuxRepetitions(10)
        Snapshot.sendApplyNotifications()
        assertTrue(vm.panneaux.value.paliersEnErreur.isEmpty())
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
        assertEquals(tirage.series[1].serie.id, vm.panneaux.value.serieEnSuppression)
        assertNull(vm.panneaux.value.suppression)

        val apres = vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.get(0)?.series?.size == 2 } as EtatDetail.Pret
        assertEquals(listOf(1, 2), apres.complet.exercices[0].series.map { it.serie.ordre })
        assertEquals(listOf(40.0 to 15, 45.0 to 10), apres.complet.exercices[0].series.map { it.valeurs.single().let { p -> p.poidsKg to p.repetitions } })
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

    // --- Édition d'une dégressive (7·D, spec §4.5.1) ---

    private fun DetailViewModel.lignes() = paliers.map { it.poids.text.toString() to it.repetitions.text.toString() }

    private suspend fun seriesTirage() = dao.lireComplet(id)!!.exercices[0].series.map { it.valeurs }

    @Test
    fun `degressive en tableau, modifier, ajouter et retirer des paliers, jamais le palier 1`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        dao.modifierSerie(tirage.series[2].serie.id, listOf(ValeursPalier(45.0, 10), ValeursPalier(30.0, 8), ValeursPalier(20.0, 6)), horloge.maintenant())
        val degressive = vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.get(0)?.series?.get(2)?.paliers?.size == 3 }
            .let { (it as EtatDetail.Pret).complet.exercices[0].series[2] }

        vm.ouvrirEdition(tirage.exercice, degressive)
        assertEquals(true, vm.panneaux.value.edition?.tableau)
        assertEquals(listOf("45" to "10", "30" to "8", "20" to "6"), vm.lignes())

        // Le palier 1 ne se retire pas ; un palier dégressif, si.
        vm.retirerPalier(vm.paliers[0].cle)
        vm.retirerPalier(vm.paliers[1].cle)
        assertEquals(listOf("45" to "10", "20" to "6"), vm.lignes())
        vm.paliers[1].poids.setTextAndPlaceCursorAtEnd("22,5")
        // Ligne ajoutée laissée vide : ignorée.
        val ajoutee = vm.ajouterPalier()
        assertEquals(ajoutee, vm.paliers.last().cle)
        assertEquals("" to "", vm.lignes().last())
        vm.ajouterPalier()
        vm.paliers.last().repetitions.setTextAndPlaceCursorAtEnd("4")
        vm.enregistrerEdition()

        vm.panneaux.attendre { it.edition == null }
        assertEquals(listOf(ValeursPalier(45.0, 10), ValeursPalier(22.5, 6), ValeursPalier(null, 4)), seriesTirage()[2])
    }

    @Test
    fun `annuler l'edition d'une degressive ne change rien`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        val paliers = listOf(ValeursPalier(45.0, 10), ValeursPalier(30.0, 8), ValeursPalier(20.0, 6))
        dao.modifierSerie(tirage.series[2].serie.id, paliers, horloge.maintenant())
        val degressive = (vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.get(0)?.series?.get(2)?.paliers?.size == 3 }
            as EtatDetail.Pret).complet.exercices[0].series[2]
        val avant = seriesTirage()

        vm.ouvrirEdition(tirage.exercice, degressive)
        vm.retirerPalier(vm.paliers[1].cle)
        vm.paliers[0].poids.setTextAndPlaceCursorAtEnd("99")
        vm.ajouterPalier()
        vm.paliers.last().repetitions.setTextAndPlaceCursorAtEnd("4")
        vm.fermerEdition()
        assertNull(vm.panneaux.value.edition)

        // Témoin : les écritures du ViewModel s'enchaînent, une écriture lancée par Annuler serait passée avant.
        vm.ouvrirRenommage(tirage.exercice)
        vm.nom.setTextAndPlaceCursorAtEnd("Tirage poitrine")
        vm.renommer()
        vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.get(0)?.exercice?.nom == "Tirage poitrine" }
        assertEquals(avant, seriesTirage())

        // Rouverte, la feuille retrouve les paliers enregistrés.
        vm.ouvrirEdition(tirage.exercice, degressive)
        assertEquals(listOf("45" to "10", "30" to "8", "20" to "6"), vm.lignes())
    }

    @Test
    fun `serie simple passee en degressive, puis revenue simple en retirant ses paliers`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        vm.ouvrirEdition(tirage.exercice, tirage.series[0])
        assertEquals(false, vm.panneaux.value.edition?.tableau)

        vm.ajouterPalier()
        assertEquals(true, vm.panneaux.value.edition?.tableau)
        vm.paliers[1].poids.setTextAndPlaceCursorAtEnd("30")
        vm.paliers[1].repetitions.setTextAndPlaceCursorAtEnd("8")
        vm.enregistrerEdition()
        vm.panneaux.attendre { it.edition == null }
        assertEquals(listOf(ValeursPalier(40.0, 15), ValeursPalier(30.0, 8)), seriesTirage()[0])

        val degressive = (vm.etat.attendre { (it as? EtatDetail.Pret)?.complet?.exercices?.get(0)?.series?.get(0)?.paliers?.size == 2 }
            as EtatDetail.Pret).complet.exercices[0].series[0]
        vm.ouvrirEdition(tirage.exercice, degressive)
        vm.retirerPalier(vm.paliers[1].cle)
        vm.enregistrerEdition()
        vm.panneaux.attendre { it.edition == null }
        assertEquals(listOf(ValeursPalier(40.0, 15)), seriesTirage()[0])
    }

    @Test
    fun `repetitions manquantes sur un palier, signalees jusqu'a ce que le champ change`() = runTest {
        val vm = ecran()
        val tirage = vm.pret().complet.exercices[0]
        val avant = seriesTirage()
        vm.ouvrirEdition(tirage.exercice, tirage.series[1])
        vm.ajouterPalier()
        val fautif = vm.paliers[1]
        fautif.poids.setTextAndPlaceCursorAtEnd("30")
        vm.enregistrerEdition()

        assertEquals(setOf(fautif.cle), vm.panneaux.value.paliersEnErreur.keys)
        assertEquals(avant, seriesTirage())
        // Une autre ligne modifiée ne fait pas disparaître l'erreur ; la ligne fautive, si.
        vm.paliers[0].repetitions.setTextAndPlaceCursorAtEnd("11")
        Snapshot.sendApplyNotifications()
        assertEquals(setOf(fautif.cle), vm.panneaux.value.paliersEnErreur.keys)
        fautif.repetitions.setTextAndPlaceCursorAtEnd("8")
        Snapshot.sendApplyNotifications()
        assertTrue(vm.panneaux.value.paliersEnErreur.isEmpty())
    }
}
