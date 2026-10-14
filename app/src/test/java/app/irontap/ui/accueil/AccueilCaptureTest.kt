package app.irontap.ui.accueil

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.EtatChrono.Etat
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Serie
import app.irontap.ui.saisie.EtatSaisie
import app.irontap.ui.theme.IronTapTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Captures de l'accueil (maquettes 1a, 1b, 1c), à comparer avec `design/Muscu - Ecrans.dc.html`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h750dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class AccueilCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.parse("2026-10-12T17:00:00Z")

    private val historique = listOf(
        LocalDate.of(2026, 10, 12) to "Dos / biceps",
        LocalDate.of(2026, 10, 9) to "Jambes",
        LocalDate.of(2026, 10, 7) to "Pecs / épaules",
        LocalDate.of(2026, 10, 5) to "Dos / biceps",
        LocalDate.of(2026, 10, 2) to null,
        LocalDate.of(2026, 9, 30) to "Jambes",
    ).mapIndexed { i, (date, libelle) -> Entrainement(i + 10L, date, libelle, t0, termineLe = t0) }

    private val enCours = EtatSaisie.EnCours(
        Entrainement(1, LocalDate.of(2026, 10, 13), "Dos / biceps", t0),
        listOf(
            ExerciceAvecSeries(
                Exercice(1, 1, "Tirage vertical", 1),
                List(2) { Serie(it.toLong(), 1, it + 1, 40.0, 12, t0) },
            ),
        ),
        EtatChrono(1, Etat.EnCours, demarreA = 0),
    )

    private object SansAction : ActionsAccueil {
        override fun nouvelEntrainement() = Unit
        override fun reprendre() = Unit
        override fun ouvrir(entrainement: Entrainement) = Unit
    }

    private fun afficher(etat: EtatAccueil.Pret) = compose.setContent {
        IronTapTheme { ContenuAccueil(etat, realtime = { 72_000 }, onExporter = {}, actions = SansAction) }
    }

    @Test
    fun avecHistorique() {
        afficher(EtatAccueil.Pret(historique, enCours = null))
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun vide() {
        afficher(EtatAccueil.Pret(emptyList(), enCours = null))
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun enCours() {
        afficher(EtatAccueil.Pret(historique, enCours))
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun dialogueTerminerEnCours() {
        compose.setContent {
            IronTapTheme {
                ContenuAccueil(EtatAccueil.Pret(historique, enCours), realtime = { 72_000 }, onExporter = {}, actions = SansAction)
                DialogueTerminerEnCours(avecSeries = true, onAnnuler = {}, onTerminer = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}
