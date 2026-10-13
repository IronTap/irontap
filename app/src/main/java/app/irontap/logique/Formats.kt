package app.irontap.logique

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chrono en `MM:SS` ; au-delà d'une heure, les minutes continuent (`75:12`, cf. DECISIONS.md). */
fun formaterChrono(ecouleMs: Long): String {
    val secondes = ecouleMs.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(Locale.ROOT, secondes / 60, secondes % 60)
}

private val FormatDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH)

/** « Lun. 12 oct. 2026 ». */
fun formaterDate(date: LocalDate): String =
    FormatDate.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }
