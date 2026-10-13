package app.irontap.ui.saisie

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.irontap.IronTapApplication
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Horloge
import app.irontap.logique.Suggestion
import app.irontap.logique.ajouterPoids
import app.irontap.logique.ajouterRepetitions
import app.irontap.logique.decrireSerie
import app.irontap.logique.lirePoids
import app.irontap.logique.lireRepetitions
import app.irontap.logique.suggerer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que la base dit de l'entraînement en cours. */
sealed interface EtatSaisie {
    data object Chargement : EtatSaisie

    /** Plus d'entraînement en cours (terminé, ou jamais créé) : l'écran se ferme. */
    data object Aucun : EtatSaisie

    data class EnCours(
        val entrainement: Entrainement,
        val exercices: List<ExerciceAvecSeries>,
        val chrono: EtatChrono?,
    ) : EtatSaisie {
        private val dernier = exercices.lastOrNull()

        /** Nom verrouillé : l'exercice courant a déjà une série et « Nouvel exercice » n'a pas été demandé. */
        val verrouille = dernier != null && !entrainement.nouvelExercice

        /** Nom de l'exercice courant quand il est verrouillé. */
        val nomVerrouille = if (verrouille) dernier?.exercice?.nom else null

        val numeroExercice = if (verrouille) exercices.size else exercices.size + 1
        val numeroSerie = if (verrouille) dernier!!.series.size + 1 else 1
    }
}

/** Dialogue « Terminer l'entraînement ? » (spec §4.3.6). */
sealed interface DialogueFin {
    /** 4a : la série en cours est remplie et valide, [description] « 45 kg × 10 ». */
    data class AvecSerie(val description: String) : DialogueFin

    /** 4b. */
    data object Simple : DialogueFin
}

/** État propre à l'écran, hors champs de saisie. */
data class Formulaire(
    val erreurNom: Boolean = false,
    val erreurRepetitions: Boolean = false,
    val dialogue: DialogueFin? = null,
    val toast: String? = null,
    /** Dernière suggestion choisie : la liste reste fermée tant que le nom n'est pas retouché. */
    val nomChoisi: String? = null,
)

