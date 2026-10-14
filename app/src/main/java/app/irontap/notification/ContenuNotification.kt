package app.irontap.notification

import app.irontap.donnees.EntrainementEnCours
import app.irontap.donnees.EtatChrono
import app.irontap.ui.saisie.EtatSaisie

/**
 * Ce qu'affiche la notification du chrono (spec §4.3.4, maquettes 5a, 5b) : l'état du chrono et
 * « Exercice · Série N », la série en cours de saisie, comme l'en-tête de l'écran 3.
 */
data class ContenuNotification(val chrono: EtatChrono, val ligne: String) {

    val enCours get() = chrono.etat == EtatChrono.Etat.EnCours

    companion object {
        /** `null` sans entraînement en cours ou tant que le chrono n'a jamais été lancé : pas de notification. */
        fun de(enCours: EntrainementEnCours?): ContenuNotification? {
            val chrono = enCours?.chrono ?: return null
            if (chrono.etat == EtatChrono.Etat.Arrete) return null
            val saisie = EtatSaisie.EnCours(enCours.entrainement, enCours.exercices, chrono)
            // Après « Nouvel exercice », le nom n'est pas encore connu.
            val exercice = saisie.nomVerrouille ?: "Exercice ${saisie.numeroExercice}"
            return ContenuNotification(chrono, "$exercice · Série ${saisie.numeroSerie}")
        }
    }
}

/** Actions de la notification. Explicites (pas de bascule) : un appui sur une notification en retard reste sans surprise. */
enum class ActionChrono(val intentAction: String) {
    Pause("app.irontap.action.CHRONO_PAUSE"),
    Reprendre("app.irontap.action.CHRONO_REPRENDRE"),
    Relancer("app.irontap.action.CHRONO_RELANCER"),
    ;

    fun appliquer(chrono: EtatChrono, realtime: Long): EtatChrono = when (this) {
        Pause -> chrono.enPause(realtime)
        Reprendre -> chrono.demarre(realtime)
        Relancer -> chrono.relance(realtime)
    }

    companion object {
        /** Seules ces trois actions sont acceptées par le récepteur. */
        fun depuis(intentAction: String?): ActionChrono? = entries.firstOrNull { it.intentAction == intentAction }
    }
}
