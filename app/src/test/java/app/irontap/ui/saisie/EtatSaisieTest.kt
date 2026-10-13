package app.irontap.ui.saisie

import app.irontap.donnees.Entrainement
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Serie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Numérotation « Exercice N · Série M » et verrouillage du nom (spec §4.3.2 et §4.3.5). */
class EtatSaisieTest {

    private val t0 = Instant.EPOCH
    private val entrainement = Entrainement(1, LocalDate.of(2026, 10, 12), null, t0)

    private fun exercice(ordre: Int, series: Int) = ExerciceAvecSeries(
        Exercice(ordre.toLong(), 1, "Exercice $ordre", ordre),
        List(series) { Serie(it.toLong(), ordre.toLong(), it + 1, null, 10, t0) },
    )

    private fun etat(vararg exercices: ExerciceAvecSeries, nouvelExercice: Boolean = false) =
        EtatSaisie.EnCours(entrainement.copy(nouvelExercice = nouvelExercice), exercices.toList(), chrono = null)

    @Test
    fun `premier exercice, nom editable, exercice 1 serie 1`() {
        val e = etat()
        assertEquals(false, e.verrouille)
        assertNull(e.nomVerrouille)
        assertEquals(1 to 1, e.numeroExercice to e.numeroSerie)
    }

    @Test
    fun `apres nouvelle serie, nom verrouille, serie suivante`() {
        val e = etat(exercice(1, 3), exercice(2, 2))
        assertEquals("Exercice 2", e.nomVerrouille)
        assertEquals(2 to 3, e.numeroExercice to e.numeroSerie)
    }

    @Test
    fun `apres nouvel exercice, nom editable, exercice suivant serie 1`() {
        val e = etat(exercice(1, 3), nouvelExercice = true)
        assertEquals(false, e.verrouille)
        assertEquals(2 to 1, e.numeroExercice to e.numeroSerie)
    }
}
