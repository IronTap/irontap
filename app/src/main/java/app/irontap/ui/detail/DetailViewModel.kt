package app.irontap.ui.detail

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.irontap.IronTapApplication
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Exercice
import app.irontap.donnees.Horloge
import app.irontap.donnees.Serie
import app.irontap.logique.Suggestion
import app.irontap.logique.ajouterPoids
import app.irontap.logique.ajouterRepetitions
import app.irontap.logique.decrireSerie
import app.irontap.logique.formaterPoids
import app.irontap.logique.lirePoids
import app.irontap.logique.lireRepetitions
import app.irontap.logique.suggerer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EtatDetail {
    data object Chargement : EtatDetail

    /** Entraînement supprimé (ou inexistant) : l'écran se ferme. */
    data object Introuvable : EtatDetail

    data class Pret(val complet: EntrainementComplet) : EtatDetail
}

/** Feuille d'édition (écran 7) : [serie] `null` pour « Ajouter une série ». */
data class EditionSerie(val exercice: Exercice, val serie: Serie?)

/** Série dont la suppression attend confirmation (6b). */
data class SuppressionSerie(val exercice: Exercice, val serie: Serie) {
    val description get() = decrireSerie(serie.poidsKg, serie.repetitions)
}

/** Ce que l'écran affiche par-dessus le détail, hors champs de saisie. */
data class Panneaux(
    val edition: EditionSerie? = null,
    val erreurRepetitions: Boolean = false,
    val suppression: SuppressionSerie? = null,
    /**
     * Série dont la suppression est confirmée : sa ligne reste ouverte jusqu'à ce qu'elle disparaisse de
     * la liste (au lieu de se refermer le temps que la base réponde).
     */
    val serieEnSuppression: Long? = null,
    /** Menu ⋮ > Supprimer l'entraînement : confirmation affichée. */
    val suppressionEntrainement: Boolean = false,
    /** Exercice en cours de renommage. */
    val renommage: Exercice? = null,
    val erreurNom: Boolean = false,
    /** Dernière suggestion choisie : la liste reste fermée tant que le nom n'est pas retouché. */
    val nomChoisi: String? = null,
    /** Une écriture en base a échoué (stockage plein…). */
    val erreurEnregistrement: Boolean = false,
)

