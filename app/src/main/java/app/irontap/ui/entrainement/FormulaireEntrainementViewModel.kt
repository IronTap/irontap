package app.irontap.ui.entrainement

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.irontap.IronTapApplication
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Horloge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Issue du formulaire : l'écran se ferme ou ouvre la saisie. */
enum class FinFormulaire { Lance, Enregistre, Introuvable }

data class EtatFormulaire(
    /** `null` tant que l'entraînement à modifier n'est pas chargé. */
    val date: LocalDate?,
    val modification: Boolean,
    val erreur: Boolean = false,
    val fin: FinFormulaire? = null,
)

/**
 * Écran 2 (spec §4.2) : création d'un entraînement ([id] `null`, date du jour) ou modification de la date
 * et du libellé d'un entraînement existant.
 */
class FormulaireEntrainementViewModel(
    private val dao: EntrainementDao,
    private val horloge: Horloge,
    private val id: Long?,
) : ViewModel() {

    val aujourdhui: LocalDate = horloge.aujourdhui()

    val libelle = TextFieldState()

    private val _etat = MutableStateFlow(EtatFormulaire(date = if (id == null) aujourdhui else null, modification = id != null))
    val etat: StateFlow<EtatFormulaire> = _etat.asStateFlow()

    private var enregistrement: Job? = null

    init {
        if (id != null) {
            viewModelScope.launch {
                val entrainement = dao.observer(id).first()
                if (entrainement == null) {
                    _etat.update { it.copy(fin = FinFormulaire.Introuvable) }
                } else {
                    libelle.setTextAndPlaceCursorAtEnd(entrainement.libelle.orEmpty())
                    _etat.update { it.copy(date = entrainement.date) }
                }
            }
        }
    }

    fun choisirDate(date: LocalDate) = _etat.update { it.copy(date = date) }

    /** « Lancer l'entraînement » ou « Enregistrer ». Un double appui n'enregistre qu'une fois. */
    fun valider() {
        val date = _etat.value.date ?: return
        if (enregistrement?.isActive == true || _etat.value.fin != null) return
        enregistrement = viewModelScope.launch {
            try {
                val fin = if (id == null) {
                    // `null` s'il en existe déjà un en cours : la saisie le reprendra.
                    dao.creerEntrainement(date, libelle.text.toString(), horloge.maintenant())
                    FinFormulaire.Lance
                } else {
                    dao.modifierEntrainement(id, date, libelle.text.toString())
                    FinFormulaire.Enregistre
                }
                _etat.update { it.copy(erreur = false, fin = fin) }
            } catch (e: SQLiteException) {
                _etat.update { it.copy(erreur = true) }
            }
        }
    }

    companion object {
        /** [id] : entraînement à modifier, `null` pour en créer un. */
        fun fabrique(id: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                FormulaireEntrainementViewModel(conteneur.base.entrainements(), conteneur.horloge, id)
            }
        }
    }
}
