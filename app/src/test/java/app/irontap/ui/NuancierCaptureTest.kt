package app.irontap.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.irontap.ui.theme.IronTapTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capture de la planche du thème, rendue sur JVM (Robolectric, rendu natif).
 * `./gradlew recordRoborazziDebug` écrit l'image dans app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1900dp-xxhdpi")
class NuancierCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nuancier() {
        compose.setContent { IronTapTheme { Nuancier(onSaisie = {}) } }
        compose.onRoot().captureRoboImage()
    }
}
