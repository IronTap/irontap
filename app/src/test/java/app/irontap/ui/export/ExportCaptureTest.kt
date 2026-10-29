package app.irontap.ui.export

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.serieDeTest
import app.irontap.logique.FormatExport
import app.irontap.ui.accueil.ActionsAccueil
import app.irontap.ui.accueil.ContenuAccueil
import app.irontap.ui.accueil.EtatAccueil
import app.irontap.ui.theme.IronTapTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Captures de la feuille d'export (maquette 8), à comparer avec la planche. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h750dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
class ExportCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.parse("2026-10-12T17:00:00Z")

    private val dos = EntrainementComplet(
        Entrainement(1, LocalDate.of(2026, 10, 12), "Dos / biceps", t0, termineLe = t0),
        listOf(
            ExerciceAvecSeries(
                Exercice(1, 1, "Tirage vertical", 1),
                listOf(40.0 to 15, 45.0 to 12, 45.0 to 10).mapIndexed { i, (p, r) -> serieDeTest(i + 1L, 1, i + 1, p, r, t0) },
            ),
            ExerciceAvecSeries(Exercice(2, 1, "Curl haltères", 2), listOf(serieDeTest(9, 2, 1, 12.5, 12, t0))),
        ),
    )

    private val pret = EtatExport.Pret(
        portee = "Tout l’historique · 24 entraînements",
        dateEntrainement = null,
        contenus = FormatExport.entries.associateWith { it.generer(listOf(dos)) },
    )

    private object SansAction : ActionsExport, ActionsAccueil {
        override fun choisir(format: FormatExport) = Unit
        override fun partager() = Unit
        override fun enregistrer() = Unit
        override fun fermer() = Unit
        override fun importer() = Unit
        override fun aPropos() = Unit
        override fun nouvelEntrainement() = Unit
        override fun reprendre() = Unit
        override fun ouvrir(entrainement: Entrainement) = Unit
    }

    private fun afficher(format: FormatExport, operation: OperationExport = OperationExport()) = compose.setContent {
        IronTapTheme {
            ContenuAccueil(EtatAccueil.Pret(listOf(dos.entrainement), enCours = null), realtime = { 0 }, onExporter = {}, actions = SansAction)
            // Depuis l'accueil : lien d'import en bas.
            VueExport(pret, format, operation, SansAction, importable = true)
        }
    }

    @Test
    fun texte() {
        afficher(FormatExport.Texte)
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun csvAvecErreur() {
        afficher(FormatExport.Csv, OperationExport(erreur = "Enregistrement impossible : le fichier n’a pas pu être écrit"))
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}
