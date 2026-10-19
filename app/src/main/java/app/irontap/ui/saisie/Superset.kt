package app.irontap.ui.saisie

import androidx.compose.foundation.text.input.TextFieldState
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.PalierEnCours
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.SupersetEnCours
import app.irontap.donnees.ValeursPalier
import app.irontap.logique.decrireSerie

/** Champs d'un onglet : l'exercice courant (ou l'onglet A d'un superset), ou l'onglet B. */
class ChampsOnglet {
    val nom = TextFieldState()
    val poids = TextFieldState()
    val repetitions = TextFieldState()

    /** Ni poids, ni répétitions : onglet ignoré par « Nouvel exercice » s'il n'a pas non plus de palier gardé. */
    val vide get() = poids.text.isBlank() && repetitions.text.isBlank()
}

/**
 * Superset en cours de saisie (spec §4.3.10), vu de la base : ses exercices A et B une fois créés (au premier
 * tour), les paliers gardés par onglet, et le numéro du tour en cours.
 */
class EtatSuperset(
    val enCours: SupersetEnCours,
    exercices: List<ExerciceAvecSeries>,
    paliersEnCours: List<PalierEnCours>,
) {
    private val parRole = exercices.filter { it.exercice.supersetId == enCours.supersetId }.associateBy { it.exercice.supersetRole }
    private val gardes = paliersEnCours.groupBy { it.role }

    fun exercice(role: RoleSuperset): ExerciceAvecSeries? = parRole[role]

    /** Paliers gardés par « Dégressive » pour cet onglet, dans l'ordre. */
    fun paliers(role: RoleSuperset): List<ValeursPalier> = gardes[role].orEmpty().map { it.valeurs }

    /** Nom verrouillé : l'exercice a déjà une série (tour précédent) ou un palier gardé dans ce tour. */
    fun verrouille(role: RoleSuperset) = exercice(role)?.series?.isNotEmpty() == true || paliers(role).isNotEmpty()

    /** Nom de l'onglet verrouillé, `null` s'il est encore modifiable. */
    fun nomVerrouille(role: RoleSuperset): String? =
        if (verrouille(role)) exercice(role)?.exercice?.nom ?: enCours.nom(role) else null

    /** Nom enregistré (exercice créé, ou mémorisé), vide s'il n'est pas encore connu. */
    fun nom(role: RoleSuperset): String = exercice(role)?.exercice?.nom ?: enCours.nom(role)

    /** Tour en cours : le suivant du dernier tour enregistré, A et B confondus. */
    val tour = 1 + RoleSuperset.entries.maxOf { role -> exercice(role)?.series?.maxOfOrNull { it.serie.ordre } ?: 0 }

    /** En-tête en dégressive pour cet onglet : « 50 kg × 10 ↓ … », `null` sans palier gardé. */
    fun chaineDegressive(role: RoleSuperset): String? = paliers(role).takeIf { it.isNotEmpty() }?.let { decrireSerie(it) + " ↓ …" }
}
