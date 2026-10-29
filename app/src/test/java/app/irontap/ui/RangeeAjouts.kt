package app.irontap.ui

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Rangée des boutons +X du poids (la première affichée) : chaque bouton [libelles] fait 58 dp et en laisse au moins
 * 8 avant le suivant (cibles distinctes), sans dépasser [largeur].
 */
fun SemanticsNodeInteractionsProvider.verifierAjoutsPoids(libelles: List<String>, largeur: Float) {
    val bornes = libelles.map { onAllNodes(hasText(it) and hasClickAction())[0].getBoundsInRoot() }
    bornes.forEach { assertEquals(58f, (it.right - it.left).value, 0.5f) }
    bornes.zipWithNext { a, b -> assertTrue("écart ${b.left - a.right}", b.left - a.right >= 8.dp) }
    assertTrue(bornes.last().right.value <= largeur)
}
