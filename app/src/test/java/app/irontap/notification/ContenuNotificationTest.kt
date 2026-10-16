package app.irontap.notification

import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementEnCours
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.EtatChrono.Etat
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.serieDeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Contenu de la notification du chrono (spec §4.3.4, maquettes 5a, 5b). */
class ContenuNotificationTest {

    private val t0 = Instant.EPOCH
    private val entrainement = Entrainement(1, LocalDate.of(2026, 10, 12), null, t0)
    private val enMarche = EtatChrono(1, Etat.EnCours, demarreA = 0)

    private val tirage = ExerciceAvecSeries(
        Exercice(1, 1, "Tirage vertical", 1),
        List(2) { serieDeTest(it.toLong(), 1, it + 1, 40.0, 12, t0) },
    )

    private fun enCours(chrono: EtatChrono?, nouvelExercice: Boolean = false, vararg exercices: ExerciceAvecSeries) =
        EntrainementEnCours(entrainement.copy(nouvelExercice = nouvelExercice), exercices.toList(), chrono)

    @Test
    fun `pas de notification sans entrainement ni chrono lance`() {
        assertNull(ContenuNotification.de(null))
        assertNull(ContenuNotification.de(enCours(chrono = null)))
        assertNull(ContenuNotification.de(enCours(EtatChrono(1))))
    }

    @Test
    fun `exercice courant et serie en cours de saisie`() {
        assertEquals(
            ContenuNotification(enMarche, "Tirage vertical · Série 3"),
            ContenuNotification.de(enCours(enMarche, false, tirage)),
        )
        val enPause = EtatChrono(1, Etat.Pause, cumulMs = 5_000)
        assertEquals("Tirage vertical · Série 3", ContenuNotification.de(enCours(enPause, false, tirage))?.ligne)
    }

    @Test
    fun `apres nouvel exercice, le nom n'est pas encore connu`() {
        assertEquals("Exercice 2 · Série 1", ContenuNotification.de(enCours(enMarche, true, tirage))?.ligne)
    }

    @Test
    fun `seules les trois actions de la notification sont acceptees`() {
        assertEquals(ActionChrono.Pause, ActionChrono.depuis("app.irontap.action.CHRONO_PAUSE"))
        assertNull(ActionChrono.depuis("android.intent.action.BOOT_COMPLETED"))
        assertNull(ActionChrono.depuis(null))
    }
}
