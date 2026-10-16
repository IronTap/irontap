package app.irontap.logique

import app.irontap.donnees.SerieHistorique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoriqueExerciceTest {

    private val douze = LocalDate.of(2026, 10, 12)
    private val vingtQuatre = LocalDate.of(2026, 9, 24)

    private fun serie(entrainement: Long, date: LocalDate, libelle: String?, poids: Double?, reps: Int, nom: String = "Tirage vertical") =
        SerieHistorique(entrainement, date, libelle, nom, poids, reps)

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
                    SeanceHistorique("Dos / biceps — 12/10/2026", listOf("15 × 40 kg", "12 × 45 kg", "10 × 45 kg")),
                    SeanceHistorique("24/09/2026", listOf("12 × 37,5 kg", "10 × 40 kg")),
                ),
            ),
            historique,
        )
    }

    @Test
    fun `poids du corps, et rien sans serie`() {
        assertEquals(listOf("12 × PDC", "8 × 41,25 kg"), historiqueExercice(listOf(serie(1, douze, null, null, 12), serie(1, douze, null, 41.25, 8)))?.seances?.single()?.series)
        assertNull(historiqueExercice(emptyList()))
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
