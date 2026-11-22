package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.irontap.donnees.Rir
import app.irontap.logique.RIR_MAX
import app.irontap.logique.formaterRir
import app.irontap.logique.mentionRir
import app.irontap.logique.rirApresAppui
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

/** Libellé des pastilles du RIR (saisie, feuille d'édition 7). */
const val LIBELLE_RIR = "Répétitions en réserve (RIR)"

/**
 * Pastilles du RIR (DECISIONS.md) : 0, 1, 2, 3, 4 et « 5+ », 48 dp de haut, réparties sur la largeur (8 dp entre deux),
 * rayon 14, valeur 19 sp. [onAppui] reçoit la pastille touchée ; le RIR qui en résulte suit [rirApresAppui] (choix,
 * plage de deux voisines, effacement). Choisie : fond accent-800, contour accent 2 dp, texte accent-200 ; les deux
 * pastilles d'une plage ne forment qu'une forme, l'écart entre elles compris. Appuyée : fond accent-800 et contour
 * accent, comme les boutons +X ; sinon fond [fond] et filet séparateur. Lecteur d'écran : cases à cocher « RIR 2 »,
 * suivies au besoin de [precision] (« RIR 2, exercice B »).
 */
@Composable
fun PastillesRir(
    choisi: Rir?,
    onAppui: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fond: Color = Couleurs.Surface,
    precision: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { if (choisi != null) dessinerChoix(choisi, EcartPastilles.toPx()) },
        horizontalArrangement = Arrangement.spacedBy(EcartPastilles),
    ) {
        (0..RIR_MAX).forEach { valeur ->
            val coche = choisi != null && valeur in choisi.bas..choisi.haut
            val interactions = remember { MutableInteractionSource() }
            val appuye by interactions.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(Formes.Grande)
                    // Choisie : dessinée derrière la rangée, d'un seul tenant pour une plage.
                    .then(
                        if (coche) {
                            Modifier
                        } else {
                            Modifier
                                .background(if (appuye) Couleurs.Accent800 else fond)
                                .border(1.dp, if (appuye) Couleurs.Accent else Couleurs.Separateur, Formes.Grande)
                        },
                    )
                    .toggleable(value = coche, interactionSource = interactions, indication = null, role = Role.Checkbox) { onAppui(valeur) }
                    .semantics { contentDescription = listOfNotNull(mentionRir(Rir(valeur)), precision).joinToString(", ") },
                contentAlignment = Alignment.Center,
            ) {
                // Le lecteur d'écran lit la description seule (« RIR 2 »), pas aussi le chiffre.
                Text(
                    formaterRir(valeur),
                    style = Typo.BoutonAjout,
                    color = if (coche) Couleurs.Accent200 else Couleurs.Texte,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

private val EcartPastilles = 8.dp

/** Fond et contour du RIR [choisi], de sa pastille basse à sa pastille haute (écart compris pour une plage). */
private fun DrawScope.dessinerChoix(choisi: Rir, ecart: Float) {
    val largeur = (size.width - ecart * RIR_MAX) / (RIR_MAX + 1)
    val debut = choisi.bas * (largeur + ecart)
    val fin = choisi.haut * (largeur + ecart) + largeur
    val gauche = if (layoutDirection == LayoutDirection.Rtl) size.width - fin else debut
    val forme = Size(fin - debut, size.height)
    val rayon = Formes.Grande.topStart.toPx(forme, this)
    drawRoundRect(Couleurs.Accent800, Offset(gauche, 0f), forme, CornerRadius(rayon))
    val trait = 2.dp.toPx()
    drawRoundRect(
        Couleurs.Accent,
        Offset(gauche + trait / 2, trait / 2),
        Size(fin - debut - trait, size.height - trait),
        CornerRadius(rayon - trait / 2),
        style = Stroke(trait),
    )
}

/** Repère du RIR d'un palier (historique 3g, détail) : « RIR 2 », « RIR 2-3 », 13 sp accent-200 sur fond accent-800, rayon 4. */
@Composable
fun BadgeRir(rir: Rir, modifier: Modifier = Modifier) {
    Text(
        text = mentionRir(rir),
        style = Typo.Pastille,
        color = Couleurs.Accent200,
        maxLines = 1,
        modifier = modifier
            .background(Couleurs.Accent800, Formes.Petite)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