/** Saisie des séries de l'entraînement en cours (écran 3, spec §4.3). */
class SaisieViewModel(
    private val dao: EntrainementDao,
    private val horloge: Horloge,
) : ViewModel(), ActionsSaisie {

    // Champs de saisie : `TextFieldState` plutôt qu'un StateFlow, pour ne jamais perdre une frappe.
    val nom = TextFieldState()
    val poids = TextFieldState()
    val repetitions = TextFieldState()

    private val _formulaire = MutableStateFlow(Formulaire())
    val formulaire: StateFlow<Formulaire> = _formulaire.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val etat: StateFlow<EtatSaisie> = dao.observerEnCours()
        .flatMapLatest { entrainement ->
            if (entrainement == null) {
                flowOf(EtatSaisie.Aucun)
            } else {
                combine(dao.observerExercices(entrainement.id), dao.observerChrono(entrainement.id)) { exercices, chrono ->
                    EtatSaisie.EnCours(entrainement, exercices, chrono)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatSaisie.Chargement)

    private val texteNom = snapshotFlow { nom.text.toString() }

    val suggestions: StateFlow<List<Suggestion>> = combine(
        texteNom,
        dao.observerNomsExercices(),
        _formulaire.map { it.nomChoisi }.distinctUntilChanged(),
    ) { saisie, noms, choisi ->
        if (saisie == choisi) emptyList() else suggerer(saisie, noms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** `elapsedRealtime` courant, pour l'affichage du chrono. */
    fun realtime(): Long = horloge.realtime()

    private var enregistrementEnCours = false
    private var toastJob: Job? = null

    init {
        // Une erreur disparaît dès que le champ fautif est modifié (spec §4.3.5).
        viewModelScope.launch { texteNom.collect { _formulaire.update { it.copy(erreurNom = false) } } }
        viewModelScope.launch {
            snapshotFlow { repetitions.text.toString() }.collect { _formulaire.update { it.copy(erreurRepetitions = false) } }
        }
    }

    override fun choisir(suggestion: Suggestion) {
        nom.setTextAndPlaceCursorAtEnd(suggestion.nom)
        _formulaire.update { it.copy(nomChoisi = suggestion.nom) }
    }

    override fun ajouterAuPoids(valeur: Int) = poids.setTextAndPlaceCursorAtEnd(ajouterPoids(poids.text.toString(), valeur))

    override fun ajouterAuxRepetitions(valeur: Int) =
        repetitions.setTextAndPlaceCursorAtEnd(ajouterRepetitions(repetitions.text.toString(), valeur))

    // --- Chrono ---

    /** Play / Pause. */
    override fun basculerChrono() = modifierChrono { chrono, realtime ->
        if (chrono.etat == EtatChrono.Etat.EnCours) chrono.enPause(realtime) else chrono.demarre(realtime)
    }

    override fun relancerChrono() = modifierChrono { chrono, realtime -> chrono.relance(realtime) }

    private fun modifierChrono(action: (EtatChrono, Long) -> EtatChrono) {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        // L'instant est celui de l'appui, pas celui où la base est écrite.
        val realtime = horloge.realtime()
        viewModelScope.launch { dao.modifierChrono(enCours.entrainement.id) { action(it, realtime) } }
    }

    // --- Séries ---

    /** « Nouvelle série » ou « Nouvel exercice » : valide, enregistre, relance le chrono, vide les champs. */
    override fun enregistrer(suite: Suite) {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        val serie = valider(enCours) ?: return
        enregistrer(enCours, serie, suite) { numero ->
            vider(suite)
            afficherToast("Série $numero enregistrée · ${decrireSerie(serie.poidsKg, serie.repetitions)}")
        }
    }

    private class SerieSaisie(val poidsKg: Double?, val repetitions: Int)

    /** Série remplie et valide, ou `null` après avoir signalé les champs fautifs. */
    private fun valider(enCours: EtatSaisie.EnCours): SerieSaisie? {
        val nomManquant = !enCours.verrouille && nom.text.isBlank()
        val repetitions = lireRepetitions(repetitions.text.toString())
        _formulaire.update { it.copy(erreurNom = nomManquant, erreurRepetitions = repetitions == null) }
        if (nomManquant || repetitions == null) return null
        return SerieSaisie(lirePoids(poids.text.toString()), repetitions)
    }

    private fun enregistrer(enCours: EtatSaisie.EnCours, serie: SerieSaisie, suite: Suite, ensuite: (Int) -> Unit) {
        // Un double appui ne doit pas enregistrer deux fois la même série.
        if (enregistrementEnCours) return
        enregistrementEnCours = true
        viewModelScope.launch {
            try {
                val enregistree = dao.enregistrerSerie(
                    entrainementId = enCours.entrainement.id,
                    nomExercice = nom.text.toString(),
                    poidsKg = serie.poidsKg,
                    repetitions = serie.repetitions,
                    suite = suite,
                    maintenant = horloge.maintenant(),
                    realtime = horloge.realtime(),
                )
                // Écran en retard sur la base (nom requis alors qu'il semblait verrouillé) : on le signale.
                if (enregistree == null) _formulaire.update { it.copy(erreurNom = true) } else ensuite(enregistree.ordre)
            } finally {
                enregistrementEnCours = false
            }
        }
    }

    private fun vider(suite: Suite) {
        poids.clearText()
        repetitions.clearText()
        if (suite == Suite.NouvelExercice) nom.clearText()
        _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, nomChoisi = null) }
    }

    private fun afficherToast(message: String) {
        toastJob?.cancel()
        _formulaire.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(DUREE_TOAST_MS)
            _formulaire.update { it.copy(toast = null) }
        }
    }

    // --- Fin de l'entraînement ---

    override fun demanderFin() {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        val repetitions = lireRepetitions(repetitions.text.toString())
        val remplie = (enCours.verrouille || nom.text.isNotBlank()) && repetitions != null
        val dialogue = if (remplie) {
            DialogueFin.AvecSerie(decrireSerie(lirePoids(poids.text.toString()), repetitions))
        } else {
            DialogueFin.Simple
        }
        _formulaire.update { it.copy(dialogue = dialogue) }
    }

    override fun annulerFin() = _formulaire.update { it.copy(dialogue = null) }

    /** Termine l'entraînement, après avoir enregistré la série en cours si [avecSerie]. L'écran se ferme ensuite. */
    override fun terminer(avecSerie: Boolean) {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        _formulaire.update { it.copy(dialogue = null) }
        val terminer = { viewModelScope.launch { dao.terminer(enCours.entrainement.id, horloge.maintenant()) } }
        val serie = if (avecSerie) valider(enCours) else null
        if (serie != null) enregistrer(enCours, serie, Suite.NouvelleSerie) { terminer() } else terminer()
    }

    companion object {
        const val DUREE_TOAST_MS = 2_600L

        val Fabrique = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                SaisieViewModel(conteneur.base.entrainements(), conteneur.horloge)
            }
        }
    }
}
