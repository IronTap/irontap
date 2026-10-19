package app.irontap.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
fun Modifier.cadreChamp(erreur: Boolean = false, focus: Boolean = false, fond: Color = Couleurs.Surface): Modifier = this
    .background(fond, Formes.Grande)
    .border(
        width = if (erreur) 2.dp else 1.dp,
        color = when {
            erreur -> Couleurs.ErreurBordure
            focus -> Couleurs.Accent
            else -> Couleurs.Separateur
        },
        shape = Formes.Grande,
    )

/**
 * Champ texte d'une ligne en 22 / 500 (nom d'exercice 52 dp, libellé d'entraînement 62 dp) :
 * placeholder neutre-600, curseur accent, bordure accent au focus.
 */
@Composable
fun ChampTexte(
    etat: TextFieldState,
    placeholder: String,
    hauteur: Dp,
    longueurMax: Int,
    modifier: Modifier = Modifier,
    erreur: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
) {
    val interactions = remember { MutableInteractionSource() }
    val focus by interactions.collectIsFocusedAsState()
    val filtre = remember(longueurMax) { InputTransformation.maxLength(longueurMax) }
    BasicTextField(
        state = etat,
        modifier = modifier.fillMaxWidth(),
        inputTransformation = filtre,
        textStyle = Typo.Champ.copy(color = Couleurs.Texte),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactions,
        cursorBrush = SolidColor(Couleurs.Accent),
        decorator = { champ ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hauteur)
                    .cadreChamp(erreur, focus)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (etat.text.isEmpty()) Text(placeholder, style = Typo.Champ, color = Couleurs.Neutre600)
                champ()
            }
        },
    )
}

/**
 * Champ « Poids » : 62 dp, valeur 38 sp, « kg » à droite. Chiffres et un séparateur décimal (point ou virgule).
 * [fond] : surface sur l'écran, `bg` dans la feuille d'édition (contraste avec la feuille).
 */
@Composable
fun ChampPoids(etat: TextFieldState, modifier: Modifier = Modifier, fond: Color = Couleurs.Surface) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .cadreChamp(fond = fond)
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
fun ChampRepetitions(etat: TextFieldState, erreur: Boolean, modifier: Modifier = Modifier, fond: Color = Couleurs.Surface) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .cadreChamp(erreur, fond = fond)
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

/**
 * Champ d'un palier dans le tableau d'édition d'une dégressive (7·D) : 48 dp de haut, [largeur] au moins (poids 100,
 * répétitions 68 ; un poids comme « 102,25 » élargit le champ plutôt que d'être coupé), surface, rayon 8, filet
 * séparateur ; actif : bordure accent 2 dp ; erreur : 2 dp couleur erreur. Valeur 24 / 500 calée à droite, suivie
 * de « kg » 15 / 400 neutre-400 pour le poids. Clavier numérique ; [onSuivant] reçoit l'action « Suivant »
 * ([imeAction]) du clavier.
 */
@Composable
fun ChampPalier(
    etat: TextFieldState,
    poids: Boolean,
    largeur: Dp,
    description: String,
    modifier: Modifier = Modifier,
    erreur: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onSuivant: KeyboardActionHandler? = null,
    apparence: ApparenceChampPalier = ApparenceChampPalier.Tableau,
    /** Valeur affichée en neutre-600 tant que le champ est vide (valeurs du tour précédent). */
    indication: String = "0",
    /** Le champ prend toute la largeur donnée par [modifier] (poids de la feuille « Ajouter un tour »). */
    remplir: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val focus by interactions.collectIsFocusedAsState()
    // Largeur du contenu (valeur, « kg », marges, curseur) : au-delà de [largeur], le champ s'élargit.
    val mesureur = rememberTextMeasurer()
    val marge = apparence.marge ?: if (poids) 10.dp else 12.dp
    val largeurContenu = with(LocalDensity.current) {
        val valeur = mesureur.measure(etat.text.toString().ifEmpty { indication }, apparence.valeur).size.width.toDp()
        val unite = if (poids) mesureur.measure("kg", apparence.unite).size.width.toDp() + apparence.ecartUnite else 0.dp
        valeur + unite + marge * 2 + 2.dp
    }
    BasicTextField(
        state = etat,
        modifier = modifier.semantics { contentDescription = description },
        inputTransformation = if (poids) FiltrePoids else FiltreRepetitions,
        textStyle = apparence.valeur.copy(color = Couleurs.Texte, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = if (poids) KeyboardType.Decimal else KeyboardType.Number, imeAction = imeAction),
        onKeyboardAction = onSuivant,
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactions,
        cursorBrush = SolidColor(Couleurs.Accent),
        decorator = { champ ->
            Row(
                modifier = Modifier
                    .then(if (remplir) Modifier.fillMaxWidth().height(apparence.hauteur) else Modifier.size(width = maxOf(largeur, largeurContenu), height = apparence.hauteur))
                    .background(apparence.fond, apparence.forme)
                    .border(
                        width = if (erreur || focus) 2.dp else 1.dp,
                        color = when {
                            erreur -> Couleurs.ErreurBordure
                            focus -> Couleurs.Accent
                            else -> Couleurs.Separateur
                        },
                        shape = apparence.forme,
                    )
                    .padding(horizontal = marge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(apparence.ecartUnite, Alignment.End),
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (etat.text.isEmpty()) Text(indication, style = apparence.valeur, color = Couleurs.Neutre600)
                    champ()
                }
                if (poids) Text("kg", style = apparence.unite, color = if (etat.text.isEmpty()) apparence.uniteSansValeur else Couleurs.Neutre400)
            }
        },
    )
}

/**
 * Apparence d'un [ChampPalier] : tableau des paliers (7·D : 48 dp, 24 sp, surface, rayon 8) ou feuille « Ajouter un
 * tour » (3·S+ T1 : 58 dp, 28 sp, `bg`, rayon 14, marges 14, « kg » 17 sp à 6 dp).
 */
class ApparenceChampPalier(
    val hauteur: Dp,
    val valeur: TextStyle,
    val unite: TextStyle,
    val fond: Color,
    val forme: Shape,
    val marge: Dp?,
    val ecartUnite: Dp,
    /** Couleur de « kg » tant que le champ est vide (indication du tour précédent : neutre-500). */
    val uniteSansValeur: Color = Couleurs.Neutre400,
) {
    companion object {
        val Tableau = ApparenceChampPalier(48.dp, Typo.ValeurPalier, Typo.Corps, Couleurs.Surface, Formes.Moyenne, marge = null, ecartUnite = 4.dp)
        val Tour = ApparenceChampPalier(
            58.dp, Typo.ValeurTour, Typo.UniteTour, Couleurs.Fond, Formes.Grande, marge = 14.dp, ecartUnite = 6.dp,
            uniteSansValeur = Couleurs.Neutre500,
        )
    }
}

/** Rangée de boutons +X ronds de 58 dp, répartis sur la largeur, 8 dp sous le champ. */
@Composable
fun RangeeAjouts(valeurs: List<Int>, onAjout: (Int) -> Unit, modifier: Modifier = Modifier, fond: Color = Couleurs.Surface) {
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
                fond = fond,
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
