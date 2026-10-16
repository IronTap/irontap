package app.irontap.ui.entrainement

import android.content.Context
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Horloge
import app.irontap.ui.ViewModelsDeTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

/** Écran 2 : création et modification d'un entraînement (spec §4.2), sur une base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FormulaireEntrainementViewModelTest {

    private val vms = ViewModelsDeTest()
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao

    private val horloge = object : Horloge {
        override fun maintenant(): Instant = LocalDate.of(2026, 10, 13).atTime(18, 30).atZone(ZoneId.systemDefault()).toInstant()
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

    private fun kotlinx.coroutines.test.TestScope.formulaire(id: Long? = null): FormulaireEntrainementViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return vms.garder(FormulaireEntrainementViewModel(dao, horloge, id))
    }

    private suspend fun <T> StateFlow<T>.attendre(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(condition) } }

    @Test
    fun `creation, date du jour par defaut, libelle nettoye`() = runTest {
        val vm = formulaire()
        assertEquals(EtatFormulaire(date = LocalDate.of(2026, 10, 13), modification = false), vm.etat.value)

        vm.libelle.setTextAndPlaceCursorAtEnd("  Jambes ")
        vm.choisirDate(LocalDate.of(2026, 10, 12))
        vm.valider()
        vm.valider()

        assertEquals(FinFormulaire.Lance, vm.etat.attendre { it.fin != null }.fin)
        val cree = checkNotNull(dao.lireEnCours())
        assertEquals(LocalDate.of(2026, 10, 12) to "Jambes", cree.date to cree.libelle)
        assertEquals(horloge.maintenant(), cree.creeLe)
    }

    @Test
    fun `creation sans libelle`() = runTest {
        val vm = formulaire()
        vm.libelle.setTextAndPlaceCursorAtEnd("   ")
        vm.valider()
        vm.etat.attendre { it.fin != null }
        assertNull(dao.lireEnCours()?.libelle)
    }

    @Test
    fun `modification, valeurs existantes puis enregistrement`() = runTest {
        val id = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 12), "Dos / biceps", horloge.maintenant()))
        val vm = formulaire(id)
        assertTrue(vm.etat.value.modification)

        vm.etat.attendre { it.date != null }
        assertEquals(LocalDate.of(2026, 10, 12), vm.etat.value.date)
        assertEquals("Dos / biceps", vm.libelle.text.toString())

        vm.libelle.setTextAndPlaceCursorAtEnd("Dos")
        vm.choisirDate(LocalDate.of(2026, 10, 11))
        vm.valider()

        assertEquals(FinFormulaire.Enregistre, vm.etat.attendre { it.fin != null }.fin)
        val modifie = checkNotNull(dao.observer(id).first())
        assertEquals(LocalDate.of(2026, 10, 11) to "Dos", modifie.date to modifie.libelle)
    }

    @Test
    fun `entrainement a modifier introuvable, l'ecran se ferme`() = runTest {
        val vm = formulaire(id = 42)
        assertEquals(FinFormulaire.Introuvable, vm.etat.attendre { it.fin != null }.fin)
        assertFalse(vm.etat.value.erreur)
    }
}
