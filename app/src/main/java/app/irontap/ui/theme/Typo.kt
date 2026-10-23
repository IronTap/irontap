package app.irontap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.irontap.R

/** Inter embarquée (res/font) : aucune police téléchargeable, donc aucun accès réseau. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * Style de base : Inter, chiffres tabulaires partout (les valeurs ne « dansent » pas).
 * L'interligne est réparti également au-dessus et au-dessous du texte, comme en CSS.
 */
private val Base = TextStyle(
    fontFamily = Inter,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Styles Nocturne : interligne 1,55 par défaut (`body` des maquettes). */
private fun style(taille: Int, graisse: FontWeight = FontWeight.Medium) =
    Base.copy(fontSize = taille.sp, fontWeight = graisse, lineHeight = 1.55.em)

/** Texte des champs et des boutons écrits avec le raccourci CSS `font:` : interligne naturel de la police. */
private fun TextStyle.interligneNaturel() = copy(lineHeight = TextUnit.Unspecified)

/**
 * Échelle typographique Nocturne (README § Typographie). Graisse maximale 500,
 * sauf la partie surlignée de l'autocomplétion (600).
 * Les couleurs ne sont pas fixées ici : elles dépendent de l'état (actif, en pause, erreur…).
 */
object Typo {
    /**
     * Chrono : 40 / 500, −0,02 em, interligne 1.
     * Affiché en `MM:SS` (voir DECISIONS.md) : en chiffres tabulaires, `HH:MM:SS` mesure ≈ 172 dp,
     * plus que la place laissée par les deux boutons du bandeau (≈ 168 dp).
     * Compose ne réduit pas une ligne seule sous la hauteur naturelle de la police (≈ 48,7 dp ici) :
     * afficher avec `Modifier.hauteurLigne(40.dp)` pour retrouver la boîte de 40 dp des maquettes.
     */
    val Chrono = style(40).copy(letterSpacing = (-0.02).em, lineHeight = 1.em)

    /** Valeur des champs poids et répétitions : 38 / 500, −0,02 em, interligne naturel (champ). */
    val Valeur = style(38).copy(letterSpacing = (-0.02).em).interligneNaturel()

    /** Date en tête du sélecteur de date (2b) : 28 / 500. */
    val TitreDate = style(28)

    /** Titre de l'écran d'accueil : 24 / 500, −0,015 em. */
    val TitreAccueil = style(24).copy(letterSpacing = (-0.015).em)

    /** Unité « kg » : 24 / 400 (couleur neutre-400). */
    val Unite = style(24, FontWeight.Normal)

    /** Nom d'exercice et date dans un champ : 22 / 500, interligne naturel (champ). */
    val Champ = style(22).interligneNaturel()

    /** Grand titre : feuille de pré-permission (écran 9), accueil vide (1b) : 22 / 500. */
    val GrandTitre = style(22)

    /** Titre de dialogue : 21 / 500. */
    val TitreDialogue = style(21)

    /** Titre d'en-tête, valeur d'une série dans le détail : 20 / 500. */
    val TitreEntete = style(20)

    /** Date dans l'en-tête du détail : 19 / 500. */
    val TitreDetail = style(19)

    /** Boutons d'ajout rapide +X : 19 / 500, interligne naturel. */
    val BoutonAjout = style(19).interligneNaturel()

    /** Grandes actions : 18 / 500, interligne 1,15. */
    val GrandeAction = style(18).copy(lineHeight = 1.15.em)

    /** Grandes actions de la saisie, sur 3 colonnes (3·D) : 16 / 500, interligne 1,15. */
    val GrandeActionCompacte = style(16).copy(lineHeight = 1.15.em)

    /** Valeur d'un palier dans le tableau d'édition d'une dégressive (7·D) : 24 / 500, interligne naturel (champ). */
    val ValeurPalier = style(24).interligneNaturel()

    /** Palier dégressif sous la valeur d'une série (détail 6a, popup 3g) : 16 / 500. */
    val PalierDegressif = style(16)

    /** Nom d'exercice dans le détail, titre du bandeau « en cours » : 18 / 500. */
    val TitreSection = style(18)

    /** Suggestion d'autocomplétion : 18 / 400, interligne naturel (la partie correspondante passe en 600). */
    val Suggestion = style(18, FontWeight.Normal).interligneNaturel()

    /** Boutons d'action « feuille » (Partager…, Autoriser les notifications) : 17 / 500. */
    val ActionFeuille = style(17)

    /** Boutons secondaires et contour de 52–56 dp (Annuler, Nouvel entraînement en secondaire) : 16 / 500. */
    val ActionMoyenne = style(16)

    /** Date d'une carte d'historique : 17 / 500. */
    val DateCarte = style(17)

    /** Corps de dialogue : 15 / 400, interligne 1,45 (couleur neutre-300). */
    val CorpsDialogue = style(15, FontWeight.Normal).copy(lineHeight = 1.45.em)

    /** Bouton texte (Annuler, Terminer, « + Ajouter une série ») : 15 / 500, interligne naturel. */
    val Action = style(15).interligneNaturel()

    /** Rappel « Exercice N · Série M » de l'en-tête de saisie : 15 / 500. */
    val EnteteSaisie = style(15)

    /** Texte courant, éléments du menu ⋮ : 15 / 400. */
    val Corps = style(15, FontWeight.Normal)

    /** Lien discret (« Terminer l'entraînement »), sous-ligne : 14 / 500. */
    val Discret = style(14)

    /** Texte secondaire, toast : 14 / 400. */
    val Secondaire = style(14, FontWeight.Normal)

    /** Libellé de champ, message d'erreur : 13 / 400 (couleur neutre-300). */
    val Libelle = style(13, FontWeight.Normal)

    /** En-tête de séance dans l'historique de l'exercice (3g) : 13 / 500. */
    val EnteteSeance = style(13)

    /** Petit texte (libellé au-dessus de l'en-tête de saisie) : 12 / 400. */
    val Petit = style(12, FontWeight.Normal)

    /** Badge « ↓ dégressive » de l'en-tête de saisie : 11 / 500 (hérité du numéro de série dans la maquette). */
    val Badge = style(11)

    /** Pastilles de la ligne du nom (« Superset », « Historique A ») : 13 / 500, interligne naturel. */
    val Pastille = style(13).interligneNaturel()

    /** Nom d'un onglet de superset (S3) : 16 / 500, interligne naturel (champ). */
    val Onglet = style(16).interligneNaturel()

    /** Lettre A / B des onglets et de la feuille « Ajouter un tour » : 11 / 600. */
    val BadgeOnglet = style(11, FontWeight.SemiBold).interligneNaturel()

    /** Lettre A / B de l'en-tête de la carte superset du détail (H) : 10 / 600. */
    val BadgeOngletPetit = style(10, FontWeight.SemiBold).interligneNaturel()

    /** Titre d'une ligne de l'écran À propos (10a) : 16 / 400. */
    val LigneAPropos = style(16, FontWeight.Normal)

    /** Texte d'une licence (10b) : 13 / 400, interligne 1,6. */
    val TexteLicence = style(13, FontWeight.Normal).copy(lineHeight = 1.6.em)

    /** Noms A et B de l'en-tête de la carte superset du détail (H) : 15 / 500. */
    val TitreCarte = style(15)

    /** Valeur des champs de la feuille « Ajouter un tour » (3·S+ T1) : 28 / 500, interligne naturel. */
    val ValeurTour = style(28).interligneNaturel()

    /** Unité « kg » de la feuille « Ajouter un tour » : 17 / 400. */
    val UniteTour = style(17, FontWeight.Normal).interligneNaturel()

    /** « × » entre poids et répétitions de la feuille « Ajouter un tour » : 20 / 400. */
    val SigneTour = style(20, FontWeight.Normal).interligneNaturel()

    /** Étiquette « En superset avec … » de la popup d'historique (S4) : 12 / 400. */
    val EtiquetteSuperset = style(12, FontWeight.Normal)

    /**
     * Aperçu de l'export (écran 8) : 12 / 400 en chasse fixe, interligne 1,55. Police monospace du système :
     * rien à embarquer pour un seul bloc de texte.
     */
    val Apercu = style(12, FontWeight.Normal).copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = null)

    /** Étiquette de libellé d'entraînement : 11 / 400, +0,02 em (classe `.tag`). */
    val Etiquette = style(11, FontWeight.Normal).copy(letterSpacing = 0.02.em)

    /** Sur-titre (« REPOS · EN COURS ») : 11 / 400, majuscules, +0,08 em. Le texte est passé en majuscules à l'appel. */
    val SurTitre = style(11, FontWeight.Normal).copy(letterSpacing = 0.08.em)
}

/** Typographie Material 3 : mêmes tailles que M3, mais en Inter avec chiffres tabulaires. */
internal val TypographieMaterial: Typography = Typography().run {
    fun TextStyle.inter() = merge(Base)
    copy(
        displayLarge = displayLarge.inter(),
        displayMedium = displayMedium.inter(),
        displaySmall = displaySmall.inter(),
        headlineLarge = headlineLarge.inter(),
        headlineMedium = headlineMedium.inter(),
        headlineSmall = headlineSmall.inter(),
        titleLarge = titleLarge.inter(),
        titleMedium = titleMedium.inter(),
        titleSmall = titleSmall.inter(),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(),
        labelMedium = labelMedium.inter(),
        labelSmall = labelSmall.inter(),
    )
}

/**
 * Hauteur de ligne inférieure à la hauteur naturelle de la police, comme `line-height` en CSS :
 * le texte est mesuré normalement puis centré dans une boîte de [hauteur] (il peut en déborder).
 */
fun Modifier.hauteurLigne(hauteur: Dp): Modifier = layout { mesurable, contraintes ->
    val texte = mesurable.measure(contraintes.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val boite = hauteur.roundToPx()
    layout(texte.width, boite) { texte.place(0, (boite - texte.height) / 2) }
}
