package app.irontap.ui.export

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.irontap.IronTapApplication
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Horloge
import app.irontap.export.Exporteur
import app.irontap.logique.FormatExport
import app.irontap.logique.apercuExport
import app.irontap.logique.formaterDate
import app.irontap.logique.nomFichierExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

sealed interface EtatExport {
    data object Chargement : EtatExport

    /** Rien à exporter : historique vide, ou entraînement supprimé entre-temps. */
    data object Vide : EtatExport

    /**
     * Export prêt dans les deux formats. [portee] : sous-titre de la feuille ; [dateEntrainement] : date de
     * l'entraînement exporté seul, `null` pour tout l'historique.
     */
    data class Pret(
        val portee: String,
        val dateEntrainement: LocalDate?,
        val contenus: Map<FormatExport, String>,
    ) : EtatExport {
        val apercus: Map<FormatExport, String> = contenus.mapValues { apercuExport(it.value) }
    }
}

/** Partage ou enregistrement en cours, et dernier échec à afficher dans la feuille. */
data class OperationExport(val occupe: Boolean = false, val erreur: String? = null)

/**
 * Export (écran 8, spec §5) : tout l'historique terminé si [id] est `null` (accueil), sinon cet entraînement
 * (menu ⋮ du détail). Les contenus suivent la base tant que la feuille est affichée.
 */
class ExportViewModel(
    dao: EntrainementDao,
    private val horloge: Horloge,
    private val exporteur: Exporteur,
    id: Long?,
    private val etatSauve: SavedStateHandle,
) : ViewModel() {

    /** Format choisi, conservé si l'appli est tuée pendant le choix du fichier. Texte par défaut (spec §5). */
    val format: StateFlow<FormatExport> = etatSauve.getStateFlow(CLE_FORMAT, FormatExport.Texte)

    private val source: Flow<List<EntrainementComplet>> =
        if (id == null) dao.observerHistoriqueComplet() else dao.observerComplet(id).map { listOfNotNull(it) }

    val etat: StateFlow<EtatExport> = source
        .map { entrainements ->
            if (entrainements.isEmpty()) {
                EtatExport.Vide
            } else {
                val seul = entrainements.singleOrNull()?.entrainement?.takeIf { id != null }
                EtatExport.Pret(
                    portee = if (seul != null) {
                        listOfNotNull(formaterDate(seul.date), seul.libelle).joinToString(" · ")
                    } else {
                        "Tout l’historique · ${entrainements.size} entraînement${if (entrainements.size > 1) "s" else ""}"
                    },
                    dateEntrainement = seul?.date,
                    contenus = FormatExport.entries.associateWith { it.generer(entrainements) },
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatExport.Chargement)

    private val _operation = MutableStateFlow(OperationExport())
    val operation: StateFlow<OperationExport> = _operation.asStateFlow()

    fun choisir(format: FormatExport) {
        etatSauve[CLE_FORMAT] = format
        _operation.update { it.copy(erreur = null) }
    }

    /** Feuille refermée : la prochaine ouverture repart du texte, sans erreur. */
    fun reinitialiser() {
        etatSauve[CLE_FORMAT] = FormatExport.Texte
        _operation.value = OperationExport()
    }

    /** Nom proposé pour le fichier, dans le format choisi. */
    fun nomFichier(pret: EtatExport.Pret): String = nomFichierExport(format.value, pret.dateEntrainement, horloge.aujourdhui())

    /** « Partager… » : intention `ACTION_SEND` à présenter dans le sélecteur, ou `null` (occupé, ou échec affiché). */
    suspend fun partager(): Intent? {
        if (!reserver()) return null
        return executer("Partage impossible : stockage indisponible") { pret, format ->
            exporteur.intentionPartage(format, nomFichier(pret), pret.contenus.getValue(format), "IronTap · ${pret.portee}")
        }
    }

    /**
     * « Enregistrer le fichier », avant d'ouvrir le sélecteur de document : `false` si une opération est déjà
     * en cours (double appui). Suivi de [enregistrer] ou [abandonner].
     */
    fun reserver(): Boolean {
        var libre = false
        _operation.update { if (it.occupe) it else it.copy(occupe = true, erreur = null).also { libre = true } }
        return libre
    }

    /** Sélecteur de document fermé sans choix, ou impossible à ouvrir ([erreur]). */
    fun abandonner(erreur: String? = null) = _operation.update { OperationExport(erreur = erreur) }

    /** Écrit l'export dans le document choisi ; `true` si c'est fait, sinon l'erreur est affichée. */
    suspend fun enregistrer(uri: Uri): Boolean =
        executer("Enregistrement impossible : le fichier n’a pas pu être écrit") { pret, format ->
            exporteur.enregistrer(uri, format, pret.contenus.getValue(format))
        } != null

    /** Exécute [bloc] hors du fil principal sur l'export prêt, puis libère l'opération réservée. */
    private suspend fun <T : Any> executer(messageEchec: String, bloc: (EtatExport.Pret, FormatExport) -> T): T? {
        try {
            val pret = etat.first { it != EtatExport.Chargement } as? EtatExport.Pret
            if (pret == null) {
                _operation.value = OperationExport(erreur = "Rien à exporter")
                return null
            }
            val resultat = withContext(Dispatchers.IO) { bloc(pret, format.value) }
            _operation.value = OperationExport()
            return resultat
        } catch (e: IOException) {
            _operation.value = OperationExport(erreur = messageEchec)
            return null
        } finally {
            // Annulation (feuille fermée en cours de route) : l'opération ne reste pas bloquée.
            _operation.update { it.copy(occupe = false) }
        }
    }

    companion object {
        private const val CLE_FORMAT = "format"

        fun fabrique(id: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                ExportViewModel(conteneur.base.entrainements(), conteneur.horloge, conteneur.exporteur, id, createSavedStateHandle())
            }
        }
    }
}
