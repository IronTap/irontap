package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.donnees.RoleSuperset
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo

/** Lettre A / B d'un exercice de superset : carré à coins de 5 dp ([taille] 20, 18 dans le détail). */
@Composable
fun BadgeLettre(
    role: RoleSuperset,
    modifier: Modifier = Modifier,
    fond: Color = Couleurs.Accent700,
    texte: Color = Couleurs.Accent100,
    taille: Dp = 20.dp,
    style: TextStyle = Typo.BadgeOnglet,
) {
    Box(
        modifier
            .size(taille)
            .background(fond, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(role.name, style = style, color = texte) }
}

/**
 * Étiquette accent d'un superset : « 🔗 En superset avec … » de la popup d'historique (S4 : 12 sp, icône 12, padding
 * 3/9/3/7, rayon 4), ou « 🔗 Superset » du détail ([detail], H : 11 sp, icône 11, padding 3/10, rayon 6).
 */
@Composable
fun EtiquetteSuperset(texte: String, modifier: Modifier = Modifier, detail: Boolean = false) {
    Row(
        modifier = modifier
            .background(Couleurs.Accent800, RoundedCornerShape(if (detail) 6.dp else 4.dp))
            .padding(start = if (detail) 10.dp else 7.dp, top = 3.dp, end = if (detail) 10.dp else 9.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (detail) 4.dp else 6.dp),
    ) {
        Icone(R.drawable.ic_link_simple_small, taille = if (detail) 11.dp else 12.dp, couleur = Couleurs.Accent100)
        Text(texte, style = if (detail) Typo.Etiquette else Typo.EtiquetteSuperset, color = Couleurs.Accent100)
    }
}
