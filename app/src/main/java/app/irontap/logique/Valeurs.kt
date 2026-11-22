package app.irontap.logique

import app.irontap.donnees.Rir
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
 * RIR tapé au clavier (feuilles de modification) ou lu dans un CSV : un entier positif ou nul (« 2 »), ou deux valeurs
 * voisines (« 2-3 », dans un sens ou dans l'autre ; séparées aussi par une espace, une virgule, un point, une barre
 * oblique ou « à »). Une valeur au-delà de [RIR_MAX] est ramenée à 5+ : « 7 » et « 5-7 » donnent 5+, « 4-9 » 4-5+.
 * « 5+ » se relit. `null` : vide, ou illisible (cf. [rirLisible]).
 */
fun lireRir(texte: String): Rir? = (lectureRir(texte) as? LectureRir.Lu)?.rir

/**
 * Faux si le RIR tapé ne se lit pas : autre chose que des nombres, deux valeurs qui ne se suivent pas (« 1-3 »), plus
 * de deux, ou un séparateur sans nombre d'un côté (« 2- », « -1 »). Vide : lisible (pas de RIR).
 */
fun rirLisible(texte: String): Boolean = lectureRir(texte) != LectureRir.Illisible

private sealed interface LectureRir {
    data object Vide : LectureRir

    data object Illisible : LectureRir

    data class Lu(val rir: Rir) : LectureRir
}

private val SeparateurRir = Regex("""\s*(?:[-–—/,.]|à)\s*|\s+""")

private fun lectureRir(texte: String): LectureRir {
    if (texte.isBlank()) return LectureRir.Vide
    val valeurs = texte.trim().split(SeparateurRir).map { morceau ->
        morceau.removeSuffix("+").toIntOrNull()?.takeIf { it >= 0 }?.coerceAtMost(RIR_MAX) ?: return LectureRir.Illisible
    }.sorted()
    val rir = when (valeurs.size) {
        1 -> Rir(valeurs[0])
        2 -> Rir(valeurs[0], valeurs[1])
        else -> null
    }
    return rir?.takeIf { it.valide }?.let { LectureRir.Lu(it) } ?: LectureRir.Illisible
}

/** « 2 », ou « 5+ » pour [RIR_MAX] (pastille, borne d'une plage). */
fun formaterRir(valeur: Int): String = if (valeur >= RIR_MAX) "$RIR_MAX+" else valeur.toString()

/** « 2 », « 2-3 », « 4-5+ ». */
fun formaterRir(rir: Rir): String = if (rir.plage) "${formaterRir(rir.bas)}-${formaterRir(rir.haut)}" else formaterRir(rir.bas)

/** « RIR 2 », « RIR 2-3 », « RIR 5+ ». */
fun mentionRir(rir: Rir): String = "RIR ${formaterRir(rir)}"

/** RIR tel qu'il se tape dans un champ des feuilles de modification : « 2 », « 2-3 », « 5 » (affiché « 5+ »). */
fun texteRir(rir: Rir): String = if (rir.plage) "${rir.bas}-${rir.haut}" else rir.bas.toString()

/**
 * RIR après un appui sur la pastille [valeur] (DECISIONS.md) : sans RIR, elle est choisie. Une valeur seule : l'appui sur
 * elle l'efface, sur une voisine forme la plage des deux (« 2-3 »), ailleurs la remplace. Une plage : l'appui sur l'une de
 * ses deux pastilles garde l'autre seule, ailleurs la remplace.
 */
fun rirApresAppui(rir: Rir?, valeur: Int): Rir? = when {
    rir == null -> Rir(valeur)
    rir.plage && valeur == rir.bas -> Rir(rir.haut)
    rir.plage && valeur == rir.haut -> Rir(rir.bas)
    rir.plage -> Rir(valeur)
    valeur == rir.bas -> null
    valeur == rir.bas + 1 -> Rir(rir.bas, valeur)
    valeur == rir.bas - 1 -> Rir(valeur, rir.bas)
    else -> Rir(valeur)
}

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

    /** Index des lignes dont les répétitions manquent ou sont illisibles, et de celles dont le RIR ne se lit pas ([rirs]). */
    data class Erreurs(val lignes: Set<Int>, val rirs: Set<Int> = emptySet()) : LecturePaliers
}

/** Textes d'un palier de la feuille d'édition, tels que saisis. */
data class TextesPalier(val poids: String, val repetitions: String, val rir: String = "")

/**
 * Paliers saisis, ligne par ligne : les lignes sans poids ni répétitions sont ignorées (un RIR seul ne compte pas,
 * comme en saisie), les autres demandent des répétitions (le poids et le RIR restent facultatifs, spec §4.3.5) et un
 * RIR lisible s'il est tapé ([rirLisible]). Sans aucune ligne remplie, c'est la première qui est signalée.
 */
fun lirePaliers(lignes: List<TextesPalier>): LecturePaliers {
    val remplies = lignes.withIndex().filter { (_, ligne) -> ligne.poids.isNotBlank() || ligne.repetitions.isNotBlank() }
    if (remplies.isEmpty()) return LecturePaliers.Erreurs(setOf(0))
    val erreurs = remplies.filter { lireRepetitions(it.value.repetitions) == null }.map { it.index }.toSet()
    val rirs = remplies.filter { !rirLisible(it.value.rir) }.map { it.index }.toSet()
    if (erreurs.isNotEmpty() || rirs.isNotEmpty()) return LecturePaliers.Erreurs(erreurs, rirs)
    return LecturePaliers.Valide(
        remplies.map { (_, ligne) -> ValeursPalier(lirePoids(ligne.poids), lireRepetitions(ligne.repetitions)!!, lireRir(ligne.rir)) },
    )
}

private fun nombre(texte: String): Double? =
    texte.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
