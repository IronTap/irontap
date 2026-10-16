package app.irontap.ui.saisie

import android.database.sqlite.SQLiteException
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
import app.irontap.donnees.Serie
import app.irontap.logique.Suggestion
import app.irontap.logique.ajouterPoids
import app.irontap.logique.ajouterRepetitions
import app.irontap.logique.decrireSerie
import app.irontap.logique.lirePoids
import app.irontap.logique.lireRepetitions
import app.irontap.logique.suggerer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

        /** « Tirage vertical · Série 3 » (notification, accueil) ; après « Nouvel exercice », le nom n'est pas encore connu. */
        val ligne = "${nomVerrouille ?: "Exercice $numeroExercice"} · Série $numeroSerie"
    }
}

/** Série remplie et valide, prête à être enregistrée. [nom] est ignoré si le nom est verrouillé. */
data class SerieSaisie(val nom: String, val poidsKg: Double?, val repetitions: Int) {
    val description get() = decrireSerie(poidsKg, repetitions)
}

/** Dialogue « Terminer l'entraînement ? » (spec §4.3.6). */
sealed interface DialogueFin {
    /** 4a : la série en cours est remplie et valide ; c'est elle qui sera enregistrée. */
    data class AvecSerie(val serie: SerieSaisie) : DialogueFin

    /** 4b. */
    data object Simple : DialogueFin
}

/** État propre à l'écran, hors champs de saisie. */
data class Formulaire(
    val erreurNom: Boolean = false,
    val erreurRepetitions: Boolean = false,
    val dialogue: DialogueFin? = null,
    val toast: String? = null,
    /** Le toast signale un échec (icône d'erreur au lieu de ✓). */
    val toastErreur: Boolean = false,
    /** Dernière suggestion choisie : la liste reste fermée tant que le nom n'est pas retouché. */
    val nomChoisi: String? = null,
)

/**
 * Champs en cours de saisie, gardés par le conteneur de l'application : après Retour puis « Reprendre »,
 * la saisie est retrouvée telle qu'elle a été laissée (spec §4.1). Ils sont perdus si l'application est
 * tuée (spec §7). `TextFieldState` plutôt qu'un StateFlow, pour ne jamais perdre une frappe.
 */
class BrouillonSaisie {
    val nom = TextFieldState()
    val poids = TextFieldState()
    val repetitions = TextFieldState()

    private var entrainementId: Long? = null

    /** Associe le brouillon à l'entraînement en cours ; celui d'un autre entraînement est vidé. */
    fun pour(id: Long) {
        if (entrainementId != id) {
            vider()
            entrainementId = id
        }
    }

    fun vider() {
        nom.clearText()
        poids.clearText()
        repetitions.clearText()
    }
}

