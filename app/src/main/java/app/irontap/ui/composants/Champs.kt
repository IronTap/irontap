package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

/** Libellé au-dessus d'un champ : 13 / 400 neutre-300, 6 dp avant le champ. */
@Composable
fun LibelleChamp(texte: String, modifier: Modifier = Modifier) {
    Text(texte, style = Typo.Libelle, color = Couleurs.Neutre300, modifier = modifier.padding(bottom = 6.dp))
}

/** Message d'erreur sous un champ : icône WarningCircle 15 dp + texte 13 sp. */
@Composable
fun MessageErreur(texte: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(R.drawable.ic_warning_circle, taille = 15.dp, couleur = Couleurs.ErreurTexte)
        Text(texte, style = Typo.Libelle, color = Couleurs.ErreurTexte)
    }
}

/** Fond et contour d'un champ : surface, rayon 14, filet séparateur, ou 2 dp couleur erreur. */
fun Modifier.cadreChamp(erreur: Boolean = false, focus: Boolean = false): Modifier = this
    .background(Couleurs.Surface, Formes.Grande)
    .border(
        width = if (erreur) 2.dp else 1.dp,
        color = when {
            erreur -> Couleurs.ErreurBordure
            focus -> Couleurs.Accent
            else -> Couleurs.Separateur
        },
        shape = Formes.Grande,
    )

/** Champ « Poids » : 62 dp, valeur 38 sp, « kg » à droite. Chiffres et un séparateur décimal (point ou virgule). */
@Composable
fun ChampPoids(etat: TextFieldState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .cadreChamp()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChampValeur(
            etat = etat,
            filtre = FiltrePoids,
            clavier = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        Text("kg", style = Typo.Unite, color = Couleurs.Neutre400)
    }
}

/** Champ « Nombre de répétitions » : 62 dp, valeur 38 sp, entier de 3 chiffres au plus. */
@Composable
fun ChampRepetitions(etat: TextFieldState, erreur: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .cadreChamp(erreur)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        ChampValeur(
            etat = etat,
            filtre = FiltreRepetitions,
            clavier = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChampValeur(
    etat: TextFieldState,
    filtre: InputTransformation,
    clavier: KeyboardOptions,
    modifier: Modifier,
) {
    BasicTextField(
        state = etat,
        modifier = modifier,
        inputTransformation = filtre,
        textStyle = Typo.Valeur.copy(color = Couleurs.Texte),
        keyboardOptions = clavier,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(Couleurs.Accent),
        decorator = { champ ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (etat.text.isEmpty()) Text("0", style = Typo.Valeur, color = Couleurs.Neutre600)
                champ()
            }
        },
    )
}

/** Rangée de boutons +X ronds de 58 dp, répartis sur la largeur, 8 dp sous le champ. */
@Composable
fun RangeeAjouts(valeurs: List<Int>, onAjout: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        valeurs.forEach { valeur ->
            BoutonRond(
                onClick = { onAjout(valeur) },
                taille = 58.dp,
                fond = Couleurs.Surface,
                fondAppuye = Couleurs.Accent800,
                bordure = Couleurs.Separateur,
                bordureAppuyee = Couleurs.Accent,
            ) { Text("+$valeur", style = Typo.BoutonAjout, color = Couleurs.Texte) }
        }
    }
}

/** Jusqu'à 4 chiffres, puis un point ou une virgule et 2 décimales. */
private object FiltrePoids : InputTransformation {
    private val Format = Regex("""\d{0,4}([.,]\d{0,2})?""")
    override fun TextFieldBuffer.transformInput() {
        if (!Format.matches(asCharSequence())) revertAllChanges()
    }
}

private object FiltreRepetitions : InputTransformation {
    private val Format = Regex("""\d{0,3}""")
    override fun TextFieldBuffer.transformInput() {
        if (!Format.matches(asCharSequence())) revertAllChanges()
    }
}
