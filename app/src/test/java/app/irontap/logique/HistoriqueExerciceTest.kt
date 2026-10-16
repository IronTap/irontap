package app.irontap.logique

import app.irontap.donnees.SerieHistorique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoriqueExerciceTest {

    private val douze = LocalDate.of(2026, 10, 12)
    private val vingtQuatre = LocalDate.of(2026, 9, 24)

    private var derniereSerie = 0L

    /** Un palier ; sans [serieId], c'est le seul palier d'une nouvelle série. */
    private fun serie(
        entrainement: Long,
        date: LocalDate,
        libelle: String?,
        poids: Double?,
        reps: Int,
        nom: String = "Tirage vertical",
        serieId: Long = ++derniereSerie,
    ) = SerieHistorique(entrainement, date, libelle, nom, serieId, poids, reps)

    private fun lignes(vararg principaux: String) = principaux.map { LigneHistorique(it) }

    @Test
    fun `une section par seance, dans l'ordre recu, comme la maquette 3g`() {
        val historique = historiqueExercice(
            listOf(
                serie(3, douze, "Dos / biceps", 40.0, 15),
                serie(3, douze, "Dos / biceps", 45.0, 12),
                serie(3, douze, "Dos / biceps", 45.0, 10),
                serie(1, vingtQuatre, null, 37.5, 12, nom = "tirage vertical"),
                serie(1, vingtQuatre, null, 40.0, 10, nom = "tirage vertical"),
            ),
        )
        assertEquals(
            HistoriqueExercice(
                "Tirage vertical",
                listOf(
                    SeanceHistorique("Dos / biceps — 12/10/2026", lignes("15 × 40 kg", "12 × 45 kg", "10 × 45 kg")),
                    SeanceHistorique("24/09/2026", lignes("12 × 37,5 kg", "10 × 40 kg")),
                ),
            ),
            historique,
        )
    }

    @Test
    fun `poids du corps, et rien sans serie`() {
        assertEquals(lignes("12 × PDC", "8 × 41,25 kg"), historiqueExercice(listOf(serie(1, douze, null, null, 12), serie(1, douze, null, 41.25, 8)))?.seances?.single()?.series)
        assertNull(historiqueExercice(emptyList()))
    }

    @Test
    fun `degressive, paliers empiles sous le palier principal`() {
        val historique = historiqueExercice(
            listOf(
                serie(1, douze, null, 40.0, 15),
                serie(1, douze, null, 45.0, 10, serieId = 7),
                serie(1, douze, null, 30.0, 8, serieId = 7),
                serie(1, douze, null, 20.0, 6, serieId = 7),
            ),
        )
        assertEquals(
            listOf(LigneHistorique("15 × 40 kg"), LigneHistorique("10 × 45 kg", listOf("8 × 30 kg", "6 × 20 kg"))),
            historique?.seances?.single()?.series,
        )
    }

    @Test
    fun `cle de nom sans casse, accents ni espaces de bord`() {
        assertEquals("developpe couche", normaliserNom("  Développé COUCHÉ "))
        assertEquals(normaliserNom("Tirage vertical"), normaliserNom("tirage VERTICAL"))
        assertEquals("", normaliserNom("   "))
    }

    @Test
    fun `date numerique`() {
        assertEquals("05/10/2026", formaterDateNumerique(LocalDate.of(2026, 10, 5)))
    }
}