/** Saisie des séries de l'entraînement en cours (écran 3, spec §4.3). */
class SaisieViewModel(
    private val dao: EntrainementDao,
    private val horloge: Horloge,
    private val brouillon: BrouillonSaisie,
) : ViewModel(), ActionsSaisie {

    val nom = brouillon.nom
    val poids = brouillon.poids
    val repetitions = brouillon.repetitions

    private val _formulaire = MutableStateFlow(Formulaire())
    val formulaire: StateFlow<Formulaire> = _formulaire.asStateFlow()

    val etat: StateFlow<EtatSaisie> = dao.observerEnCoursComplet()
        .map { enCours ->
            if (enCours == null) EtatSaisie.Aucun else EtatSaisie.EnCours(enCours.entrainement, enCours.exercices, enCours.chrono)
        }
        .onEach { if (it is EtatSaisie.EnCours) brouillon.pour(it.entrainement.id) }
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

    /** Dernière écriture en base lancée : les suivantes l'attendent, pour s'exécuter dans l'ordre des appuis. */
    private var derniereEcriture: Job? = null

    /** Enregistrement de série en cours : un double appui ne doit pas enregistrer deux fois la même série. */
    private var enregistrement: Job? = null

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

    /** Écrit en base après l'écriture précédente ; un échec SQLite (stockage plein…) est signalé, sans plantage. */
    private fun ecrire(bloc: suspend () -> Unit): Job {
        val precedente = derniereEcriture
        return viewModelScope.launch {
            precedente?.join()
            try {
                bloc()
            } catch (e: SQLiteException) {
                afficherToast("Enregistrement impossible : stockage indisponible", erreur = true)
            }
        }.also { derniereEcriture = it }
    }

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
        ecrire { dao.modifierChrono(enCours.entrainement.id) { action(it, realtime) } }
    }

    // --- Séries ---

    /** « Nouvelle série » ou « Nouvel exercice » : valide, enregistre, relance le chrono, vide les champs. */
    override fun enregistrer(suite: Suite) {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        if (enregistrement?.isActive == true) return
        val serie = valider(enCours) ?: return
        enregistrement = ecrire {
            val enregistree = sauver(enCours, serie, suite)
            if (enregistree != null) {
                vider(suite)
                afficherToast("Série ${enregistree.ordre} enregistrée · ${serie.description}")
            } else if (serie.nom.isBlank()) {
                // Écran en retard sur la base : le nom était requis alors qu'il semblait verrouillé.
                _formulaire.update { it.copy(erreurNom = true) }
            }
        }
    }

    /** Série remplie et valide, ou `null` après avoir signalé les champs fautifs. */
    private fun valider(enCours: EtatSaisie.EnCours): SerieSaisie? {
        val nomManquant = !enCours.verrouille && nom.text.isBlank()
        val repetitions = lireRepetitions(repetitions.text.toString())
        _formulaire.update { it.copy(erreurNom = nomManquant, erreurRepetitions = repetitions == null) }
        if (nomManquant || repetitions == null) return null
        return SerieSaisie(nom.text.toString(), lirePoids(poids.text.toString()), repetitions)
    }

    private suspend fun sauver(enCours: EtatSaisie.EnCours, serie: SerieSaisie, suite: Suite): Serie? =
        dao.enregistrerSerie(
            entrainementId = enCours.entrainement.id,
            nomExercice = serie.nom,
            poidsKg = serie.poidsKg,
            repetitions = serie.repetitions,
            suite = suite,
            maintenant = horloge.maintenant(),
            realtime = horloge.realtime(),
        )

    private fun vider(suite: Suite) {
        poids.clearText()
        repetitions.clearText()
        if (suite == Suite.NouvelExercice) nom.clearText()
        // Après « Nouvelle série », le nom reste dans le champ le temps qu'il se verrouille : pas de suggestions.
        val choisi = if (suite == Suite.NouvelleSerie) nom.text.toString() else null
        _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, nomChoisi = choisi) }
    }

    private fun afficherToast(message: String, erreur: Boolean = false) {
        toastJob?.cancel()
        _formulaire.update { it.copy(toast = message, toastErreur = erreur) }
        toastJob = viewModelScope.launch {
            delay(DUREE_TOAST_MS)
            _formulaire.update { it.copy(toast = null) }
        }
    }

    // --- Fin de l'entraînement ---

    /** Ouvre le dialogue de fin, une fois les écritures en cours terminées (les champs ont pu être vidés). */
    override fun demanderFin() {
        viewModelScope.launch {
            derniereEcriture?.join()
            val enCours = etat.value as? EtatSaisie.EnCours ?: return@launch
            val repetitions = lireRepetitions(repetitions.text.toString())
            val remplie = (enCours.verrouille || nom.text.isNotBlank()) && repetitions != null
            val dialogue = if (remplie) {
                DialogueFin.AvecSerie(SerieSaisie(nom.text.toString(), lirePoids(poids.text.toString()), repetitions))
            } else {
                DialogueFin.Simple
            }
            _formulaire.update { it.copy(dialogue = dialogue) }
        }
    }

    override fun annulerFin() = _formulaire.update { it.copy(dialogue = null) }

    /**
     * Termine l'entraînement, après avoir enregistré la série affichée dans le dialogue 4a si [avecSerie].
     * L'écran se ferme ensuite, quand la base ne signale plus d'entraînement en cours.
     */
    override fun terminer(avecSerie: Boolean) {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        val dialogue = _formulaire.value.dialogue ?: return
        val serie = (dialogue as? DialogueFin.AvecSerie)?.serie?.takeIf { avecSerie }
        _formulaire.update { it.copy(dialogue = null) }
        ecrire {
            val id = enCours.entrainement.id
            if (serie != null) {
                dao.enregistrerSerieEtTerminer(id, serie.nom, serie.poidsKg, serie.repetitions, horloge.maintenant(), horloge.realtime())
            } else {
                dao.terminer(id, horloge.maintenant())
            }
            brouillon.vider()
        }
    }

    companion object {
        const val DUREE_TOAST_MS = 2_600L

        val Fabrique = viewModelFactory {
            initializer {
                val conteneur = (this[APPLICATION_KEY] as IronTapApplication).container
                SaisieViewModel(conteneur.base.entrainements(), conteneur.horloge, conteneur.brouillonSaisie)
            }
        }
    }
}
