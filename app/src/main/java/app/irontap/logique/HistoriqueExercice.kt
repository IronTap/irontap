package app.irontap.logique

import app.irontap.donnees.SerieHistorique

/** Popup « Historique de l'exercice » (maquette 3g, spec §4.3.8) : [nom] tel qu'enregistré, puis les séances. */
data class HistoriqueExercice(val nom: String, val seances: List<SeanceHistorique>)

/** Une séance passée : « Dos / biceps — 12/10/2026 » (ou la date seule), puis ses séries, « 15 × 40 kg ». */
data class SeanceHistorique(val entete: String, val series: List<String>)

/**
 * Regroupe les [series] par séance, dans l'ordre reçu (de la plus récente à la plus ancienne, puis exercices
 * et séries dans l'ordre) : un exercice fait deux fois dans une séance y voit ses séries fusionnées.
 * Le titre est le nom enregistré dans la séance la plus récente. `null` sans aucune série.
 */
fun historiqueExercice(series: List<SerieHistorique>): HistoriqueExercice? {
    val recente = series.firstOrNull() ?: return null
    val seances = series.groupBy { it.entrainementId }.values.map { seance ->
        val premiere = seance.first()
        SeanceHistorique(
            entete = listOfNotNull(premiere.libelle, formaterDateNumerique(premiere.date)).joinToString(" — "),
            series = seance.map { decrireSerieHistorique(it.poidsKg, it.repetitions) },
        )
    }
    return HistoriqueExercice(recente.nom, seances)
}
