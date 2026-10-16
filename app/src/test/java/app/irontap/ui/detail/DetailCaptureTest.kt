package app.irontap.ui.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.SerieAvecPaliers
import app.irontap.donnees.serieDeTest
import app.irontap.logique.Suggestion
import app.irontap.ui.theme.IronTapTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Exercice et ses séries ; une série dégressive est une liste de plusieurs paliers. */
    private fun exercice(id: Long, nom: String, vararg series: List<Pair<Double?, Int>>) = ExerciceAvecSeries(
        Exercice(id, 1, nom, id.toInt()),
        series.mapIndexed { i, paliers ->
            serieDeTest(++idSerie, id, i + 1, paliers[0].first, paliers[0].second, t0, *paliers.drop(1).toTypedArray())
        },
    )

    private fun s(poids: Double?, reps: Int) = listOf(poids to reps)

    private val complet = EntrainementComplet(
        Entrainement(1, LocalDate.of(2026, 10, 12), "Dos / biceps", t0, termineLe = t0),
        listOf(
            // Série 3 dégressive, comme la maquette 6a.
            exercice(1, "Tirage vertical", s(40.0, 15), s(45.0, 12), listOf(45.0 to 10, 30.0 to 8, 20.0 to 6)),
            exercice(2, "Curl haltères", s(12.0, 12), s(14.0, 10), s(14.0, 8)),
            exercice(3, "Tirage horizontal", s(50.0, 12), s(55.0, 10)),
        ),
    )
    private val tirage = complet.exercices[0]

    private object SansNavigation : NavigationDetail {
        override fun retour() = Unit
        override fun modifierEntrainement() = Unit
    }

    private open class SansAction : ActionsDetail {
        override fun modifierSerie(exercice: Exercice, serie: SerieAvecPaliers) = Unit
        override fun ajouterSerie(exercice: Exercice) = Unit
        override fun supprimerSerie(exercice: Exercice, serie: SerieAvecPaliers) = Unit
        override fun renommer(exercice: Exercice) = Unit
    }

    private object SansEdition : ActionsEdition {
        override fun ajouterAuPoids(valeur: Int) = Unit
        override fun ajouterAuxRepetitions(valeur: Int) = Unit
        override fun ajouterPalier() = 0L
        override fun retirerPalier(cle: Long) = Unit
        override fun enregistrer() = Unit
        override fun supprimer(exercice: Exercice, serie: SerieAvecPaliers) = Unit
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
        // Série 3 dégressive : paliers empilés sous la valeur principale, précédés d'une flèche.
        compose.onNode(hasText("45 kg × 10")).assertIsDisplayed()
        compose.onNode(hasText("30 kg × 8")).assertIsDisplayed()
        compose.onNode(hasText("20 kg × 6")).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("puis"), useUnmergedTree = true).assertCountEquals(2)
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun serieBalayee() {
        val serie = tirage.series[1]
        afficher(serieRevelee = serie.serie.id) {
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
                paliers = listOf(ChampsPalier(0, "45", "12")),
                paliersEnErreur = emptySet(),
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
        val demandes = mutableListOf<SerieAvecPaliers>()
        afficher(
            actions = object : SansAction() {
                override fun supprimerSerie(exercice: Exercice, serie: SerieAvecPaliers) {
                    demandes += serie
                }
            },
        )
        compose.onNode(hasClickAction() and hasText("45 kg × 12")).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(tirage.series[1]), demandes)
    }

    /** 7·D : tableau des paliers d'une dégressive, palier 2 en cours de modification. */
    @Test
    fun editionDegressive() {
        val serie = tirage.series[2]
        afficher {
            FeuilleEdition(
                edition = EditionSerie(tirage.exercice, serie, tableau = true),
                // « 102,25 » : un poids de 6 caractères élargit son champ au lieu d'être coupé.
                paliers = listOf(ChampsPalier(0, "45", "10"), ChampsPalier(1, "30", "8"), ChampsPalier(2, "102,25", "6")),
                paliersEnErreur = emptySet(),
                erreurEnregistrement = false,
                actions = SansEdition,
            )
        }
        compose.onNode(hasContentDescription("Poids du palier 2")).requestFocus()
        compose.waitForIdle()
        // Le palier 1 ne se retire pas : un seul ✕ par palier dégressif.
        compose.onAllNodes(hasContentDescription("Retirer ce palier")).assertCountEquals(2)
        compose.onNode(hasContentDescription("Poids du palier 1")).assertWidthIsEqualTo(100.dp)
        val large = compose.onNode(hasContentDescription("Poids du palier 3")).fetchSemanticsNode().size.width
        assertTrue(large > compose.onNode(hasContentDescription("Poids du palier 1")).fetchSemanticsNode().size.width)
        captureScreenRoboImage()
    }
}
