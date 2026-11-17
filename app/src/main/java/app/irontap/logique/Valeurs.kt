package app.irontap.logique

import app.irontap.donnees.ValeursPalier
import java.math.BigDecimal
import kotlin.math.roundToLong

/** Poids maximal accepté : garde les additions +X dans la longueur du champ. */
const val POIDS_MAX_KG = 9999.75

/** Répétitions maximales acceptées (3 chiffres). */
const val REPETITIONS_MAX = 999

/** RIR le plus haut enregistré : il se lit « 5+ », et toute valeur au-delà s'enregistre ainsi (DECISIONS.md). */
const val RIR_MAX = 5

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

/**
 * RIR saisi au clavier : un entier positif ou nul, ramené à [RIR_MAX] au-delà (« 7 », « 12 » : 5+). « 5+ » se relit
 * aussi. Vide ou illisible : `null`, pas de RIR.
 */
fun lireRir(texte: String): Int? = texte.trim().removeSuffix("+").toIntOrNull()?.takeIf { it >= 0 }?.coerceAtMost(RIR_MAX)

/** « 2 », ou « 5+ » pour [RIR_MAX]. */
fun formaterRir(rir: Int): String = if (rir >= RIR_MAX) "$RIR_MAX+" else rir.toString()

/** « RIR 2 », « RIR 5+ ». */
fun mentionRir(rir: Int): String = "RIR ${formaterRir(rir)}"

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
fun decrirePalier(poidsKg: Double?, repetitions: Int): String =
    (if (poidsKg != null) "${formaterPoids(poidsKg)} kg" else "PDC") + " × $repetitions"

/**
 * « 40 kg × 15 (RIR 2) » ; une dégressive : « 50 kg × 10 (RIR 2) ↓ 30 kg × 8 (RIR 0) ↓ 20 kg × 6 » (toast, dialogues,
 * export texte). Le RIR suit chaque palier qui en a un ; sans [avecRir], il est omis (rappel des paliers gardés).
 */
fun decrireSerie(paliers: List<ValeursPalier>, avecRir: Boolean = true): String =
    paliers.joinToString(" ↓ ") { palier ->
        decrirePalier(palier.poidsKg, palier.repetitions) + if (avecRir && palier.rir != null) " (${mentionRir(palier.rir)})" else ""
    }

/** « 15 × 40 kg », ou « 12 × PDC » au poids du corps : répétitions en tête (historique de l'exercice, 3g). */
fun decrirePalierHistorique(poidsKg: Double?, repetitions: Int): String =
    "$repetitions × " + if (poidsKg != null) "${formaterPoids(poidsKg)} kg" else "PDC"

/** Lecture des paliers de la feuille d'édition (spec §4.5.1). */
sealed interface LecturePaliers {
    data class Valide(val paliers: List<ValeursPalier>) : LecturePaliers

    /** Index des lignes dont les répétitions manquent ou sont illisibles. */
    data class Erreurs(val lignes: Set<Int>) : LecturePaliers
}

/** Textes d'un palier de la feuille d'édition, tels que saisis. */
data class TextesPalier(val poids: String, val repetitions: String, val rir: String = "")

/**
 * Paliers saisis, ligne par ligne : les lignes sans poids ni répétitions sont ignorées (un RIR seul ne compte pas,
 * comme en saisie), les autres demandent des répétitions (le poids et le RIR restent facultatifs, spec §4.3.5).
 * Sans aucune ligne remplie, c'est la première qui est signalée.
 */
fun lirePaliers(lignes: List<TextesPalier>): LecturePaliers {
    val remplies = lignes.withIndex().filter { (_, ligne) -> ligne.poids.isNotBlank() || ligne.repetitions.isNotBlank() }
    if (remplies.isEmpty()) return LecturePaliers.Erreurs(setOf(0))
    val erreurs = remplies.filter { lireRepetitions(it.value.repetitions) == null }.map { it.index }.toSet()
    if (erreurs.isNotEmpty()) return LecturePaliers.Erreurs(erreurs)
    return LecturePaliers.Valide(
        remplies.map { (_, ligne) -> ValeursPalier(lirePoids(ligne.poids), lireRepetitions(ligne.repetitions)!!, lireRir(ligne.rir)) },
    )
}

private fun nombre(texte: String): Double? =
    texte.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
