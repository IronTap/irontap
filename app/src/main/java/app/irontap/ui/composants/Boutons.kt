package app.irontap.ui.composants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

/** Apparence d'un bouton Nocturne, au repos et appuyé. */
private data class Apparence(
    val fond: Color,
    val fondAppuye: Color,
    val bordure: Color?,
)

/** Action principale : fond accent-900, bordure accent ; appuyée : accent-800. L'accent ne sert jamais d'aplat. */
private val Principale = Apparence(Couleurs.Accent900, Couleurs.Accent800, Couleurs.Accent)

/** Action secondaire : fond surface, bordure séparateur ; appuyée : neutre-800. */
private val Secondaire = Apparence(Couleurs.Surface, Couleurs.Neutre800, Couleurs.Separateur)

/**
 * Action principale (texte accent-200). Par défaut : grand bouton de 64 dp en 18 / 500.
 * La largeur est celle du contenu : passer `Modifier.fillMaxWidth()` ou `weight(1f)` à l'appel.
 */
@Composable
fun BoutonPrincipal(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    hauteur: Dp = 64.dp,
    forme: Shape = Formes.Grande,
    style: TextStyle = Typo.GrandeAction,
    couleurTexte: Color = Couleurs.Accent200,
    tailleIcone: Dp = 20.dp,
    margeHorizontale: Dp = 16.dp,
    actif: Boolean = true,
) = BoutonNocturne(Principale, texte, onClick, modifier, icone, hauteur, forme, style, couleurTexte, tailleIcone, margeHorizontale, actif)

/**
 * Action secondaire. Par défaut : 56 dp en 16 / 500 neutre-200 (« Nouvel entraînement » en 1c) ;
 * les grandes actions de la saisie passent `hauteur = 72.dp, style = Typo.GrandeAction, couleurTexte = Couleurs.Texte`.
 */
@Composable
fun BoutonSecondaire(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    hauteur: Dp = 56.dp,
    forme: Shape = Formes.Grande,
    style: TextStyle = Typo.ActionMoyenne,
    couleurTexte: Color = Couleurs.Neutre200,
    tailleIcone: Dp = 18.dp,
    margeHorizontale: Dp = 16.dp,
    actif: Boolean = true,
) = BoutonNocturne(Secondaire, texte, onClick, modifier, icone, hauteur, forme, style, couleurTexte, tailleIcone, margeHorizontale, actif)

/**
 * Action secondaire sans fond (« Annuler » des écrans 2a / 2c) : 52 dp en 16 / 500 neutre-200, contour séparateur.
 * Avec [bordure] accent : « Reprendre l'entraînement » du bandeau de l'accueil (1c).
 */
@Composable
fun BoutonContour(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    hauteur: Dp = 52.dp,
    forme: Shape = Formes.Grande,
    style: TextStyle = Typo.ActionMoyenne,
    couleurTexte: Color = Couleurs.Neutre200,
    tailleIcone: Dp = 18.dp,
    margeHorizontale: Dp = 16.dp,
    actif: Boolean = true,
    bordure: Color = Couleurs.Separateur,
    fondAppuye: Color = Couleurs.Neutre800,
) = BoutonNocturne(Apparence(Color.Transparent, fondAppuye, bordure), texte, onClick, modifier, icone, hauteur, forme, style, couleurTexte, tailleIcone, margeHorizontale, actif)

@Composable
private fun BoutonNocturne(
    apparence: Apparence,
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier,
    @DrawableRes icone: Int?,
    hauteur: Dp,
    forme: Shape,
    style: TextStyle,
    couleurTexte: Color,
    tailleIcone: Dp,
    margeHorizontale: Dp,
    actif: Boolean,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Row(
        modifier = modifier
            // Hauteur minimale : le bouton grandit si une grande taille de police fait passer le texte sur 2 lignes.
            .heightIn(min = hauteur)
            .alpha(if (actif) 1f else 0.45f)
            .clip(forme)
            .background(if (appuye) apparence.fondAppuye else apparence.fond)
            .then(if (apparence.bordure != null) Modifier.border(1.dp, apparence.bordure, forme) else Modifier)
            // Le retour visuel est le changement de fond (maquettes), pas l'onde Material.
            .clickable(interactions, indication = null, enabled = actif, role = Role.Button, onClick = onClick)
            .padding(horizontal = margeHorizontale),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) Icone(icone, taille = tailleIcone, couleur = couleurTexte)
        Text(texte, style = style, color = couleurTexte, textAlign = TextAlign.Center)
    }
}

/** Étiquette du libellé d'un entraînement : fond accent-800, texte accent-100, 11 sp, padding 3×10, rayon 6. */
@Composable
fun EtiquetteLibelle(libelle: String, modifier: Modifier = Modifier) {
    Text(
        text = libelle,
        style = Typo.Etiquette,
        color = Couleurs.Accent100,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Couleurs.Accent800)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * Bouton rond (Play/Pause et Relancer du chrono, +X des champs), ou de [forme] donnée (bouton Historique, carré
 * à coins arrondis). Le retour visuel est le changement de fond et de contour à l'appui, comme dans les maquettes.
 */
@Composable
fun BoutonRond(
    onClick: () -> Unit,
    taille: Dp,
    fond: Color,
    fondAppuye: Color,
    bordure: Color,
    modifier: Modifier = Modifier,
    bordureAppuyee: Color = bordure,
    forme: Shape = CircleShape,
    description: String? = null,
    /** Désactivé : 35 % d'opacité (icône Exporter de l'accueil vide, 1b). */
    actif: Boolean = true,
    contenu: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(taille)
            .alpha(if (actif) 1f else 0.35f)
            .clip(forme)
            .background(if (appuye) fondAppuye else fond)
            .border(1.dp, if (appuye) bordureAppuyee else bordure, forme)
            .clickable(interactions, indication = null, enabled = actif, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

/**
 * Bouton texte sans fond ni contour (« Annuler » des dialogues : accent-300 ; lien discret
 * « Terminer l'entraînement » : neutre-400). Appuyé : voile de texte à 6 %.
 */
@Composable
fun BoutonTexte(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    couleur: Color = Couleurs.Accent300,
    style: TextStyle = Typo.Action,
    tailleIcone: Dp = 17.dp,
    margeHorizontale: Dp = 18.dp,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(Formes.Moyenne)
            .background(if (appuye) Couleurs.Texte.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = margeHorizontale),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) Icone(icone, taille = tailleIcone, couleur = couleur)
        Text(texte, style = style, color = couleur)
    }
}
