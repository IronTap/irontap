package app.irontap.ui.saisie

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.EtatChrono.Etat
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Serie
import app.irontap.logique.HistoriqueExercice
import app.irontap.logique.SeanceHistorique
import app.irontap.logique.Suggestion
import app.irontap.ui.theme.IronTapTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * Captures de l'écran Saisie série dans les états des maquettes 3a–3g, 4a et 4b, à comparer avec
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

    private open class SansAction : ActionsSaisie {
        override fun basculerChrono() = Unit
        override fun relancerChrono() = Unit
        override fun choisir(suggestion: Suggestion) = Unit
        override fun ajouterAuPoids(valeur: Int) = Unit
        override fun ajouterAuxRepetitions(valeur: Int) = Unit
        override fun enregistrer(suite: Suite) = Unit
        override fun demanderFin() = Unit
        override fun annulerFin() = Unit
        override fun terminer(avecSerie: Boolean) = Unit
        override fun ouvrirHistorique() = Unit
        override fun fermerHistorique() = Unit
    }

    private fun afficher(
        exercices: List<ExerciceAvecSeries> = emptyList(),
        entrainement: Entrainement = this.entrainement,
        historiqueDisponible: Boolean = false,
        chrono: EtatChrono = EtatChrono(1),
        formulaire: Formulaire = Formulaire(),
        suggestions: List<Suggestion> = emptyList(),
        nom: String = "",
        poids: String = "",
        repetitions: String = "",
        hauteurVisible: Dp? = null,
        actions: ActionsSaisie = SansAction(),
    ) {
        compose.setContent {
            IronTapTheme {
                Box(if (hauteurVisible != null) Modifier.height(hauteurVisible) else Modifier) {
                    ContenuSaisie(
                        etat = EtatSaisie.EnCours(entrainement, exercices, chrono),
                        formulaire = formulaire,
                        suggestions = suggestions,
                        historiqueDisponible = historiqueDisponible,
                        nom = TextFieldState(nom),
                        poids = TextFieldState(poids),
                        repetitions = TextFieldState(repetitions),
                        realtime = { 72_000 },
                        actions = actions,
                    )
                }
            }
        }
    }

    @Test
    fun premierExercice() {
        afficher()
        compose.onNode(hasContentDescription("Historique de l’exercice")).assertDoesNotExist()
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

    /** Clavier ouvert : la hauteur visible se réduit, le contenu défile sans être comprimé. */
    @Test
    fun clavierOuvert() {
        afficher(exercices = listOf(tirage), hauteurVisible = 400.dp)
        val nouvelleSerie = hasClickAction() and hasText("Nouvelle", substring = true)
        compose.onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(nouvelleSerie)
        compose.onNode(nouvelleSerie).assertHeightIsEqualTo(72.dp)
        compose.onNode(hasClickAction() and hasText("Nouvel\nexercice")).assertHeightIsEqualTo(72.dp)
        compose.onRoot().captureRoboImage()
    }

    /** Écran 9 : pré-permission des notifications, par-dessus la saisie. */
    @Test
    fun prePermission() {
        compose.setContent {
            IronTapTheme {
                ContenuSaisie(
                    etat = EtatSaisie.EnCours(entrainement, emptyList(), EtatChrono(1, Etat.EnCours, demarreA = 0)),
                    formulaire = Formulaire(),
                    suggestions = emptyList(),
                    historiqueDisponible = false,
                    nom = TextFieldState("Tirage vertical"),
                    poids = TextFieldState("40"),
                    repetitions = TextFieldState("15"),
                    realtime = { 0 },
                    actions = SansAction(),
                )
                FeuillePrePermission(onAutoriser = {}, onPlusTard = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    private val developpe = ExerciceAvecSeries(
        Exercice(1, 1, "Développé couché", 1),
        listOf(Serie(1, 1, 1, 50.0, 10, t0), Serie(2, 1, 2, 55.0, 8, t0)),
    )

    /** 3f : nom reconnu (deuxième exercice, nom encore éditable), bouton Historique accolé au champ. */
    @Test
    fun historiqueDisponible() {
        afficher(
            exercices = listOf(developpe),
            entrainement = entrainement.copy(nouvelExercice = true),
            historiqueDisponible = true,
            chrono = EtatChrono(1, Etat.EnCours, demarreA = 72_000 - 47_000),
            nom = "Tirage vertical",
        )
        compose.onNode(hasContentDescription("Historique de l’exercice"))
            .assertWidthIsEqualTo(52.dp)
            .assertHeightIsEqualTo(52.dp)
        compose.onRoot().captureRoboImage()
    }

    /** 3g : popup ouverte sur le nom verrouillé ; le corps défile sous l'en-tête fixe. */
    @Test
    fun historiqueOuvert() {
        val seances = listOf(
            SeanceHistorique("Dos / biceps — 12/10/2026", listOf("15 × 40 kg", "12 × 45 kg", "10 × 45 kg")),
            SeanceHistorique("Dos / biceps — 05/10/2026", listOf("15 × 40 kg", "12 × 40 kg", "10 × 45 kg")),
            SeanceHistorique("Dos / biceps — 28/09/2026", listOf("12 × 40 kg", "10 × 42,5 kg", "8 × 45 kg")),
            SeanceHistorique("24/09/2026", listOf("12 × 37,5 kg", "10 × 40 kg")),
            SeanceHistorique("Dos / biceps — 21/09/2026", listOf("10 × 30 kg", "10 × 40 kg", "8 × 40 kg")),
            SeanceHistorique("Dos / biceps — 14/09/2026", listOf("10 × 30 kg", "10 × 35 kg", "8 × 40 kg")),
        )
        val tirageEnCours = ExerciceAvecSeries(
            Exercice(2, 1, "Tirage vertical", 2),
            listOf(Serie(3, 2, 1, 40.0, 15, t0), Serie(4, 2, 2, 45.0, 12, t0)),
        )
        var fermetures = 0
        afficher(
            actions = object : SansAction() {
                override fun fermerHistorique() { fermetures++ }
            },
            exercices = listOf(developpe, tirageEnCours),
            historiqueDisponible = true,
            chrono = EtatChrono(1, Etat.EnCours, demarreA = 72_000 - 47_000),
            formulaire = Formulaire(historique = HistoriqueExercice("Tirage vertical", seances)),
        )
        compose.waitForIdle()
        captureScreenRoboImage()

        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("14/09/2026", substring = true))
        compose.onNode(hasText("14/09/2026", substring = true)).assertIsDisplayed()
        compose.onNode(isHeading() and hasText("Tirage vertical")).assertIsDisplayed()

        compose.onNode(hasContentDescription("Fermer"))
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, fermetures)
    }
}
