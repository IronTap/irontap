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
import app.irontap.donnees.PalierEnCours
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.SaisieOnglet
import app.irontap.donnees.SupersetEnCours
import app.irontap.donnees.ValeursPalier
import app.irontap.donnees.serieDeTest
import app.irontap.logique.HistoriqueExercice
import app.irontap.logique.LigneHistorique
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
        listOf(serieDeTest(1, 1, 1, 40.0, 15, t0)),
    )

    private open class SansAction : ActionsSaisie {
        override fun basculerChrono() = Unit
        override fun relancerChrono() = Unit
        override fun choisir(suggestion: Suggestion) = Unit
        override fun ajouterAuPoids(valeur: Int) = Unit
        override fun ajouterAuxRepetitions(valeur: Int) = Unit
        override fun enregistrer(suite: Suite) = Unit
        override fun degressive() = Unit
        override fun demanderFin() = Unit
        override fun annulerFin() = Unit
        override fun terminer(avecSerie: Boolean) = Unit
        override fun ouvrirHistorique() = Unit
        override fun fermerHistorique() = Unit
        override fun commencerSuperset() = Unit
        override fun basculer(role: RoleSuperset) = Unit
        override fun memoriserSaisie() = Unit
        override fun confirmerPassage() = Unit
        override fun annulerPassage() = Unit
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
        paliersEnCours: List<PalierEnCours> = emptyList(),
        superset: SupersetEnCours? = null,
        onglets: OngletsSuperset? = null,
    ) {
        compose.setContent {
            IronTapTheme {
                Box(if (hauteurVisible != null) Modifier.height(hauteurVisible) else Modifier) {
                    ContenuSaisie(
                        etat = EtatSaisie.EnCours(entrainement, exercices, chrono, paliersEnCours, superset),
                        formulaire = formulaire,
                        suggestions = suggestions,
                        historiqueDisponible = historiqueDisponible,
                        nom = TextFieldState(nom),
                        poids = TextFieldState(poids),
                        repetitions = TextFieldState(repetitions),
                        realtime = { 72_000 },
                        actions = actions,
                        onglets = onglets,
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
        afficher(exercices = listOf(tirage), formulaire = Formulaire(dialogue = DialogueFin.AvecSerie(SerieSaisie("Tirage vertical", emptyList(), ValeursPalier(45.0, 10)))))
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun dialogueSimple() {
        afficher(exercices = listOf(tirage), formulaire = Formulaire(dialogue = DialogueFin.Simple))
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    /** « Nouvel exercice » avec les champs vides sur un exercice verrouillé (DECISIONS.md). */
    @Test
    fun dialoguePassage() {
        afficher(exercices = listOf(tirageSerie3), formulaire = Formulaire(passage = PassageExercice.Simple("Tirage vertical", 2)))
        compose.waitForIdle()
        compose.onNode(hasText("Passer à l’exercice suivant ?")).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Nouvel exercice")).assertHeightIsEqualTo(48.dp)
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

    private fun lignes(vararg principaux: String) = principaux.map { LigneHistorique(it) }

    private val developpe = ExerciceAvecSeries(
        Exercice(1, 1, "Développé couché", 1),
        listOf(serieDeTest(1, 1, 1, 50.0, 10, t0), serieDeTest(2, 1, 2, 55.0, 8, t0)),
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
            SeanceHistorique(
                "Dos / biceps — 12/10/2026",
                lignes("15 × 40 kg", "12 × 45 kg") + LigneHistorique("10 × 45 kg", listOf("8 × 30 kg", "6 × 20 kg")),
            ),
            SeanceHistorique("Dos / biceps — 05/10/2026", lignes("15 × 40 kg", "12 × 40 kg", "10 × 45 kg")),
            SeanceHistorique("Dos / biceps — 28/09/2026", lignes("12 × 40 kg", "10 × 42,5 kg", "8 × 45 kg")),
            SeanceHistorique("24/09/2026", lignes("12 × 37,5 kg", "10 × 40 kg")),
            SeanceHistorique("Dos / biceps — 21/09/2026", lignes("10 × 30 kg", "10 × 40 kg", "8 × 40 kg")),
            SeanceHistorique("Dos / biceps — 14/09/2026", lignes("10 × 30 kg", "10 × 35 kg", "8 × 40 kg")),
        )
        val tirageEnCours = ExerciceAvecSeries(
            Exercice(2, 1, "Tirage vertical", 2),
            listOf(serieDeTest(3, 2, 1, 40.0, 15, t0), serieDeTest(4, 2, 2, 45.0, 12, t0)),
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
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("6 × 20 kg"))
        compose.onNode(hasText("8 × 30 kg")).assertIsDisplayed()
        compose.onNode(hasText("6 × 20 kg")).assertIsDisplayed()
        compose.onNode(isHeading() and hasText("Tirage vertical")).assertIsDisplayed()

        compose.onNode(hasContentDescription("Fermer"))
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, fermetures)
    }

    /** Tirage vertical, exercice 2, deux séries faites : la série 3 est en saisie (3·D). */
    private val tirageSerie3 = ExerciceAvecSeries(
        Exercice(2, 1, "Tirage vertical", 2),
        listOf(serieDeTest(3, 2, 1, 40.0, 15, t0), serieDeTest(4, 2, 2, 45.0, 12, t0)),
    )

    private fun degressive(vararg paliers: Pair<Double?, Int>, poids: String, repetitions: String = "") = afficher(
        exercices = listOf(developpe, tirageSerie3),
        chrono = EtatChrono(1, Etat.EnCours, demarreA = 0),
        poids = poids,
        repetitions = repetitions,
        paliersEnCours = paliers.mapIndexed { i, (p, r) -> PalierEnCours(i + 1L, 1, i + 1, p, r) },
    )

    /** 3·D A1 : 3ᵉ bouton « Dégressive » entre les deux actions. */
    @Test
    fun degressiveA1() {
        degressive(poids = "50", repetitions = "10")
        compose.onNode(hasClickAction() and hasText("Dégressive")).assertHeightIsEqualTo(72.dp)
        compose.onRoot().captureRoboImage()
    }

    /** 3·D A2 : palier 2 en saisie, palier 1 rappelé dans l'en-tête avec le badge. */
    @Test
    fun degressiveA2() {
        degressive(50.0 to 10, poids = "30")
        compose.onNode(hasText("50 kg × 10 ↓ …")).assertIsDisplayed()
        compose.onNode(hasText("Exercice 2 · Série 3")).assertIsDisplayed()
        compose.onNode(hasText("dégressive")).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    /** 3·D A3 : double dégressive, 3ᵉ palier en saisie. */
    @Test
    fun degressiveA3() {
        degressive(50.0 to 10, 30.0 to 8, poids = "20")
        compose.onNode(hasText("50 kg × 10 ↓ 30 kg × 8 ↓ …")).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    // --- Superset (3·S) ---

    private val epaules = Entrainement(1, LocalDate.of(2026, 10, 15), "Épaules / bras", t0, nouvelExercice = true)

    /** Deux exercices déjà faits : le suivant est l'exercice 3, comme la maquette. */
    private val avantSuperset = listOf(
        ExerciceAvecSeries(Exercice(1, 1, "Développé militaire", 1), listOf(serieDeTest(1, 1, 1, 30.0, 10, t0))),
        ExerciceAvecSeries(Exercice(2, 1, "Oiseau", 2), listOf(serieDeTest(2, 2, 1, 6.0, 12, t0))),
    )

    /** S1 : avant la première série, pastille « Superset » sur la ligne du libellé. */
    @Test
    fun supersetPastille() {
        afficher(exercices = avantSuperset, entrainement = epaules, chrono = EtatChrono(1, Etat.EnCours, demarreA = 0), nom = "Oiseau haltères")
        compose.onNode(hasClickAction() and hasText("Superset")).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    /** S2 : exercice avec historique, pastille et bouton historique cohabitent sans réduire le champ. */
    @Test
    fun supersetEtHistorique() {
        afficher(
            exercices = avantSuperset,
            entrainement = epaules,
            chrono = EtatChrono(1, Etat.EnCours, demarreA = 0),
            nom = "Développé couché",
            historiqueDisponible = true,
        )
        compose.onNode(hasContentDescription("Historique de l’exercice")).assertWidthIsEqualTo(52.dp)
        compose.onNode(hasClickAction() and hasText("Superset")).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    /** Superset au tour 2 : A (Élévations latérales) et B (Curl marteau) ont chacun une série. */
    private val supersetTour2 = avantSuperset + listOf(
        ExerciceAvecSeries(Exercice(3, 1, "Élévations latérales", 3, "s1", RoleSuperset.A), listOf(serieDeTest(3, 3, 1, 8.0, 15, t0))),
        ExerciceAvecSeries(Exercice(4, 1, "Curl marteau", 4, "s1", RoleSuperset.B), listOf(serieDeTest(4, 4, 1, 12.0, 12, t0))),
    )

    private fun supersetB(formulaire: Formulaire = Formulaire()) = afficher(
        exercices = supersetTour2,
        entrainement = epaules.copy(nouvelExercice = false),
        chrono = EtatChrono(1, Etat.EnCours, demarreA = 72_000 - 4_000),
        formulaire = formulaire,
        historiqueDisponible = true,
        nom = "Curl marteau",
        poids = "12",
        superset = SupersetEnCours(1, "s1", "Élévations latérales", "Curl marteau", RoleSuperset.B, "8", "12"),
        onglets = OngletsSuperset(RoleSuperset.B, mapOf(RoleSuperset.A to "Élévations latérales", RoleSuperset.B to "Curl marteau")),
    )

    /** S3 : série 2, A déjà saisi (8 kg × 12), saisie de B, pastille « Historique B ». */
    @Test
    fun supersetOngletB() {
        supersetB()
        compose.onNode(hasText("Superset · saisie de B")).assertIsDisplayed()
        compose.onNode(hasText("Exercice 3 · Série 2")).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Historique B")).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Élévations latérales")).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    /** S4 : popup historique de B, séance faite en superset signalée. */
    @Test
    fun supersetHistoriqueB() {
        supersetB(
            Formulaire(
                historique = HistoriqueExercice(
                    "Curl marteau",
                    listOf(
                        SeanceHistorique("Épaules / bras — 15/10/2026", lignes("12 × 12 kg", "10 × 12 kg", "10 × 10 kg"), superset = "Élévations latérales"),
                        SeanceHistorique("Épaules / bras — 08/10/2026", lignes("12 × 14 kg", "10 × 14 kg", "10 × 12 kg")),
                        SeanceHistorique("Épaules / bras — 01/10/2026", lignes("12 × 10 kg", "10 × 12 kg")),
                    ),
                ),
            ),
        )
        compose.waitForIdle()
        compose.onNode(hasText("En superset avec Élévations latérales")).assertIsDisplayed()
        captureScreenRoboImage()
    }

    /** « Nouvel exercice » avec les deux onglets vides : confirmation de la fin du superset. */
    @Test
    fun supersetPassage() {
        supersetB(Formulaire(passage = PassageExercice.Superset(1)))
        compose.waitForIdle()
        compose.onNode(hasText("Terminer le superset ?")).assertIsDisplayed()
        captureScreenRoboImage()
    }

    /** S5 : « Terminer » avec A saisi et B vide. */
    @Test
    fun supersetFinIncomplete() {
        supersetB(Formulaire(dialogue = DialogueFin.SupersetIncomplet(RoleSuperset.A, SaisieOnglet("", 8.0, 12), "8 kg × 12")))
        compose.waitForIdle()
        compose.onNode(hasText("Enregistrer A seul et terminer")).assertIsDisplayed()
        captureScreenRoboImage()
    }
}
