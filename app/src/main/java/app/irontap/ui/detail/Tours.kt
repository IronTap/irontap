package app.irontap.ui.detail

import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.SerieAvecPaliers

/** Ce que le détail affiche pour un exercice : une carte de séries, ou la carte d'un superset A | B (maquette H). */
sealed interface BlocDetail {
    val cle: String

    data class Simple(val exercice: ExerciceAvecSeries) : BlocDetail {
        override val cle get() = "exercice-${exercice.exercice.id}"
    }

    data class Superset(val a: ExerciceAvecSeries, val b: ExerciceAvecSeries) : BlocDetail {
        override val cle get() = "superset-${a.exercice.id}"

        /** Tours, du premier au dernier : la série de A et celle de B de même numéro (absente : « — »). */
        val tours: List<TourSuperset> = (a.series + b.series).map { it.serie.ordre }.distinct().sorted().map { numero ->
            TourSuperset(numero, a.series.firstOrNull { it.serie.ordre == numero }, b.series.firstOrNull { it.serie.ordre == numero })
        }

        fun exercice(role: RoleSuperset) = if (role == RoleSuperset.A) a else b
    }
}

/** Un tour de superset : son numéro et la série de chaque exercice, s'il en a une. */
data class TourSuperset(val numero: Int, val a: SerieAvecPaliers?, val b: SerieAvecPaliers?) {
    fun serie(role: RoleSuperset) = if (role == RoleSuperset.A) a else b
}

/**
 * Exercices du détail regroupés en blocs, dans l'ordre : les deux exercices d'un superset partagent une carte, à la
 * place de A. Un exercice dont le partenaire manque reste une carte ordinaire.
 */
fun blocsDetail(exercices: List<ExerciceAvecSeries>): List<BlocDetail> {
    val parSuperset = exercices.filter { it.exercice.supersetId != null }.groupBy { it.exercice.supersetId }
    return exercices.mapNotNull { exercice ->
        val membres = parSuperset[exercice.exercice.supersetId]
        val a = membres?.firstOrNull { it.exercice.supersetRole == RoleSuperset.A }
        val b = membres?.firstOrNull { it.exercice.supersetRole == RoleSuperset.B }
        when {
            a == null || b == null -> BlocDetail.Simple(exercice)
            exercice === a -> BlocDetail.Superset(a, b)
            else -> null
        }
    }
}

/**
 * Supprimer la série [ordre] de l'exercice [exerciceId] renumérote-t-il des séries ? Pour un exercice ordinaire,
 * s'il en a d'autres après ; dans un superset, seulement si ce tour devient vide et qu'il en reste après lui.
 */
fun renumerotationApresSuppression(blocs: List<BlocDetail>, exerciceId: Long, ordre: Int): Boolean =
    when (val bloc = blocs.firstOrNull { it.contient(exerciceId) }) {
        is BlocDetail.Simple -> bloc.exercice.series.any { it.serie.ordre > ordre }
        is BlocDetail.Superset -> {
            val tour = bloc.tours.firstOrNull { it.numero == ordre }
            val autre = if (bloc.a.exercice.id == exerciceId) tour?.b else tour?.a
            autre == null && bloc.tours.any { it.numero > ordre }
        }
        null -> false
    }

private fun BlocDetail.contient(exerciceId: Long) = when (this) {
    is BlocDetail.Simple -> exercice.exercice.id == exerciceId
    is BlocDetail.Superset -> a.exercice.id == exerciceId || b.exercice.id == exerciceId
}
