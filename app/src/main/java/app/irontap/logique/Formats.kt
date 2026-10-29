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

private val FormatDateNumerique = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

private val FormatDateCourte = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/** « Lun. 12 oct. 2026 ». */
fun formaterDate(date: LocalDate): String =
    FormatDate.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** « Lun. 12 oct. », sans l'année (bandeau « en cours » de l'accueil). */
fun formaterDateCourte(date: LocalDate): String =
    FormatDateCourte.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** « 12/10/2026 » (historique de l'exercice, 3g). */
fun formaterDateNumerique(date: LocalDate): String = FormatDateNumerique.format(date)

private val FormatJourMois = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

private val FormatJourMoisAnnee = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/** « Le 12 oct. 2026 », « Du 12 sept. au 26 oct. 2026 », « Du 28 déc. 2025 au 3 janv. 2026 » (confirmation de l'import). */
fun formaterPeriode(debut: LocalDate, fin: LocalDate): String = when {
    debut == fin -> "Le ${FormatJourMoisAnnee.format(fin)}"
    debut.year == fin.year -> "Du ${FormatJourMois.format(debut)} au ${FormatJourMoisAnnee.format(fin)}"
    else -> "Du ${FormatJourMoisAnnee.format(debut)} au ${FormatJourMoisAnnee.format(fin)}"
}
