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
import app.irontap.donnees.PalierEnCours
import app.irontap.donnees.SerieAvecPaliers
import app.irontap.donnees.ValeursPalier
import app.irontap.logique.HistoriqueExercice
import app.irontap.logique.Suggestion
import app.irontap.logique.ajouterPoids
import app.irontap.logique.ajouterRepetitions
import app.irontap.logique.decrireSerie
import app.irontap.logique.historiqueExercice
import app.irontap.logique.lirePoids
import app.irontap.logique.lireRepetitions
import app.irontap.logique.normaliserNom
import app.irontap.logique.suggerer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
        /** Paliers gardés par « Dégressive » pour la série en cours (spec §4.3.9). */
        val paliersEnCours: List<PalierEnCours> = emptyList(),
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

        /** Valeurs des paliers déjà gardés, dans l'ordre. */
        val paliersPrecedents = paliersEnCours.map { it.valeurs }

        /** En-tête en mode dégressive (3·D A2, A3) : « 50 kg × 10 ↓ 30 kg × 8 ↓ … », `null` hors dégressive. */
        val chaineDegressive = if (paliersEnCours.isEmpty()) null else decrireSerie(paliersPrecedents) + " ↓ …"
    }
}

/**
 * Série prête à être enregistrée : paliers gardés par « Dégressive » ([precedents]), puis palier courant
 * ([courant], absent quand le dialogue 4a ne retient que les paliers gardés). [nom] est ignoré si le nom
 * est verrouillé.
 */
data class SerieSaisie(val nom: String, val precedents: List<ValeursPalier>, val courant: ValeursPalier?) {
    val description get() = decrireSerie(precedents + listOfNotNull(courant))
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
    /** Popup « Historique de l'exercice » ouverte (3g, spec §4.3.8). */
    val historique: HistoriqueExercice? = null,
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
            if (enCours == null) {
                EtatSaisie.Aucun
            } else {
                EtatSaisie.EnCours(enCours.entrainement, enCours.exercices, enCours.chrono, enCours.paliersEnCours)
            }
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

