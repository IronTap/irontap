package app.irontap.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Rayons Nocturne : 4 / 8 / 14 dp, 24 dp pour les bottom sheets et le sélecteur de date. */
object Formes {
    /** 4 dp. */
    val Petite = RoundedCornerShape(4.dp)

    /** 8 dp : boutons de dialogue, toast. */
    val Moyenne = RoundedCornerShape(8.dp)

    /** 14 dp : champs, cartes, grands boutons, dialogues, menus. */
    val Grande = RoundedCornerShape(14.dp)

    /** 24 dp : sélecteur de date. */
    val TresGrande = RoundedCornerShape(24.dp)

    /** 24 dp en haut seulement : bottom sheets. */
    val Feuille = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/**
 * Formes Material 3 alignées sur Nocturne : menus et champs (extraSmall) en 14 dp,
 * sheets et sélecteur de date (extraLarge) en 24 dp.
 */
internal val FormesMaterial = Shapes(
    extraSmall = Formes.Grande,
    small = Formes.Moyenne,
    medium = Formes.Grande,
    large = Formes.Grande,
    extraLarge = Formes.TresGrande,
)
