package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Elevation
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import app.irontap.ui.theme.elevation

/**
 * Dialogue centré (écrans 4a, 4b, 6b) : voile neutre-900 à 72 %, marge 20, surface, rayon 14,
 * élévation lg, padding 22/20/16, éléments espacés de 10. Un appui hors de la carte ou Retour ferme.
 */
@Composable
fun DialogueNocturne(
    titre: String,
    onFermer: () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onFermer,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Le voile des maquettes remplace l'assombrissement de la fenêtre de dialogue.
        val fenetre = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { fenetre?.setDimAmount(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Couleurs.Voile)
                .pointerInput(onFermer) { detectTapGestures { onFermer() } }
                .safeDrawingPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .elevation(Elevation.Lg, Formes.Grande)
                    .background(Couleurs.Surface, Formes.Grande)
                    // Un appui sur la carte elle-même ne ferme pas le dialogue.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(titre, style = Typo.TitreDialogue, color = Couleurs.Texte)
                contenu()
            }
        }
    }
}
