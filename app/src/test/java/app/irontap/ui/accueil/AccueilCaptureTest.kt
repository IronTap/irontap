package app.irontap.ui.accueil

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.EtatChrono.Etat
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.serieDeTest
import app.irontap.logique.EntrainementImporte
import app.irontap.logique.ErreurImport
import app.irontap.logique.LectureImport
import app.irontap.logique.lireExportCsv
import app.irontap.ui.importation.DialoguesImport
import app.irontap.ui.importation.EtatImport
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
                List(2) { serieDeTest(it.toLong(), 1, it + 1, 40.0, 12, t0) },
            ),
        ),
        EtatChrono(1, Etat.EnCours, demarreA = 0),
    )

    private object SansAction : ActionsAccueil {
        override fun nouvelEntrainement() = Unit
        override fun reprendre() = Unit
        override fun ouvrir(entrainement: Entrainement) = Unit
        override fun aPropos() = Unit
    }

    private fun afficher(etat: EtatAccueil.Pret) = compose.setContent {
        IronTapTheme { ContenuAccueil(etat, realtime = { 72_000 }, onExporter = {}, actions = SansAction) }
    }

    @Test
    fun avecHistorique() {
        afficher(EtatAccueil.Pret(historique, enCours = null))
        compose.onNode(hasContentDescription("À propos") and hasClickAction()).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun vide() {
        afficher(EtatAccueil.Pret(emptyList(), enCours = null))
        compose.onNode(hasContentDescription("À propos") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Importer un export CSV") and hasClickAction()).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun enCours() {
        afficher(EtatAccueil.Pret(historique, enCours))
        compose.onNode(hasContentDescription("À propos") and hasClickAction()).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    private fun dialogueImport(etat: EtatImport) {
        compose.setContent {
            IronTapTheme {
                ContenuAccueil(EtatAccueil.Pret(historique, enCours = null), realtime = { 72_000 }, onExporter = {}, actions = SansAction)
                DialoguesImport(etat, onConfirmer = {}, onFermer = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun importConfirmation() {
        val seance = EntrainementImporte(LocalDate.of(2026, 9, 12), null, emptyList())
        dialogueImport(EtatImport.Confirmation(List(12) { seance.copy(date = seance.date.plusDays(it * 4L)) }, dejaPresents = 3))
        compose.onNode(hasText("Importer 12 entraînements ?")).assertIsDisplayed()
        compose.onNode(hasText("Du 12 sept. au 26 oct. 2026. 3 déjà présents seront ignorés.")).assertIsDisplayed()
    }

    @Test
    fun importErreurs() {
        val erreurs = (lireExportCsv(checkNotNull(javaClass.getResource("/import/exemple-import-erreurs.csv")).readText()) as LectureImport.Erreurs).erreurs
        dialogueImport(EtatImport.Echec(erreurs + listOf(ErreurImport(9, "date manquante"), ErreurImport(11, "nom d’exercice manquant"), ErreurImport(12, "répétitions manquantes"))))
        compose.onNode(hasText("Ligne 3 : répétitions manquantes")).assertIsDisplayed()
        compose.onNode(hasText("… et 2 autres erreurs.")).assertIsDisplayed()
    }

    @Test
    fun importRien() {
        dialogueImport(EtatImport.RienAImporter(15))
        compose.onNode(hasText("Les 15 entraînements du fichier sont déjà dans l’historique.")).assertIsDisplayed()
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
