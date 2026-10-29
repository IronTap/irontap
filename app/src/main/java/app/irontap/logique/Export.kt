package app.irontap.logique

import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.RoleSuperset
import java.math.BigDecimal
import java.time.LocalDate

/** Formats d'export (spec §5). */
enum class FormatExport(val extension: String, val mime: String) {
    Texte("txt", "text/plain"),
    Csv("csv", "text/csv"),
    ;

    /** Contenu de l'export dans ce format. */
    fun generer(entrainements: List<EntrainementComplet>): String = when (this) {
        Texte -> exporterTexte(entrainements)
        Csv -> exporterCsv(entrainements)
    }

    /**
     * Contenu écrit dans le fichier : le CSV commence par une marque d'ordre d'octets UTF-8, sans quoi
     * Excel l'ouvre en Windows-1252 et abîme les accents. Le texte reste tel quel.
     */
    fun fichier(contenu: String): String = if (this == Csv) "\uFEFF$contenu" else contenu
}

/**
 * Supersets complets (A et B présents) de l'entraînement, numérotés 1, 2… dans l'ordre des exercices. Un exercice
 * resté seul de son superset n'en fait pas partie : il s'exporte comme un exercice ordinaire.
 */
internal fun numerosSupersets(exercices: List<ExerciceAvecSeries>): Map<String, Int> {
    val roles = exercices.filter { it.exercice.supersetId != null }.groupBy({ it.exercice.supersetId!! }, { it.exercice.supersetRole })
    return roles.filterValues { it.containsAll(RoleSuperset.entries) }.keys
        .sortedBy { id -> exercices.indexOfFirst { it.exercice.supersetId == id } }
        .withIndex().associate { (index, id) -> id to index + 1 }
}

/**
 * Export lisible (spec §5) : par entraînement, « date — libellé », puis chaque exercice et ses séries
 * en retrait, les paliers d'une dégressive sur la même ligne (« 45 kg × 10 ↓ 30 kg × 8 »).
 * Un superset (DECISIONS.md) : « Superset : A + B », puis une ligne par tour, « A 8 kg × 15 · B 12 kg × 12 »,
 * « — » pour une série absente, comme la carte du détail.
 * Une ligne vide entre les exercices, deux entre les entraînements.
 */
fun exporterTexte(entrainements: List<EntrainementComplet>): String = entrainements.joinToString("\n\n") { complet ->
    val entrainement = complet.entrainement
    val supersets = numerosSupersets(complet.exercices)
    val blocs = listOf(listOfNotNull(formaterDate(entrainement.date), entrainement.libelle).joinToString(" — ")) +
        complet.exercices.mapNotNull { exercice ->
            val supersetId = exercice.exercice.supersetId?.takeIf { it in supersets }
            when {
                supersetId == null -> (listOf(exercice.exercice.nom) + exercice.series.map { "  " + decrireSerie(it.valeurs) }).joinToString("\n")
                // A et B dans un même bloc, à la place de A.
                exercice.exercice.supersetRole == RoleSuperset.A -> texteSuperset(complet.exercices.filter { it.exercice.supersetId == supersetId })
                else -> null
            }
        }
    blocs.joinToString("\n\n", postfix = "\n")
}

private fun texteSuperset(membres: List<ExerciceAvecSeries>): String {
    val parRole = RoleSuperset.entries.associateWith { role -> membres.first { it.exercice.supersetRole == role } }
    val tours = membres.flatMap { it.series }.map { it.serie.ordre }.distinct().sorted()
    val lignes = tours.map { tour ->
        "  " + parRole.entries.joinToString(" · ") { (role, exercice) ->
            "$role " + (exercice.series.firstOrNull { it.serie.ordre == tour }?.let { decrireSerie(it.valeurs) } ?: "—")
        }
    }
    return (listOf("Superset : " + parRole.values.joinToString(" + ") { it.exercice.nom }) + lignes).joinToString("\n")
}

