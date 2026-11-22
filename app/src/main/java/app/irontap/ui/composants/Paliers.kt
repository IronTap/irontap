package app.irontap.ui.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.donnees.Rir
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo

/**
 * Valeur d'une série : palier principal en [stylePrincipal], puis paliers dégressifs empilés dessous
 * (gap 2) en 16 / 500 neutre-300, précédés d'une flèche ↓ accent-300 de 13 dp (détail 6a, popup 3g).
 * [retrait] : décalage des paliers dégressifs (2 dp dans la popup 3g, aucun dans le détail 6a). [rirs] : RIR de chaque
 * palier, le principal d'abord, affiché en badge après sa valeur (DECISIONS.md) ; `null` ou absent : pas de badge.
 * Sur une cellule étroite (tour de superset), le badge passe sous la valeur plutôt que de la couper.
 */
@Composable
fun PaliersEmpiles(
    principal: String,
    degressifs: List<String>,
    stylePrincipal: TextStyle,
    modifier: Modifier = Modifier,
    retrait: Dp = 0.dp,
    rirs: List<Rir?> = emptyList(),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AvecRir(rirs.firstOrNull()) {
            Text(principal, style = stylePrincipal, color = Couleurs.Texte)
        }
        degressifs.forEachIndexed { index, palier ->
            AvecRir(rirs.getOrNull(index + 1), Modifier.padding(start = retrait)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icone(R.drawable.ic_arrow_down_small, taille = 13.dp, couleur = Couleurs.Accent300, description = "puis")
                    Text(palier, style = Typo.PalierDegressif, color = Couleurs.Neutre300)
                }
            }
        }
    }
}

/** [valeur] suivie du badge de son [rir] s'il est noté, ou dessous si la largeur manque. */
@Composable
private fun AvecRir(rir: Rir?, modifier: Modifier = Modifier, valeur: @Composable () -> Unit) {
    if (rir == null) {
        Box(modifier) { valeur() }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            valeur()
            BadgeRir(rir)
        }
    }
}
