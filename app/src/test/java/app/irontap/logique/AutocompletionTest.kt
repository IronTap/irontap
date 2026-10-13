package app.irontap.logique

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocompletionTest {

    private val historique = listOf(
        "Tirage vertical", "Développé couché", "Tirage horizontal", "tirage vertical",
        "Curl haltères", "Squat", "Tirage poulie basse", "Rowing barre",
    )

    private fun noms(saisie: String) = suggerer(saisie, historique).map { it.nom }

    @Test
    fun `n'importe quelle partie du nom, sans casse ni accents, dans l'ordre d'utilisation`() {
        assertEquals(listOf("Tirage vertical", "Tirage horizontal", "Tirage poulie basse"), noms("tir"))
        assertEquals(listOf("Développé couché"), noms("DEVEL"))
        assertEquals(listOf("Curl haltères"), noms("alter"))
        assertEquals(listOf("Rowing barre"), noms("  barre "))
    }

    @Test
    fun `sans doublons, sans le nom deja saisi, 4 au plus`() {
        // « tirage vertical » est un doublon de « Tirage vertical », plus récent.
        assertEquals(listOf("Tirage vertical", "Tirage horizontal", "Tirage poulie basse"), noms("tirage"))
        assertTrue(noms("Tirage Vertical").isEmpty())
        assertTrue(noms("Squat").isEmpty())
        assertEquals(4, suggerer("a", historique).size)
        assertTrue(noms("").isEmpty())
    }

    @Test
    fun `la partie correspondante est reperee dans le nom d'origine, accents compris`() {
        assertEquals(Suggestion("Développé couché", 3, 7), suggerer("elop", historique).single())
        assertEquals(Suggestion("Curl haltères", 7, 13), suggerer("lteres", historique).single())
    }
}
