package app.irontap.ui.composants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icône Phosphor (`R.drawable.ic_*`) à la taille et la couleur voulues. */
@Composable
fun Icone(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    taille: Dp = 22.dp,
    couleur: Color = LocalContentColor.current,
    description: String? = null,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = description,
        modifier = modifier.size(taille),
        tint = couleur,
    )
}
