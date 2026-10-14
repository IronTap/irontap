package app.irontap.logique

import app.irontap.donnees.EntrainementComplet
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
 * Export lisible (spec §5) : par entraînement, « date — libellé », puis chaque exercice et ses séries
 * en retrait. Une ligne vide entre les exercices, deux entre les entraînements.
 */
fun exporterTexte(entrainements: List<EntrainementComplet>): String = entrainements.joinToString("\n\n") { complet ->
    val entrainement = complet.entrainement
    val blocs = listOf(listOfNotNull(formaterDate(entrainement.date), entrainement.libelle).joinToString(" — ")) +
        complet.exercices.map { exercice ->
            (listOf(exercice.exercice.nom) + exercice.series.map { "  " + decrireSerie(it.poidsKg, it.repetitions) }).joinToString("\n")
        }
    blocs.joinToString("\n\n", postfix = "\n")
}

private val EnteteCsv = listOf("date", "libelle_entrainement", "exercice", "ordre_exercice", "ordre_serie", "poids_kg", "repetitions")

/**
 * Export tableur (spec §5) : une ligne par série, séparateur `;`, décimales avec point, poids vide au poids du corps.
 * Lignes terminées par CRLF (RFC 4180).
 */
fun exporterCsv(entrainements: List<EntrainementComplet>): String = buildString {
    append(EnteteCsv.joinToString(";")).append("\r\n")
    for (complet in entrainements) {
        val entrainement = complet.entrainement
        for (exercice in complet.exercices) {
            for (serie in exercice.series) {
                val champs = listOf(
                    entrainement.date.toString(),
                    champTexteCsv(entrainement.libelle.orEmpty()),
                    champTexteCsv(exercice.exercice.nom),
                    exercice.exercice.ordre.toString(),
                    serie.ordre.toString(),
                    serie.poidsKg?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
                    serie.repetitions.toString(),
                )
                append(champs.joinToString(";")).append("\r\n")
            }
        }
    }
}

/** Caractères qui font interpréter une cellule comme une formule par les tableurs. */
private val DebutsFormule = setOf('=', '+', '-', '@', '\t', '\r')

/**
 * Champ libre (libellé, nom d'exercice) : neutralisé s'il ressemble à une formule (apostrophe en tête,
 * recommandation OWASP), puis entre guillemets s'il contient `;`, `"` ou un saut de ligne (RFC 4180).
 */
internal fun champTexteCsv(valeur: String): String {
    val neutre = if (valeur.firstOrNull() in DebutsFormule) "'$valeur" else valeur
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
