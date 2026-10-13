package app.irontap.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Élévation Nocturne : sur fond sombre, un filet d'1 dp + une ombre ambiante
 * (équivalent des `--shadow-sm/md/lg` des maquettes).
 */
enum class Elevation(internal val filet: Shadow, internal val ombre: Shadow?) {
    /** Contour 1 dp neutre-800, sans ombre. */
    Sm(filet(Couleurs.Neutre800), null),

    /** Contour 1 dp neutre-700 + ombre 0/6/18 noir 55 % (menu d'autocomplétion, toast). */
    Md(filet(Couleurs.Neutre700), Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.55f), offset = DpOffset(0.dp, 6.dp))),

    /** Contour 1 dp neutre-500 + ombre 0/16/40 noir 65 % (dialogues, bottom sheets, menus). */
    Lg(filet(Couleurs.Neutre500), Shadow(radius = 40.dp, color = Color.Black.copy(alpha = 0.65f), offset = DpOffset(0.dp, 16.dp))),
}

/** Filet d'1 dp à l'extérieur de la forme, comme `box-shadow: 0 0 0 1px` (une bordure, elle, serait dedans). */
private fun filet(couleur: Color) = Shadow(radius = 0.dp, color = couleur, spread = 1.dp)

/** Dessine l'ombre ambiante puis le filet d'une [Elevation] sous l'élément. À placer avant son `background`. */
fun Modifier.elevation(niveau: Elevation, forme: Shape): Modifier =
    (niveau.ombre?.let { dropShadow(forme, it) } ?: this)
        .dropShadow(forme, niveau.filet)