/** Détail d'un entraînement passé (écran 4, spec §4.4) et édition de ses séries (écran 5, spec §4.5). */
class DetailViewModel(
    private val dao: EntrainementDao,
    private val horloge: Horloge,
    private val id: Long,
) : ViewModel() {

    val etat: StateFlow<EtatDetail> = dao.observerComplet(id)
        .map { if (it == null) EtatDetail.Introuvable else EtatDetail.Pret(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatDetail.Chargement)

    private val _panneaux = MutableStateFlow(Panneaux())
    val panneaux: StateFlow<Panneaux> = _panneaux.asStateFlow()

    /** Champs de la feuille d'édition. */
    val poids = TextFieldState()
    val repetitions = TextFieldState()

    /** Champ du dialogue « Renommer l'exercice ». */
    val nom = TextFieldState()

    private val texteNom = snapshotFlow { nom.text.toString() }

    /** Autocomplétion du renommage, mêmes règles que la saisie (spec §4.3.2). */
    val suggestions: StateFlow<List<Suggestion>> = combine(
        texteNom,
        dao.observerNomsExercices(),
        _panneaux.map { it.renommage to it.nomChoisi },
    ) { saisie, noms, (renommage, choisi) ->
        if (renommage == null || saisie == choisi) emptyList() else suggerer(saisie, noms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Dernière écriture lancée : les suivantes l'attendent, pour s'exécuter dans l'ordre des appuis. */
    private var ecriture: Job? = null

    /** Un double appui sur « Enregistrer » n'enregistre qu'une fois. */
    private val occupe get() = ecriture?.isActive == true

    init {
        // Une erreur disparaît dès que le champ fautif est modifié.
        viewModelScope.launch { texteNom.collect { _panneaux.update { it.copy(erreurNom = false) } } }
        viewModelScope.launch {
            snapshotFlow { repetitions.text.toString() }.collect { _panneaux.update { it.copy(erreurRepetitions = false) } }
        }
    }

    /**
     * Écrit en base après l'écriture précédente, puis applique [apres]. Un échec SQLite (stockage plein…)
     * ne plante pas : [echec] rouvre au besoin le panneau concerné, qui affiche alors l'erreur.
     */
    private fun ecrire(apres: () -> Unit = {}, echec: (Panneaux) -> Panneaux = { it }, bloc: suspend () -> Unit) {
        val precedente = ecriture
        ecriture = viewModelScope.launch {
            precedente?.join()
            try {
                bloc()
                _panneaux.update { it.copy(erreurEnregistrement = false) }
                apres()
            } catch (e: SQLiteException) {
                _panneaux.update { echec(it).copy(erreurEnregistrement = true) }
            }
        }
    }

    // --- Édition d'une série (écran 7) ---

    /** Appui sur une série ([serie]) ou sur « Ajouter une série » (`null`) : champs pré-remplis ou vides. */
    fun ouvrirEdition(exercice: Exercice, serie: Serie?) {
        poids.setTextAndPlaceCursorAtEnd(serie?.poidsKg?.let(::formaterPoids).orEmpty())
        repetitions.setTextAndPlaceCursorAtEnd(serie?.repetitions?.toString().orEmpty())
        _panneaux.update { Panneaux(edition = EditionSerie(exercice, serie)) }
    }

    fun ajouterAuPoids(valeur: Int) = poids.setTextAndPlaceCursorAtEnd(ajouterPoids(poids.text.toString(), valeur))

    fun ajouterAuxRepetitions(valeur: Int) =
        repetitions.setTextAndPlaceCursorAtEnd(ajouterRepetitions(repetitions.text.toString(), valeur))

    /** Même validation qu'en saisie (spec §4.3.5) : répétitions obligatoires, poids facultatif. */
    fun enregistrerEdition() {
        val edition = _panneaux.value.edition ?: return
        if (occupe) return
        val repetitions = lireRepetitions(repetitions.text.toString())
        if (repetitions == null) {
            _panneaux.update { it.copy(erreurRepetitions = true) }
            return
        }
        val poids = lirePoids(poids.text.toString())
        ecrire(apres = ::fermerEdition) {
            if (edition.serie == null) {
                dao.ajouterSerie(edition.exercice.id, poids, repetitions, horloge.maintenant())
            } else {
                dao.modifierSerie(edition.serie.id, poids, repetitions, horloge.maintenant())
            }
        }
    }

    fun fermerEdition() = _panneaux.update { it.copy(edition = null, erreurRepetitions = false, erreurEnregistrement = false) }

    // --- Suppression d'une série (6b) ---

    /** Balayage d'une série, ou « Supprimer » de la feuille d'édition : confirmation d'abord. */
    fun demanderSuppression(exercice: Exercice, serie: Serie) =
        _panneaux.update { it.copy(suppression = SuppressionSerie(exercice, serie), erreurEnregistrement = false) }

    fun annulerSuppression() = _panneaux.update { it.copy(suppression = null, erreurEnregistrement = false) }

    /** Supprime la série ; les suivantes sont renumérotées et un exercice vide disparaît (spec §4.4). */
    fun confirmerSuppression() {
        val suppression = _panneaux.value.suppression ?: return
        _panneaux.update { panneaux ->
            // La feuille d'édition de cette série se ferme avec elle.
            val edition = panneaux.edition?.takeUnless { it.serie?.id == suppression.serie.id }
            panneaux.copy(suppression = null, edition = edition, serieEnSuppression = suppression.serie.id)
        }
        // En cas d'échec, le dialogue revient avec l'erreur.
        ecrire(echec = { it.copy(suppression = suppression, serieEnSuppression = null) }) {
            dao.supprimerSerie(suppression.serie.id)
        }
    }

    // --- Renommage d'un exercice ---

    fun ouvrirRenommage(exercice: Exercice) {
        nom.setTextAndPlaceCursorAtEnd(exercice.nom)
        // Le nom actuel ne propose pas de suggestions tant qu'il n'est pas retouché.
        _panneaux.update { Panneaux(renommage = exercice, nomChoisi = exercice.nom) }
    }

    fun choisir(suggestion: Suggestion) {
        nom.setTextAndPlaceCursorAtEnd(suggestion.nom)
        _panneaux.update { it.copy(nomChoisi = suggestion.nom) }
    }

    fun renommer() {
        val exercice = _panneaux.value.renommage ?: return
        if (occupe) return
        val nouveau = nom.text.toString()
        if (nouveau.isBlank()) {
            _panneaux.update { it.copy(erreurNom = true) }
            return
        }
        ecrire(apres = ::fermerRenommage) { dao.renommerExercice(exercice.id, nouveau) }
    }

    fun fermerRenommage() {
        nom.clearText()
        _panneaux.update { it.copy(renommage = null, erreurNom = false, nomChoisi = null, erreurEnregistrement = false) }
    }

    // --- Entraînement ---

    fun demanderSuppressionEntrainement() = _panneaux.update { it.copy(suppressionEntrainement = true, erreurEnregistrement = false) }

    fun annulerSuppressionEntrainement() = _panneaux.update { it.copy(suppressionEntrainement = false, erreurEnregistrement = false) }

    /** Menu ⋮ > Supprimer l'entraînement, après confirmation : l'écran se ferme quand la base ne le trouve plus. */
    fun supprimerEntrainement() {
        _panneaux.update { it.copy(suppressionEntrainement = false) }
        ecrire(echec = { it.copy(suppressionEntrainement = true) }) { dao.supprimerEntrainement(id) }
    }

    companion object {
        fun fabrique(id: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                DetailViewModel(conteneur.base.entrainements(), conteneur.horloge, id)
            }
        }
    }
}
