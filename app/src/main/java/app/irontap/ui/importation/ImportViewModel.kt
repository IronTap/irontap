package app.irontap.ui.importation

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.irontap.IronTapApplication
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Horloge
import app.irontap.export.FichierTropVolumineux
import app.irontap.logique.EntrainementImporte
import app.irontap.logique.ErreurImport
import app.irontap.logique.LectureImport
import app.irontap.logique.ecarterDoublons
import app.irontap.logique.lireExportCsv
import app.irontap.logique.versImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface EtatImport {
    data object Aucun : EtatImport

    /** Lecture du fichier, vérification ou écriture en cours. */
    data object EnCours : EtatImport

    /** Fichier valide : [entrainements] à ajouter, [dejaPresents] ignorés. */
    data class Confirmation(val entrainements: List<EntrainementImporte>, val dejaPresents: Int) : EtatImport {
        val debut = entrainements.minOf { it.date }
        val fin = entrainements.maxOf { it.date }
    }

    /** Les [total] entraînements du fichier sont déjà dans l'historique. */
    data class RienAImporter(val total: Int) : EtatImport

    /** Fichier refusé (rien n'est importé), ou écriture impossible. */
    data class Echec(val erreurs: List<ErreurImport>) : EtatImport

    /** [nombre] entraînements ajoutés : toast système, puis [ImportViewModel.fermer]. */
    data class Termine(val nombre: Int) : EtatImport
}

/**
 * Import d'un export CSV (DECISIONS.md « Import »), depuis l'accueil : le fichier choisi est lu et vérifié en entier,
 * puis, après confirmation, ajouté à l'historique en une transaction. [lireFichier] : contenu du document choisi.
 */
class ImportViewModel(
    private val dao: EntrainementDao,
    private val horloge: Horloge,
    private val lireFichier: (Uri) -> String,
) : ViewModel() {

    private val _etat = MutableStateFlow<EtatImport>(EtatImport.Aucun)
    val etat: StateFlow<EtatImport> = _etat.asStateFlow()

    /** Document choisi : confirmation, rien à importer, ou erreurs. Ignoré si un import est déjà en cours. */
    fun analyser(uri: Uri) {
        if (_etat.value != EtatImport.Aucun) return
        _etat.value = EtatImport.EnCours
        viewModelScope.launch { _etat.value = analyse(uri) }
    }

    private suspend fun analyse(uri: Uri): EtatImport {
        val contenu = try {
            withContext(Dispatchers.IO) { lireFichier(uri) }
        } catch (e: FichierTropVolumineux) {
            return echec("Ce fichier est trop volumineux pour être un export d’IronTap.")
        } catch (e: IOException) {
            return echec("Le fichier n’a pas pu être lu.")
        }
        val lu = withContext(Dispatchers.Default) { lireExportCsv(contenu) }
        if (lu is LectureImport.Erreurs) return EtatImport.Echec(lu.erreurs)
        val importes = (lu as LectureImport.Valide).entrainements
        val existants = try {
            dao.lireHistoriqueComplet()
        } catch (e: SQLiteException) {
            return echec(STOCKAGE_INDISPONIBLE)
        }
        val (nouveaux, ignores) = withContext(Dispatchers.Default) { ecarterDoublons(importes, existants.map { it.versImport() }) }
        return if (nouveaux.isEmpty()) EtatImport.RienAImporter(importes.size) else EtatImport.Confirmation(nouveaux, ignores)
    }

    /** « Importer » : ajout en une transaction (les doublons sont de nouveau écartés, l'historique a pu changer). */
    fun confirmer() {
        val confirmation = _etat.value as? EtatImport.Confirmation ?: return
        _etat.value = EtatImport.EnCours
        viewModelScope.launch {
            _etat.value = try {
                EtatImport.Termine(dao.importer(confirmation.entrainements, horloge.maintenant()))
            } catch (e: SQLiteException) {
                echec(STOCKAGE_INDISPONIBLE)
            }
        }
    }

    /** Sélecteur de document impossible à ouvrir. */
    fun signaler(message: String) {
        if (_etat.value == EtatImport.Aucun) _etat.value = echec(message)
    }

    /** Dialogue fermé, ou toast de fin affiché. */
    fun fermer() {
        if (_etat.value != EtatImport.EnCours) _etat.value = EtatImport.Aucun
    }

    private fun echec(message: String) = EtatImport.Echec(listOf(ErreurImport(null, message)))

    companion object {
        private const val STOCKAGE_INDISPONIBLE = "Enregistrement impossible : stockage indisponible."

        /** Types proposés par le sélecteur : un CSV peut être annoncé de bien des façons selon l'appli qui le fournit. */
        val TypesFichier = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

        val Fabrique = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                ImportViewModel(conteneur.base.entrainements(), conteneur.horloge, conteneur.importeur::lire)
            }
        }
    }
}
