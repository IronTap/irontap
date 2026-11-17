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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.irontap.logique.RIR_MAX
import app.irontap.logique.formaterRir
import app.irontap.logique.mentionRir
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

/** Libellé des pastilles du RIR (saisie, feuille d'édition 7). */
const val LIBELLE_RIR = "Répétitions en réserve (RIR)"

/**
 * Pastilles du RIR (DECISIONS.md) : 0, 1, 2, 3, 4 et « 5+ », 48 dp de haut, réparties sur la largeur (8 dp entre deux),
 * rayon 14, valeur 19 sp. Un appui choisit la valeur ; un appui sur la pastille choisie l'efface ([onChoisir] reçoit
 * `null`). Choisie : fond accent-800, contour accent 2 dp, texte accent-200 ; appuyée : fond accent-800 et contour accent,
 * comme les boutons +X ; sinon fond [fond] et filet séparateur. Lecteur d'écran : boutons radio « RIR 2 », suivis au
 * besoin de [precision] (« RIR 2, exercice B »).
 */
@Composable
fun PastillesRir(
    choisi: Int?,
    onChoisir: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    fond: Color = Couleurs.Surface,
    precision: String? = null,
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..RIR_MAX).forEach { valeur ->
            val coche = valeur == choisi
            val interactions = remember { MutableInteractionSource() }
            val appuye by interactions.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(Formes.Grande)
                    .background(if (coche || appuye) Couleurs.Accent800 else fond)
                    .border(if (coche) 2.dp else 1.dp, if (coche || appuye) Couleurs.Accent else Couleurs.Separateur, Formes.Grande)
                    .selectable(selected = coche, interactionSource = interactions, indication = null, role = Role.RadioButton) {
                        onChoisir(if (coche) null else valeur)
                    }
                    .semantics { contentDescription = listOfNotNull(mentionRir(valeur), precision).joinToString(", ") },
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

/** Repère du RIR d'un palier (historique 3g, détail) : « RIR 2 » 13 sp accent-200 sur fond accent-800, rayon 4. */
@Composable
fun BadgeRir(rir: Int, modifier: Modifier = Modifier) {
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
