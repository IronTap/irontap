package app.irontap.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Correspondance Nocturne → rôles Material 3, pour les composants Material utilisés tels quels
 * (sélecteur de date, bottom sheets, menus, champs). L'accent ne sert jamais d'aplat :
 * les « containers » restent sombres.
 */
private val SchemaNocturne = darkColorScheme(
    primary = Couleurs.Accent300,
    onPrimary = Couleurs.Accent900,
    primaryContainer = Couleurs.Accent900,
    onPrimaryContainer = Couleurs.Accent200,
    inversePrimary = Couleurs.Accent700,
    secondary = Couleurs.Accent300,
    onSecondary = Couleurs.Accent900,
    secondaryContainer = Couleurs.Accent800,
    onSecondaryContainer = Couleurs.Accent100,
    tertiary = Couleurs.Accent300,
    onTertiary = Couleurs.Accent900,
    tertiaryContainer = Couleurs.Accent800,
    onTertiaryContainer = Couleurs.Accent100,
    background = Couleurs.Fond,
    onBackground = Couleurs.Texte,
    surface = Couleurs.Fond,
    onSurface = Couleurs.Texte,
    surfaceVariant = Couleurs.Surface,
    onSurfaceVariant = Couleurs.Neutre400,
    surfaceTint = Color.Transparent,
    inverseSurface = Couleurs.Neutre800,
    inverseOnSurface = Couleurs.Neutre100,
    error = Couleurs.ErreurBordure,
    onError = Couleurs.Fond,
    errorContainer = Couleurs.FondSuppression,
    onErrorContainer = Couleurs.TexteSuppression,
    outline = Couleurs.Separateur,
    outlineVariant = Couleurs.Neutre800,
    // Attention : ModalBottomSheet remplace l'opacité du voile par 32 % (passer scrimColor = Couleurs.Voile),
    // et les dialogues utilisent l'assombrissement de fenêtre de la plateforme, pas ce rôle.
    scrim = Couleurs.Voile,
    surfaceBright = Couleurs.Neutre800,
    surfaceDim = Couleurs.Fond,
    surfaceContainerLowest = Couleurs.Fond,
    surfaceContainerLow = Couleurs.Surface,
    surfaceContainer = Couleurs.Surface,
    surfaceContainerHigh = Couleurs.Surface,
    surfaceContainerHighest = Couleurs.Surface,
)

private val SelectionNocturne = TextSelectionColors(
    handleColor = Couleurs.Accent,
    backgroundColor = Couleurs.Accent.copy(alpha = 0.30f),
)

/** Thème IronTap : Nocturne, sombre uniquement (pas de variante claire ni de couleurs dynamiques). */
@Composable
fun IronTapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SchemaNocturne,
        typography = TypographieMaterial,
        shapes = FormesMaterial,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Couleurs.Texte,
            LocalTextStyle provides Typo.Corps,
            LocalTextSelectionColors provides SelectionNocturne,
            content = content,
        )
    }
}
