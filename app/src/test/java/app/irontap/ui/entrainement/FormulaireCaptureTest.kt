package app.irontap.ui.entrainement

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
import java.time.LocalDate

/** Captures de l'écran 2 (maquettes 2a, 2b, 2c). Locale française pour le sélecteur de date. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "fr-rFR-w360dp-h750dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class FormulaireCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val aujourdhui = LocalDate.of(2026, 10, 13)

    private fun afficher(etat: EtatFormulaire, libelle: String = "", selecteurOuvert: Boolean = false) = compose.setContent {
        IronTapTheme {
            ContenuFormulaire(
                etat = etat,
                aujourdhui = aujourdhui,
                libelle = TextFieldState(libelle),
                onDate = {},
                onValider = {},
                onFermer = {},
                selecteurOuvert = selecteurOuvert,
            )
        }
    }

    @Test
    fun nouvelEntrainement() {
        afficher(EtatFormulaire(aujourdhui, modification = false))
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun selecteurDate() {
        afficher(EtatFormulaire(aujourdhui, modification = false), selecteurOuvert = true)
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun modifierEntrainement() {
        afficher(EtatFormulaire(LocalDate.of(2026, 10, 12), modification = true), libelle = "Dos / biceps")
        compose.onRoot().captureRoboImage()
    }

    /** Écran courant de 411 dp : le dialogue retrouve sa marge de 20 dp. */
    @Test
    @Config(qualifiers = "fr-rFR-w411dp-h891dp-xxhdpi")
    fun selecteurDateEcranLarge() {
        afficher(EtatFormulaire(aujourdhui, modification = false), selecteurOuvert = true)
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}
