package app.irontap.logique

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValeursTest {

    @Test
    fun `le poids est arrondi au 0,25 kg`() {
        assertEquals(41.25, lirePoids("41,3")!!, 0.0)
        assertEquals(41.25, lirePoids("41.2")!!, 0.0)
        assertEquals(41.5, lirePoids("41,4")!!, 0.0)
        assertEquals(41.25, lirePoids("41,125")!!, 0.0)
        assertEquals(12.5, lirePoids(" 12,50 ")!!, 0.0)
    }

    @Test
    fun `poids vide, illisible ou nul = poids du corps`() {
        assertNull(lirePoids(""))
        assertNull(lirePoids(","))
        assertNull(lirePoids("0"))
        assertNull(lirePoids("0,1"))
        assertNull(lirePoids("NaN"))
        assertNull(lirePoids("Infinity"))
    }

    @Test
    fun `poids borne au maximum`() {
        assertEquals(POIDS_MAX_KG, lirePoids("99999")!!, 0.0)
    }

    @Test
    fun `les boutons +X ajoutent a la valeur courante`() {
        assertEquals("1", ajouterPoids("", 1))
        assertEquals("45", ajouterPoids("40", 5))
        assertEquals("51,25", ajouterPoids("41,25", 10))
        assertEquals("46,25", ajouterPoids("41,3", 5))
        assertEquals("20", ajouterPoids(",", 20))
        assertEquals("9999,75", ajouterPoids("9999", 20))
        assertEquals("12", ajouterRepetitions("10", 2))
        assertEquals("5", ajouterRepetitions("", 5))
        assertEquals("999", ajouterRepetitions("995", 10))
    }

    @Test
    fun `repetitions entieres et positives`() {
        assertEquals(12, lireRepetitions("12"))
        assertNull(lireRepetitions(""))
        assertNull(lireRepetitions("0"))
        assertNull(lireRepetitions("1000"))
    }

    @Test
    fun `affichage avec virgule, sans zeros inutiles`() {
        assertEquals("40", formaterPoids(40.0))
        assertEquals("12,5", formaterPoids(12.5))
        assertEquals("41,25", formaterPoids(41.25))
        assertEquals("0", formaterPoids(0.0))
        assertEquals("40 kg × 15", decrireSerie(40.0, 15))
        assertEquals("PDC × 12", decrireSerie(null, 12))
    }
}
