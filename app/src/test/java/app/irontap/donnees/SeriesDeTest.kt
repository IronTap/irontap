package app.irontap.donnees

import java.time.Instant

/**
 * Série de test : palier principal [poidsKg] × [repetitions], puis les paliers [degressifs] (poids, répétitions)
 * d'une dégressive. Identifiants des paliers dérivés de [id].
 */
fun serieDeTest(
    id: Long,
    exerciceId: Long,
    ordre: Int,
    poidsKg: Double?,
    repetitions: Int,
    creeLe: Instant,
    vararg degressifs: Pair<Double?, Int>,
) = SerieAvecPaliers(
    Serie(id, exerciceId, ordre, creeLe),
    (listOf(poidsKg to repetitions) + degressifs).mapIndexed { index, (poids, reps) -> Palier(id * 100 + index, id, index + 1, poids, reps) },
)
