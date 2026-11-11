package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes

/**
 * Case « à l'échec » d'une série (DECISIONS.md) : carré de [taille], batterie vide de [tailleIcone]. Décochée : fond
 * [fond], filet séparateur, icône neutre-400 ; cochée : fond accent-800, contour accent 2 dp, icône accent-200. Appuyée :
 * fond accent-800 et contour accent, comme le bouton Historique. [description] : « À l'échec », suivi au besoin de
 * l'exercice (« À l'échec, exercice B »).
 */
@Composable
fun CaseEchec(
    coche: Boolean,
    onBasculer: () -> Unit,
    taille: Dp,
    modifier: Modifier = Modifier,
    fond: Color = Couleurs.Surface,
    forme: Shape = Formes.Grande,
    tailleIcone: Dp = 24.dp,
    description: String = DESCRIPTION_ECHEC,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(taille)
            .clip(forme)
            .background(if (coche || appuye) Couleurs.Accent800 else fond)
            .border(if (coche) 2.dp else 1.dp, if (coche || appuye) Couleurs.Accent else Couleurs.Separateur, forme)
            .toggleable(value = coche, interactionSource = interactions, indication = null, role = Role.Checkbox) { onBasculer() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icone(R.drawable.ic_battery_empty, taille = tailleIcone, couleur = if (coche) Couleurs.Accent200 else Couleurs.Neutre400)
    }
}

/** Repère d'une série à l'échec dans l'historique et le détail : batterie vide accent-300 de [taille]. */
@Composable
fun IconeEchec(modifier: Modifier = Modifier, taille: Dp = 16.dp) {
    Icone(R.drawable.ic_battery_empty_small, modifier, taille = taille, couleur = Couleurs.Accent300, description = DESCRIPTION_ECHEC)
}

const val DESCRIPTION_ECHEC = "À l’échec"
