package app.irontap.ui.accueil

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.irontap.IronTapApplication
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Horloge
import app.irontap.ui.saisie.EtatSaisie
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EtatAccueil {
    data object Chargement : EtatAccueil

    /** [historique] : entraînements terminés, du plus récent au plus ancien ; [enCours] : bandeau 1c. */
    data class Pret(val historique: List<Entrainement>, val enCours: EtatSaisie.EnCours?) : EtatAccueil
}

/** Accueil (écran 1, spec §4.1). */
class AccueilViewModel(private val dao: EntrainementDao, private val horloge: Horloge) : ViewModel() {

    val etat: StateFlow<EtatAccueil> = combine(dao.observerHistorique(), dao.observerEnCoursComplet()) { historique, enCours ->
        EtatAccueil.Pret(historique, enCours?.let { EtatSaisie.EnCours(it.entrainement, it.exercices, it.chrono) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatAccueil.Chargement)

    /** `elapsedRealtime` courant, pour le chrono du bandeau. */
    fun realtime(): Long = horloge.realtime()

    /**
     * « Nouvel entraînement » alors qu'un autre est en cours : il est d'abord terminé (rangé dans
     * l'historique, ou supprimé s'il n'a aucune série). Si la base échoue, il reste en cours : l'écran
     * suivant le reprendra au lieu d'en créer un autre.
     */
    fun terminerEnCours(): Job = viewModelScope.launch {
        val enCours = (etat.value as? EtatAccueil.Pret)?.enCours ?: return@launch
        try {
            dao.terminer(enCours.entrainement.id, horloge.maintenant())
        } catch (e: SQLiteException) {
            // Cf. KDoc.
        }
    }

    companion object {
        val Fabrique = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                AccueilViewModel(conteneur.base.entrainements(), conteneur.horloge)
            }
        }
    }
}
