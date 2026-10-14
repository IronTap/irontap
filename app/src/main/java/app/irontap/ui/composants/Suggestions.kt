package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.irontap.logique.Suggestion
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Elevation
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import app.irontap.ui.theme.elevation

/** Menu d'autocomplétion : surface, élévation md, lignes de 52 dp ; la partie correspondante en 600 accent-200. */
@Composable
fun ListeSuggestions(suggestions: List<Suggestion>, onChoisir: (Suggestion) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .elevation(Elevation.Md, Formes.Grande)
            .background(Couleurs.Surface, Formes.Grande)
            .clip(Formes.Grande)
            .padding(vertical = 4.dp),
    ) {
        suggestions.forEach { suggestion ->
            val interactions = remember { MutableInteractionSource() }
            val appuye by interactions.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(if (appuye) Couleurs.Neutre800 else Color.Transparent)
                    .clickable(interactions, indication = null, role = Role.Button) { onChoisir(suggestion) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = buildAnnotatedString {
                        val nom = suggestion.nom
                        append(nom, 0, suggestion.debut)
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Couleurs.Accent200)) {
                            append(nom, suggestion.debut, suggestion.fin)
                        }
                        append(nom, suggestion.fin, nom.length)
                    },
                    style = Typo.Suggestion,
                    color = Couleurs.Texte,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