/**
 * Colonnes du CSV : celles de la spec §5, puis `entrainement` (numéro de l'entraînement dans le fichier : deux séances
 * du même jour au même libellé restent distinctes), `superset` (numéro du superset dans l'entraînement) et
 * `role_superset` (A ou B), vides hors superset (DECISIONS.md).
 */
internal val EnteteCsv = listOf(
    "date", "libelle_entrainement", "exercice", "ordre_exercice", "ordre_serie", "palier", "poids_kg", "repetitions",
    "entrainement", "superset", "role_superset",
)

/**
 * Export tableur (spec §5) : une ligne par palier (`palier` = 1 pour une série simple, 1, 2… pour une dégressive),
 * séparateur `;`, décimales avec point, poids vide au poids du corps. Lignes terminées par CRLF (RFC 4180).
 * Dans un superset, `ordre_serie` est le numéro du tour : il peut sauter (A : 1, 3 quand le tour 2 n'a que B).
 */
fun exporterCsv(entrainements: List<EntrainementComplet>): String = buildString {
    append(EnteteCsv.joinToString(";")).append("\r\n")
    for ((index, complet) in entrainements.withIndex()) {
        val entrainement = complet.entrainement
        val supersets = numerosSupersets(complet.exercices)
        for (exercice in complet.exercices) {
            val superset = exercice.exercice.supersetId?.let { supersets[it] }
            for ((serie, paliers) in exercice.series) {
                for (palier in paliers) {
                    val champs = listOf(
                        entrainement.date.toString(),
                        champTexteCsv(entrainement.libelle.orEmpty()),
                        champTexteCsv(exercice.exercice.nom),
                        exercice.exercice.ordre.toString(),
                        serie.ordre.toString(),
                        palier.ordre.toString(),
                        palier.poidsKg?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
                        palier.repetitions.toString(),
                        (index + 1).toString(),
                        superset?.toString().orEmpty(),
                        exercice.exercice.supersetRole?.takeIf { superset != null }?.name.orEmpty(),
                    )
                    append(champs.joinToString(";")).append("\r\n")
                }
            }
        }
    }
}

/** Caractères qui font interpréter une cellule comme une formule par les tableurs. */
internal val DebutsFormule = setOf('=', '+', '-', '@', '\t', '\r')

/**
 * Champ libre (libellé, nom d'exercice) : neutralisé s'il ressemble à une formule (apostrophe en tête,
 * recommandation OWASP), puis entre guillemets s'il contient `;`, `"` ou un saut de ligne (RFC 4180).
 * Un champ qui commence déjà par une apostrophe en reçoit une aussi, pour que l'import retire sans ambiguïté
 * l'apostrophe ajoutée (cf. `lireChampTexte`) : l'aller-retour est exact.
 */
internal fun champTexteCsv(valeur: String): String {
    val neutre = if (valeur.firstOrNull().let { it in DebutsFormule || it == '\'' }) "'$valeur" else valeur
    return if (neutre.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + neutre.replace("\"", "\"\"") + "\""
    } else {
        neutre
    }
}

/** Aperçu de la feuille d'export : les [lignes] premières lignes, sans lignes vides finales. */
fun apercuExport(contenu: String, lignes: Int = 5): String =
    contenu.lineSequence().take(lignes).joinToString("\n") { it.trimEnd('\r') }.trimEnd()

/**
 * Nom proposé pour le fichier : `irontap-2026-10-12.txt` pour un entraînement (sa date),
 * `irontap-historique-2026-10-14.csv` pour tout l'historique (date du jour).
 */
fun nomFichierExport(format: FormatExport, dateEntrainement: LocalDate?, aujourdhui: LocalDate): String =
    if (dateEntrainement != null) {
        "irontap-$dateEntrainement.${format.extension}"
    } else {
        "irontap-historique-$aujourdhui.${format.extension}"
    }
