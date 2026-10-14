package app.irontap.logique

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatsTest {

    @Test
    fun `chrono en MM SS, les minutes continuent au-dela d'une heure`() {
        assertEquals("00:00", formaterChrono(0))
        assertEquals("00:00", formaterChrono(999))
        assertEquals("01:12", formaterChrono(72_000))
        assertEquals("59:59", formaterChrono(3_599_999))
        assertEquals("75:12", formaterChrono(4_512_000))
        assertEquals("00:00", formaterChrono(-5_000))
    }

    @Test
    fun `date en francais`() {
        assertEquals("Lun. 12 oct. 2026", formaterDate(LocalDate.of(2026, 10, 12)))
        assertEquals("Mer. 30 sept. 2026", formaterDate(LocalDate.of(2026, 9, 30)))
        assertEquals("Mar. 13 oct.", formaterDateCourte(LocalDate.of(2026, 10, 13)))
    }
}
