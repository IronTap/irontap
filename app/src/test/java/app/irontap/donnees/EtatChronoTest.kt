package app.irontap.donnees

import app.irontap.donnees.EtatChrono.Etat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EtatChronoTest {

    private val arrete = EtatChrono(entrainementId = 1)

    @Test
    fun `arrete, le chrono vaut 0`() {
        assertEquals(0, arrete.ecouleMs(realtime = 50_000))
    }

    @Test
    fun `en cours, le temps ecoule se calcule depuis le demarrage`() {
        val enCours = arrete.demarre(realtime = 10_000)
        assertEquals(Etat.EnCours, enCours.etat)
        assertEquals(72_000, enCours.ecouleMs(realtime = 82_000))
    }

    @Test
    fun `la pause fige la valeur et la reprise repart de celle-ci`() {
        val enPause = arrete.demarre(10_000).enPause(25_000)
        assertEquals(EtatChrono(1, Etat.Pause, demarreA = null, cumulMs = 15_000), enPause)
        assertEquals(15_000, enPause.ecouleMs(realtime = 999_000))

        val repris = enPause.demarre(100_000)
        assertEquals(20_000, repris.ecouleMs(realtime = 105_000))
    }

    @Test
    fun `play sur un chrono en cours et pause sur un chrono arrete sont sans effet`() {
        val enCours = arrete.demarre(10_000)
        assertSame(enCours, enCours.demarre(20_000))
        assertSame(arrete, arrete.enPause(20_000))
    }

    @Test
    fun `relancer repart de 0 et demarre quel que soit l'etat`() {
        val attendu = EtatChrono(1, Etat.EnCours, demarreA = 70_000, cumulMs = 0)
        assertEquals(attendu, arrete.relance(70_000))
        assertEquals(attendu, arrete.demarre(10_000).relance(70_000))
        assertEquals(attendu, arrete.demarre(10_000).enPause(30_000).relance(70_000))
    }

    @Test
    fun `apres un redemarrage du telephone, la part en cours est bornee a 0`() {
        val enCours = EtatChrono(1, Etat.EnCours, demarreA = 5_000_000, cumulMs = 3_000)
        assertEquals(3_000, enCours.ecouleMs(realtime = 1_000))
    }
}
