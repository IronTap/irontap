package app.irontap.logique

import java.math.BigDecimal
import kotlin.math.roundToLong

/** Poids maximal accepté : garde les additions +X dans la longueur du champ. */
const val POIDS_MAX_KG = 9999.75

/** Répétitions maximales acceptées (3 chiffres). */
const val REPETITIONS_MAX = 999

/** Arrondi au 0,25 kg le plus proche (petits disques de 1,25 kg, spec §4.3.3). */
fun arrondirPoids(kg: Double): Double = (kg * 4).roundToLong() / 4.0

/**
 * Poids saisi (« 41,3 », « 40.5 »), arrondi au 0,25 kg. Vide, illisible ou nul : `null`, c'est-à-dire
 * poids du corps. Le point et la virgule sont acceptés.
 */
fun lirePoids(texte: String): Double? =
    nombre(texte)?.let(::arrondirPoids)?.takeIf { it > 0 }?.coerceAtMost(POIDS_MAX_KG)

/** Nombre de répétitions saisi, `null` s'il est vide, illisible ou nul. */
fun lireRepetitions(texte: String): Int? = texte.trim().toIntOrNull()?.takeIf { it in 1..REPETITIONS_MAX }

/** Bouton +X du poids : ajoute à la valeur courante (vide = 0), arrondit au 0,25 et affiche avec virgule. */
fun ajouterPoids(texte: String, ajout: Int): String =
    formaterPoids(arrondirPoids((nombre(texte) ?: 0.0) + ajout).coerceIn(0.0, POIDS_MAX_KG))

/** Bouton +X des répétitions : ajoute à la valeur courante (vide = 0). */
fun ajouterRepetitions(texte: String, ajout: Int): String =
    ((texte.trim().toIntOrNull() ?: 0) + ajout).coerceIn(0, REPETITIONS_MAX).toString()

/** « 41,25 », « 40 », « 12,5 » : virgule décimale, sans zéros inutiles. */
fun formaterPoids(kg: Double): String =
    BigDecimal.valueOf(kg).stripTrailingZeros().toPlainString().replace('.', ',')

/** « 40 kg × 15 », ou « PDC × 12 » au poids du corps. */
fun decrireSerie(poidsKg: Double?, repetitions: Int): String =
    (if (poidsKg != null) "${formaterPoids(poidsKg)} kg" else "PDC") + " × $repetitions"

private fun nombre(texte: String): Double? =
    texte.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
