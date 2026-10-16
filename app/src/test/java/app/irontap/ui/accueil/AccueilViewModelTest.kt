package app.irontap.ui.accueil

import android.content.Context
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Accueil (spec §4.1), sur une base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccueilViewModelTest {

    private val vms = ViewModelsDeTest()
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao

    private val horloge = object : Horloge {
        override fun maintenant(): Instant = Instant.parse("2026-10-13T17:00:00Z")
        override fun realtime() = 10_000L
    }

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BaseIronTap::class.java)
            .build()
        dao = base.entrainements()
    }

    @After
    fun fermer() {
        vms.vider()
        base.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.ecran(): AccueilViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return vms.garder(AccueilViewModel(dao, horloge)).also { vm -> backgroundScope.launch { vm.etat.collect {} } }
    }

    /** Attend (en temps réel : Room écrit sur ses propres fils) que [condition] soit vraie. */
    private suspend fun <T> StateFlow<T>.attendre(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(condition) } }

    private suspend fun termine(date: LocalDate, libelle: String?) {
        val id = checkNotNull(dao.creerEntrainement(date, libelle, horloge.maintenant()))
        dao.enregistrerSerie(id, "Squat", 100.0, 5, Suite.NouvelleSerie, horloge.maintenant(), 0)
        dao.terminer(id, horloge.maintenant())
    }

    @Test
    fun `historique du plus recent au plus ancien, et entrainement en cours a part`() = runTest {
        termine(LocalDate.of(2026, 10, 5), "Dos / biceps")
        termine(LocalDate.of(2026, 10, 12), null)
        val enCours = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 13), "Jambes", horloge.maintenant()))
        val vm = ecran()

        val etat = vm.etat.attendre { it is EtatAccueil.Pret && it.historique.size == 2 } as EtatAccueil.Pret
        assertEquals(listOf(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 5)), etat.historique.map { it.date })
        assertEquals(enCours, etat.enCours?.entrainement?.id)
        assertEquals("Exercice 1 · Série 1", etat.enCours?.ligne)
    }

    @Test
    fun `terminer l'entrainement en cours avant d'en commencer un nouveau`() = runTest {
        val id = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 13), null, horloge.maintenant()))
        dao.enregistrerSerie(id, "Squat", 100.0, 5, Suite.NouvelleSerie, horloge.maintenant(), 0)
        val vm = ecran()
        vm.etat.attendre { (it as? EtatAccueil.Pret)?.enCours != null }

        vm.terminerEnCours().join()

        assertNull(dao.lireEnCours())
        // Les deux requêtes émettent chacune leur tour : on attend l'état final.
        val etat = vm.etat.attendre { it is EtatAccueil.Pret && it.historique.isNotEmpty() } as EtatAccueil.Pret
        assertNull(etat.enCours)
        assertEquals(listOf(id), etat.historique.map { it.id })
    }
}