    /** Clés des noms d'exercices des autres entraînements (cf. [normaliserNom]). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val nomsAutresEntrainements: Flow<Set<String>> = etat
        .map { (it as? EtatSaisie.EnCours)?.entrainement?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptySet()) else dao.observerNomsExercicesHors(id).map { noms -> noms.mapTo(HashSet(), ::normaliserNom) }
        }

    /**
     * Bouton « Historique » (3f, spec §4.3.8) : le nom, verrouillé ou saisi, correspond à un exercice d'un autre
     * entraînement. Masqué si le champ est vide ou en erreur.
     */
    val historiqueDisponible: StateFlow<Boolean> = combine(
        etat,
        texteNom,
        _formulaire.map { it.erreurNom }.distinctUntilChanged(),
        nomsAutresEntrainements,
    ) { etat, saisie, erreurNom, noms ->
        val enCours = etat as? EtatSaisie.EnCours
        val nom = enCours?.nomVerrouille ?: saisie.takeUnless { enCours == null || erreurNom }
        nom != null && normaliserNom(nom).let { it.isNotEmpty() && it in noms }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** `elapsedRealtime` courant, pour l'affichage du chrono. */
    fun realtime(): Long = horloge.realtime()

    /** Dernière écriture en base lancée : les suivantes l'attendent, pour s'exécuter dans l'ordre des appuis. */
    private var derniereEcriture: Job? = null

    /** Enregistrement de série ou de palier en cours : un double appui ne doit pas enregistrer deux fois la même série. */
    private var enregistrement: Job? = null

    private var toastJob: Job? = null

    /** Lecture de l'historique en cours : un double appui n'ouvre pas deux fois la popup. */
    private var lectureHistorique: Job? = null

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
                // Paliers lus en base : ceux d'une dégressive commencée avant un retour à l'accueil compris.
                afficherToast("Série ${enregistree.serie.ordre} enregistrée · ${decrireSerie(enregistree.valeurs)}")
            } else if (serie.nom.isBlank()) {
                // Écran en retard sur la base : le nom était requis alors qu'il semblait verrouillé.
                _formulaire.update { it.copy(erreurNom = true) }
            }
        }
    }

    /**
     * « Dégressive » (spec §4.3.9) : même validation que « Nouvelle série ». Le palier est gardé pour la série
     * en cours, poids et répétitions sont vidés, le nom se verrouille ; ni le chrono ni le numéro de série ne changent.
     */
    override fun degressive() {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        if (enregistrement?.isActive == true) return
        val serie = valider(enCours) ?: return
        val palier = checkNotNull(serie.courant)
        enregistrement = ecrire {
            val garde = dao.ajouterPalierEnCours(enCours.entrainement.id, serie.nom, palier.poidsKg, palier.repetitions)
            if (garde != null) {
                vider(Suite.NouvelleSerie)
            } else if (serie.nom.isBlank()) {
                _formulaire.update { it.copy(erreurNom = true) }
            }
        }
    }

    /** Série remplie et valide (palier courant compris), ou `null` après avoir signalé les champs fautifs. */
    private fun valider(enCours: EtatSaisie.EnCours): SerieSaisie? {
        val nomManquant = !enCours.verrouille && nom.text.isBlank()
        val repetitions = lireRepetitions(repetitions.text.toString())
        _formulaire.update { it.copy(erreurNom = nomManquant, erreurRepetitions = repetitions == null) }
        if (nomManquant || repetitions == null) return null
        return SerieSaisie(nom.text.toString(), enCours.paliersPrecedents, ValeursPalier(lirePoids(poids.text.toString()), repetitions))
    }

    private suspend fun sauver(enCours: EtatSaisie.EnCours, serie: SerieSaisie, suite: Suite): SerieAvecPaliers? =
        dao.enregistrerSerie(
            entrainementId = enCours.entrainement.id,
            nomExercice = serie.nom,
            poidsKg = serie.courant?.poidsKg,
            repetitions = serie.courant?.repetitions,
            suite = suite,
            maintenant = horloge.maintenant(),
            realtime = horloge.realtime(),
        )

    /** Champs vidés après une série ([suite]) ou un palier (comme « Nouvelle série »). */
    private fun vider(suite: Suite) {
        poids.clearText()
        repetitions.clearText()
        if (suite == Suite.NouvelExercice) nom.clearText()
        // Après « Nouvelle série » ou « Dégressive », le nom reste dans le champ le temps qu'il se verrouille : pas de suggestions.
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

    // --- Historique de l'exercice ---

    /** Ouvre la popup 3g sur l'exercice verrouillé, ou sur le nom saisi. Lecture seule : la saisie n'est pas touchée. */
    override fun ouvrirHistorique() {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        if (lectureHistorique?.isActive == true || _formulaire.value.historique != null) return
        val nomExercice = enCours.nomVerrouille ?: nom.text.toString()
        lectureHistorique = viewModelScope.launch {
            val historique = try {
                historiqueExercice(dao.lireHistoriqueExercice(enCours.entrainement.id, nomExercice))
            } catch (e: SQLiteException) {
                afficherToast("Historique indisponible : stockage illisible", erreur = true)
                return@launch
            }
            _formulaire.update { it.copy(historique = historique) }
        }
    }

    override fun fermerHistorique() = _formulaire.update { it.copy(historique = null) }

    // --- Fin de l'entraînement ---

    /**
     * Ouvre le dialogue de fin, une fois les écritures en cours terminées (les champs ont pu être vidés). L'état
     * est relu en base : celui de l'écran peut ne pas encore montrer un palier que « Dégressive » vient de
     * garder, et 4b le ferait abandonner sans le proposer.
     */
    override fun demanderFin() {
        viewModelScope.launch {
            derniereEcriture?.join()
            val lu = try {
                dao.lireEnCoursComplet()
            } catch (e: SQLiteException) {
                afficherToast("Lecture impossible : stockage indisponible", erreur = true)
                return@launch
            } ?: return@launch
            val enCours = EtatSaisie.EnCours(lu.entrainement, lu.exercices, lu.chrono, lu.paliersEnCours)
            val courant = lireRepetitions(repetitions.text.toString())?.let { ValeursPalier(lirePoids(poids.text.toString()), it) }
            // Une dégressive en attente fait proposer sa série (spec §4.3.9), avec le palier courant s'il est valide.
            val remplie = (enCours.verrouille || nom.text.isNotBlank()) && (courant != null || enCours.paliersEnCours.isNotEmpty())
            val dialogue = if (remplie) {
                DialogueFin.AvecSerie(SerieSaisie(nom.text.toString(), enCours.paliersPrecedents, courant))
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
                val courant = serie.courant
                dao.enregistrerSerieEtTerminer(id, serie.nom, courant?.poidsKg, courant?.repetitions, horloge.maintenant(), horloge.realtime())
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
