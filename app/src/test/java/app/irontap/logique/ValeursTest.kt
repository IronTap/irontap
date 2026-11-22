package app.irontap.logique

import app.irontap.donnees.Rir
import app.irontap.donnees.ValeursPalier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals("40 kg × 15", decrirePalier(40.0, 15))
        assertEquals("PDC × 12", decrirePalier(null, 12))
        assertEquals("40 kg × 15", decrireSerie(listOf(ValeursPalier(40.0, 15))))
        assertEquals("50 kg × 10 ↓ 30 kg × 8 ↓ PDC × 6", decrireSerie(listOf(ValeursPalier(50.0, 10), ValeursPalier(30.0, 8), ValeursPalier(null, 6))))
    }

    @Test
    fun `RIR facultatif, au-dela de 5 enregistre 5+`() {
        assertEquals(null, lireRir(""))
        assertEquals(null, lireRir("  "))
        assertEquals(null, lireRir("a"))
        assertEquals(null, lireRir("-1"))
        assertEquals(Rir(0), lireRir("0"))
        assertEquals(Rir(3), lireRir(" 3 "))
        assertEquals(Rir(5), lireRir("5"))
        assertEquals(Rir(5), lireRir("5+"))
        assertEquals(Rir(5), lireRir("7"))
        assertEquals(Rir(5), lireRir("12"))
        assertEquals("4", formaterRir(4))
        assertEquals("5+", formaterRir(5))
        assertEquals("RIR 0", mentionRir(Rir(0)))
        assertEquals("RIR 5+", mentionRir(Rir(5)))
    }

    @Test
    fun `plage de RIR, deux valeurs voisines dans un sens ou dans l'autre`() {
        for (texte in listOf("2-3", "3-2", "2 3", "2,3", "2.3", "2/3", "2 - 3", "2–3", "2 à 3")) assertEquals(texte, Rir(2, 3), lireRir(texte))
        assertEquals(Rir(4, 5), lireRir("4-5+"))
        assertEquals(Rir(4, 5), lireRir("4-9"))
        // Au-delà de 5, les deux bornes se rejoignent : 5+.
        assertEquals(Rir(5), lireRir("5-7"))
        assertEquals(Rir(2), lireRir("2-2"))
        for (texte in listOf("1-3", "0-5", "1-2-3", "2-", "-3", "2--3", "2-a")) {
            assertNull(texte, lireRir(texte))
            assertFalse(texte, rirLisible(texte))
        }
        assertTrue(rirLisible(""))
        assertTrue(rirLisible("2-3"))
        assertEquals("2-3", formaterRir(Rir(2, 3)))
        assertEquals("4-5+", formaterRir(Rir(4, 5)))
        assertEquals("RIR 0-1", mentionRir(Rir(0, 1)))
        assertEquals("2-3", texteRir(Rir(2, 3)))
        assertEquals("5", texteRir(Rir(5)))
        assertEquals("4-5", texteRir(Rir(4, 5)))
    }

    @Test
    fun `appui sur une pastille, choix, plage de deux voisines, effacement`() {
        assertEquals(Rir(2), rirApresAppui(null, 2))
        // Valeur seule : elle-même l'efface, une voisine forme la plage, une autre la remplace.
        assertEquals(null, rirApresAppui(Rir(2), 2))
        assertEquals(Rir(2, 3), rirApresAppui(Rir(2), 3))
        assertEquals(Rir(1, 2), rirApresAppui(Rir(2), 1))
        assertEquals(Rir(4), rirApresAppui(Rir(2), 4))
        assertEquals(Rir(0), rirApresAppui(Rir(2), 0))
        assertEquals(Rir(4, 5), rirApresAppui(Rir(4), 5))
        assertEquals(Rir(0, 1), rirApresAppui(Rir(1), 0))
        // Plage : l'une de ses pastilles garde l'autre, une autre la remplace.
        assertEquals(Rir(3), rirApresAppui(Rir(2, 3), 2))
        assertEquals(Rir(2), rirApresAppui(Rir(2, 3), 3))
        assertEquals(Rir(4), rirApresAppui(Rir(2, 3), 4))
        assertEquals(Rir(1), rirApresAppui(Rir(2, 3), 1))
        assertEquals(Rir(5), rirApresAppui(Rir(4, 5), 4))
    }

    @Test
    fun `RIR apres chaque palier qui en a un, sauf dans le rappel des paliers gardes`() {
        val paliers = listOf(ValeursPalier(50.0, 10, Rir(2)), ValeursPalier(30.0, 8), ValeursPalier(null, 6, Rir(4, 5)))
        assertEquals("50 kg × 10 (RIR 2) ↓ 30 kg × 8 ↓ PDC × 6 (RIR 4-5+)", decrireSerie(paliers))
        assertEquals("50 kg × 10 ↓ 30 kg × 8 ↓ PDC × 6", decrireSerie(paliers, avecRir = false))
    }

    @Test
    fun `paliers de la feuille d'edition, RIR facultatif, un RIR seul ne compte pas`() {
        assertEquals(
            LecturePaliers.Valide(listOf(ValeursPalier(45.0, 10, Rir(2)), ValeursPalier(30.0, 8), ValeursPalier(null, 6, Rir(5)), ValeursPalier(20.0, 6, Rir(2, 3)))),
            lirePaliers(
                listOf(
                    TextesPalier("45", "10", "2"), TextesPalier("30", "8"), TextesPalier("", "6", "12"), TextesPalier("", "", "1"),
                    TextesPalier("20", "6", "3-2"),
                ),
            ),
        )
        assertEquals(LecturePaliers.Erreurs(setOf(0)), lirePaliers(listOf(TextesPalier("", "", "3"))))
        assertEquals(LecturePaliers.Erreurs(setOf(1)), lirePaliers(listOf(TextesPalier("45", "10"), TextesPalier("30", "", "0"))))
        // RIR illisible : signalé sur sa ligne ; ignoré sur une ligne sans poids ni répétitions.
        assertEquals(
            LecturePaliers.Erreurs(setOf(2), rirs = setOf(0)),
            lirePaliers(listOf(TextesPalier("45", "10", "1-3"), TextesPalier("", "", "9-1"), TextesPalier("30", "", "2"))),
        )
    }
}
