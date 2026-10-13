package app.irontap.ui.saisie

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.EtatChrono.Etat
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Serie
import app.irontap.logique.Suggestion
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

/**
 * Captures de l'écran Saisie série dans les états des maquettes 3a–3e, 4a et 4b, à comparer avec
 * `design/SaisieSerie.dc.html` (800 dp moins les barres système de 28 et 22 dp). `./gradlew recordRoborazziDebug` écrit les images dans app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h750dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class SaisieCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.parse("2026-10-12T17:00:00Z")
    private val entrainement = Entrainement(1, LocalDate.of(2026, 10, 12), "Dos / biceps", t0)

    private val tirage = ExerciceAvecSeries(
        Exercice(1, 1, "Tirage vertical", 1),
        listOf(Serie(1, 1, 1, 40.0, 15, t0)),
    )

    private object SansAction : ActionsSaisie {
        override fun basculerChrono() = Unit
        override fun relancerChrono() = Unit
        override fun choisir(suggestion: Suggestion) = Unit
        override fun ajouterAuPoids(valeur: Int) = Unit
        override fun ajouterAuxRepetitions(valeur: Int) = Unit
        override fun enregistrer(suite: Suite) = Unit
        override fun demanderFin() = Unit
        override fun annulerFin() = Unit
        override fun terminer(avecSerie: Boolean) = Unit
    }

    private fun afficher(
        exercices: List<ExerciceAvecSeries> = emptyList(),
        chrono: EtatChrono = EtatChrono(1),
        formulaire: Formulaire = Formulaire(),
        suggestions: List<Suggestion> = emptyList(),
        nom: String = "",
        poids: String = "",
        repetitions: String = "",
    ) {
        compose.setContent {
            IronTapTheme {
                ContenuSaisie(
                    etat = EtatSaisie.EnCours(entrainement, exercices, chrono),
                    formulaire = formulaire,
                    suggestions = suggestions,
                    nom = TextFieldState(nom),
                    poids = TextFieldState(poids),
                    repetitions = TextFieldState(repetitions),
                    realtime = { 72_000 },
                    actions = SansAction,
                )
            }
        }
    }

    @Test
    fun premierExercice() {
        afficher()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun autocompletion() {
        afficher(
            nom = "tir",
            suggestions = listOf(
                Suggestion("Tirage vertical", 0, 3),
                Suggestion("Tirage horizontal", 0, 3),
                Suggestion("Tirage poulie basse", 0, 3),
            ),
        )
        compose.onNode(hasSetTextAction() and hasTextExactly("tir")).requestFocus()
        compose.waitForIdle()
        // Capture de tout l'écran : le focus peut afficher la poignée du curseur dans une seconde fenêtre.
        captureScreenRoboImage()
    }

    @Test
    fun chronoEnMarche() {
        afficher(
            exercices = listOf(tirage),
            chrono = EtatChrono(1, Etat.EnCours, demarreA = 0, cumulMs = 0),
            formulaire = Formulaire(toast = "Série 1 enregistrée · 40 kg × 15"),
            poids = "45",
            repetitions = "12",
        )
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun chronoEnPause() {
        afficher(exercices = listOf(tirage), chrono = EtatChrono(1, Etat.Pause, cumulMs = 72_000), poids = "41,25")
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun erreurs() {
        afficher(formulaire = Formulaire(erreurNom = true, erreurRepetitions = true), poids = "40")
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun dialogueAvecSerie() {
        afficher(exercices = listOf(tirage), formulaire = Formulaire(dialogue = DialogueFin.AvecSerie(SerieSaisie("Tirage vertical", 45.0, 10))))
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun dialogueSimple() {
        afficher(exercices = listOf(tirage), formulaire = Formulaire(dialogue = DialogueFin.Simple))
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}
