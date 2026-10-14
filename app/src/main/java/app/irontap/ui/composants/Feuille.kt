package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.irontap.ui.theme.Couleurs

/**
 * Feuille du bas (écrans 7, 8, 9) : surface, rayon 24 en haut, poignée 32 × 4 neutre-600, voile des
 * dialogues, contenu à [margeHorizontale] des bords (20 dp ; 16 dans les feuilles à champs). Glisser vers le bas, Retour ou un appui sur le voile ferme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleNocturne(
    onFermer: () -> Unit,
    etat: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    margeHorizontale: Dp = 20.dp,
    espacePoignee: Dp = 22.dp,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    // Pas d'élévation lg : l'ombre serait dessinée avant le décalage de la feuille, en haut de l'écran.
    // Le voile suffit à la détacher du contenu.
    val forme = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = etat,
        shape = forme,
        containerColor = Couleurs.Surface,
        contentColor = Couleurs.Texte,
        tonalElevation = 0.dp,
        scrimColor = Couleurs.Voile,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = espacePoignee)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Couleurs.Neutre600, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        // Défile si le clavier ne laisse pas la place à tout le contenu.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = margeHorizontale),
            content = contenu,
        )
    }
}
