package app.irontap.logique

import app.irontap.donnees.SerieHistorique

/** Popup « Historique de l'exercice » (maquette 3g, spec §4.3.8) : [nom] tel qu'enregistré, puis les séances. */
data class HistoriqueExercice(val nom: String, val seances: List<SeanceHistorique>)

/** Une séance passée : « Dos / biceps — 12/10/2026 » (ou la date seule), puis ses séries. */
data class SeanceHistorique(val entete: String, val series: List<LigneHistorique>)

/**
 * Une série : palier principal « 10 × 45 kg », puis ses paliers dégressifs « 8 × 30 kg », affichés dessous
 * avec une flèche ↓ (spec §4.3.8, v1.4).
 */
data class LigneHistorique(val principal: String, val degressifs: List<String> = emptyList())

/**
 * Regroupe les paliers [series] par séance puis par série, dans l'ordre reçu (de la séance la plus récente à
 * la plus ancienne, puis exercices, séries et paliers dans l'ordre) : un exercice fait deux fois dans une
 * séance y voit ses séries fusionnées. Le titre est le nom enregistré dans la séance la plus récente.
 * `null` sans aucune série.
 */
fun historiqueExercice(series: List<SerieHistorique>): HistoriqueExercice? {
    val recente = series.firstOrNull() ?: return null
    val seances = series.groupBy { it.entrainementId }.values.map { seance ->
        val premiere = seance.first()
        SeanceHistorique(
            entete = listOfNotNull(premiere.libelle, formaterDateNumerique(premiere.date)).joinToString(" — "),
            series = seance.groupBy { it.serieId }.values.map { paliers ->
                val textes = paliers.map { decrirePalierHistorique(it.poidsKg, it.repetitions) }
                LigneHistorique(textes.first(), textes.drop(1))
            },
        )
    }
    return HistoriqueExercice(recente.nom, seances)
}
