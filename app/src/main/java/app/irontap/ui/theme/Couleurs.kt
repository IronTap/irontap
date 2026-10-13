package app.irontap.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens de couleur du système « Nocturne » (README du handoff, thème sombre unique).
 *
 * Les couleurs OKLCH des maquettes sont converties exactement en sRGB : les valeurs « ≈ » du README
 * sont des approximations, le rendu de référence est celui du navigateur.
 */
object Couleurs {
    val Fond = Color(0xFF161826)
    val Surface = Color(0xFF232532)
    val Texte = Color(0xFFE9E9ED)

    /** Séparateur et bordure de champ : texte à 16 % d'opacité. */
    val Separateur = Texte.copy(alpha = 0.16f)

    val Neutre100 = Color(0xFFF3F5FE)
    val Neutre200 = Color(0xFFE4E7F5)
    val Neutre300 = Color(0xFFCFD3E5)
    val Neutre400 = Color(0xFFB2B6CA)
    val Neutre500 = Color(0xFF9397AB)
    val Neutre600 = Color(0xFF75798C)
    val Neutre700 = Color(0xFF595D6C)
    val Neutre800 = Color(0xFF3F424D)
    val Neutre900 = Color(0xFF292B31)

    val Accent = Color(0xFF9184D9)
    val Accent100 = Color(0xFFF5F4FF)
    val Accent200 = Color(0xFFE7E5FE)
    val Accent300 = Color(0xFFD2CEFD)
    val Accent400 = Color(0xFFB5ABFC)
    val Accent500 = Color(0xFF968AE0)
    val Accent600 = Color(0xFF796CBF)
    val Accent700 = Color(0xFF5D5294)
    val Accent800 = Color(0xFF423A6A)
    val Accent900 = Color(0xFF2B2741)

    /** oklch(0.72 0.13 28) : bordure d'erreur, contour des boutons de suppression. */
    val ErreurBordure = Color(0xFFEB8376)

    /** oklch(0.78 0.11 28) : message d'erreur sous un champ. */
    val ErreurTexte = Color(0xFFF69C90)

    /** oklch(0.80 0.10 28) : texte des actions destructives (menu, bottom sheet). */
    val Destructif = Color(0xFFF8A59A)

    /** oklch(0.82 0.09 28) : « Supprimer » dans le dialogue de confirmation. */
    val DestructifDialogue = Color(0xFFF9AEA4)

    /** oklch(0.36 0.09 28) : fond révélé par le balayage d'une série. */
    val FondSuppression = Color(0xFF642721)

    /** oklch(0.94 0.03 28) : icône et texte « Supprimer » sur [FondSuppression]. */
    val TexteSuppression = Color(0xFFFFE4E0)

    /**
     * Voile des dialogues : neutre-900 à 72 %.
     * À passer explicitement (`scrimColor`) aux ModalBottomSheet : M3 n'en reprend que la teinte.
     */
    val Voile = Neutre900.copy(alpha = 0.72f)
}
