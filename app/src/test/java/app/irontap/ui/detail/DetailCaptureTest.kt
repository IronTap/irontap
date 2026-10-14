package app.irontap.ui.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Serie
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

/** Captures du détail (maquettes 6a, 6b, 6c) et de l'édition de série (7), à comparer avec la planche. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h750dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class DetailCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.parse("2026-10-12T17:00:00Z")
    private var idSerie = 0L

    private fun exercice(id: Long, nom: String, vararg series: Pair<Double, Int>) = ExerciceAvecSeries(
        Exercice(id, 1, nom, id.toInt()),
        series.mapIndexed { i, (poids, reps) -> Serie(++idSerie, id, i + 1, poids, reps, t0) },
    )

    private val complet = EntrainementComplet(
        Entrainement(1, LocalDate.of(2026, 10, 12), "Dos / biceps", t0, termineLe = t0),
        listOf(
            exercice(1, "Tirage vertical", 40.0 to 15, 45.0 to 12, 45.0 to 10),
            exercice(2, "Curl haltères", 12.0 to 12, 14.0 to 10, 14.0 to 8),
            exercice(3, "Tirage horizontal", 50.0 to 12, 55.0 to 10),
        ),
    )
    private val tirage = complet.exercices[0]

    private object SansNavigation : NavigationDetail {
        override fun retour() = Unit
        override fun modifierEntrainement() = Unit
    }

    private open class SansAction : ActionsDetail {
        override fun modifierSerie(exercice: Exercice, serie: Serie) = Unit
        override fun ajouterSerie(exercice: Exercice) = Unit
        override fun supprimerSerie(exercice: Exercice, serie: Serie) = Unit
        override fun renommer(exercice: Exercice) = Unit
    }

    private object SansEdition : ActionsEdition {
        override fun ajouterAuPoids(valeur: Int) = Unit
        override fun ajouterAuxRepetitions(valeur: Int) = Unit
        override fun enregistrer() = Unit
        override fun supprimer(exercice: Exercice, serie: Serie) = Unit
        override fun fermer() = Unit
    }

    private fun afficher(
        serieRevelee: Long? = null,
        menuOuvert: Boolean = false,
        actions: ActionsDetail = SansAction(),
        pardessus: @androidx.compose.runtime.Composable () -> Unit = {},
    ) = compose.setContent {
        IronTapTheme {
            ContenuDetail(complet, serieRevelee, SansNavigation, onExporter = {}, onSupprimerEntrainement = {}, actions, menuOuvert)
            pardessus()
        }
    }

    @Test
    fun parDefaut() {
        afficher()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun serieBalayee() {
        val serie = tirage.series[1]
        afficher(serieRevelee = serie.id) {
            DialogueSuppressionSerie(SuppressionSerie(tirage.exercice, serie), nombreSeries = 3, erreur = false, onAnnuler = {}, onSupprimer = {})
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun menu() {
        afficher(menuOuvert = true)
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun editionSerie() {
        afficher {
            FeuilleEdition(
                edition = EditionSerie(tirage.exercice, tirage.series[1]),
                poids = TextFieldState("45"),
                repetitions = TextFieldState("12"),
                erreurRepetitions = false,
                erreurEnregistrement = false,
                actions = SansEdition,
            )
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun renommage() {
        afficher {
            DialogueRenommage(
                nom = TextFieldState("Tir"),
                erreur = false,
                erreurEnregistrement = false,
                suggestions = listOf(Suggestion("Tirage vertical", 0, 3), Suggestion("Tirage horizontal", 0, 3)),
                onChoisir = {},
                onAnnuler = {},
                onEnregistrer = {},
            )
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun suppressionEntrainement() {
        afficher {
            DialogueSuppressionEntrainement(complet.entrainement, erreur = false, onAnnuler = {}, onSupprimer = {})
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun `balayer une serie vers la gauche demande sa suppression`() {
        val demandes = mutableListOf<Serie>()
        afficher(
            actions = object : SansAction() {
                override fun supprimerSerie(exercice: Exercice, serie: Serie) {
                    demandes += serie
                }
            },
        )
        compose.onNode(hasClickAction() and hasText("45 kg × 12")).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(tirage.series[1]), demandes)
    }
}
