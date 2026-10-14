package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Elevation
import app.irontap.ui.theme.elevation

/**
 * Feuille du bas (écrans 7, 8, 9) : surface, rayon 24 en haut, élévation lg, poignée 32 × 4 neutre-600, voile des
 * dialogues, contenu à 20 dp des bords. Glisser vers le bas, Retour ou un appui sur le voile ferme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleNocturne(
    onFermer: () -> Unit,
    etat: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val forme = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ModalBottomSheet(
        onDismissRequest = onFermer,
        modifier = Modifier.elevation(Elevation.Lg, forme),
        sheetState = etat,
        shape = forme,
        containerColor = Couleurs.Surface,
        contentColor = Couleurs.Texte,
        tonalElevation = 0.dp,
        scrimColor = Couleurs.Voile,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 22.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Couleurs.Neutre600, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp), content = contenu)
    }
}
