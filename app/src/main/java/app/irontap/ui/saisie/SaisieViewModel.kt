package app.irontap.ui.saisie

import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.irontap.donnees.EntrainementEnCours
import app.irontap.donnees.PalierEnCours
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.SaisieOnglet
import app.irontap.donnees.SupersetEnCours
import app.irontap.donnees.TourEnregistre
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
import java.util.UUID

/** Ce que la base dit de l'entraînement en cours. */
sealed interface EtatSaisie {
    data object Chargement : EtatSaisie

    /** Plus d'entraînement en cours (terminé, ou jamais créé) : l'écran se ferme. */
    data object Aucun : EtatSaisie

    data class EnCours(
        val entrainement: Entrainement,
        val exercices: List<ExerciceAvecSeries>,
        val chrono: EtatChrono?,
        /** Paliers gardés par « Dégressive » pour la série en cours (spec §4.3.9), onglets d'un superset compris. */
        val paliersEnCours: List<PalierEnCours> = emptyList(),
        /** Superset en cours de saisie (spec §4.3.10), `null` hors superset. */
        val superset: SupersetEnCours? = null,
    ) : EtatSaisie {
        private val dernier = exercices.lastOrNull()

        /** Superset en cours, avec ses exercices et ses paliers gardés. */
        val etatSuperset = superset?.let { EtatSuperset(it, exercices, paliersEnCours) }

        /** Nom verrouillé : l'exercice courant a déjà une série et « Nouvel exercice » n'a pas été demandé. */
        val verrouille = dernier != null && !entrainement.nouvelExercice

        /** Nom de l'exercice courant quand il est verrouillé. */
        val nomVerrouille = if (verrouille) dernier?.exercice?.nom else null

        /** Exercices déjà ouverts, un superset comptant pour un seul : « Exercice N » de l'en-tête. */
        private fun blocs(exercices: List<ExerciceAvecSeries>) = exercices.map { it.exercice.supersetId ?: "#${it.exercice.id}" }.distinct().size

        val numeroExercice = when {
            superset != null -> blocs(exercices.filter { it.exercice.supersetId != superset.supersetId }) + 1
            verrouille -> blocs(exercices)
            else -> blocs(exercices) + 1
        }
        val numeroSerie = when {
            etatSuperset != null -> etatSuperset.tour
            verrouille -> dernier!!.series.size + 1
            else -> 1
        }

        /**
         * « Tirage vertical · Série 3 » (notification, accueil) ; après « Nouvel exercice », le nom n'est pas encore connu.
         * En superset : « Élévations latérales + Curl marteau · Série 2 ».
         */
        val ligne = if (etatSuperset != null) {
            val noms = RoleSuperset.entries.map { etatSuperset.nom(it).trim() }.filter { it.isNotEmpty() }
            "${noms.joinToString(" + ").ifEmpty { "Superset" }} · Série $numeroSerie"
        } else {
            "${nomVerrouille ?: "Exercice $numeroExercice"} · Série $numeroSerie"
        }

        /** Pastille « Superset » (S1) : l'exercice courant n'a encore ni série ni palier gardé. */
        val supersetPossible = superset == null && !verrouille && paliersEnCours.isEmpty()

        /** Valeurs des paliers déjà gardés hors superset, dans l'ordre. */
        val paliersPrecedents = paliersEnCours.filter { it.role == null }.map { it.valeurs }

        /** En-tête en mode dégressive (3·D A2, A3) : « 50 kg × 10 ↓ 30 kg × 8 ↓ … », `null` hors dégressive. */
        val chaineDegressive = if (paliersPrecedents.isEmpty()) null else decrireSerie(paliersPrecedents) + " ↓ …"

        companion object {
            fun de(enCours: EntrainementEnCours) =
                EnCours(enCours.entrainement, enCours.exercices, enCours.chrono, enCours.paliersEnCours, enCours.superset)
        }
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

    /** 4a en superset : A et B saisis, enregistrés ensemble. [description] : « A 8 kg × 12 · B 12 kg × 10 ». */
    data class SupersetComplet(val a: SaisieOnglet, val b: SaisieOnglet, val description: String) : DialogueFin

    /** S5 : un seul onglet du superset saisi ([role]) ; « Enregistrer A seul et terminer ». [description] : « 8 kg × 12 ». */
    data class SupersetIncomplet(val role: RoleSuperset, val saisie: SaisieOnglet, val description: String) : DialogueFin
}

/** Confirmation de « Nouvel exercice » quand rien n'est saisi (DECISIONS.md) : on passe sans rien enregistrer. */
sealed interface PassageExercice {
    /** Exercice verrouillé [nom], qui garde ses [series] séries ; la suivante n'est pas enregistrée. */
    data class Simple(val nom: String, val series: Int) : PassageExercice

    /** Superset aux deux onglets vides, après [tours] tours enregistrés : il se termine. */
    data class Superset(val tours: Int) : PassageExercice
}

/** État propre à l'écran, hors champs de saisie. */
data class Formulaire(
    val erreurNom: Boolean = false,
    val erreurRepetitions: Boolean = false,
    val dialogue: DialogueFin? = null,
    /** Dialogue « Passer à l'exercice suivant ? » / « Terminer le superset ? ». */
    val passage: PassageExercice? = null,
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
 * tuée (spec §7), sauf pendant un superset : ses deux onglets sont mémorisés en base à chaque bascule et
 * quand l'écran passe en arrière-plan (spec §4.3.10). `TextFieldState` plutôt qu'un StateFlow, pour ne
 * jamais perdre une frappe.
 */
class BrouillonSaisie {
    /** Exercice courant, ou onglet A d'un superset. */
    val principal = ChampsOnglet()

    /** Onglet B d'un superset. */
    val ongletB = ChampsOnglet()

    val nom get() = principal.nom
    val poids get() = principal.poids
    val repetitions get() = principal.repetitions

    /** Onglet affiché pendant un superset ; A hors superset. */
    var actif by mutableStateOf(RoleSuperset.A)

    fun champs(role: RoleSuperset) = if (role == RoleSuperset.A) principal else ongletB

    private var entrainementId: Long? = null

    /**
     * Associe le brouillon à l'entraînement en cours ; celui d'un autre entraînement est vidé. `true` si le
     * brouillon vient d'être (ré)associé : après un kill, c'est le moment de retrouver un superset mémorisé.
     */
    fun pour(id: Long): Boolean {
        if (entrainementId == id) return false
        vider()
        entrainementId = id
        return true
    }

    fun vider() {
        RoleSuperset.entries.forEach { viderOnglet(it, nom = true) }
        actif = RoleSuperset.A
    }

    fun viderOnglet(role: RoleSuperset, nom: Boolean) {
        val champs = champs(role)
        if (nom) champs.nom.clearText()
        champs.poids.clearText()
        champs.repetitions.clearText()
    }

    /** Superset mémorisé en base (appli tuée) : noms, valeurs saisies et onglet actif. */
    fun restaurer(superset: SupersetEnCours) {
        principal.nom.setTextAndPlaceCursorAtEnd(superset.nomA)
        principal.poids.setTextAndPlaceCursorAtEnd(superset.poidsA)
        principal.repetitions.setTextAndPlaceCursorAtEnd(superset.repetitionsA)
        ongletB.nom.setTextAndPlaceCursorAtEnd(superset.nomB)
        ongletB.poids.setTextAndPlaceCursorAtEnd(superset.poidsB)
        ongletB.repetitions.setTextAndPlaceCursorAtEnd(superset.repetitionsB)
        actif = superset.actif
    }

    /** État du superset à mémoriser : celui de la base, avec les noms, valeurs et onglet du brouillon. */
    fun instantane(superset: SupersetEnCours) = superset.copy(
        nomA = principal.nom.text.toString(),
        nomB = ongletB.nom.text.toString(),
        actif = actif,
        poidsA = principal.poids.text.toString(),
        repetitionsA = principal.repetitions.text.toString(),
        poidsB = ongletB.poids.text.toString(),
        repetitionsB = ongletB.repetitions.text.toString(),
    )
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
                EtatSaisie.EnCours.de(enCours)
            }
        }
        .onEach { etat ->
            if (etat is EtatSaisie.EnCours && brouillon.pour(etat.entrainement.id)) {
                // Brouillon neuf (appli tuée) : un superset reprend là où il a été mémorisé.
                etat.superset?.let(brouillon::restaurer)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatSaisie.Chargement)

    /** Onglet affiché pendant un superset (A hors superset). */
    val ongletActif: RoleSuperset get() = brouillon.actif

    /** Champs de l'onglet [role] d'un superset ; A : ceux de l'exercice courant. */
    fun champs(role: RoleSuperset) = brouillon.champs(role)

    /** Champs affichés : ceux de l'exercice courant, ou de l'onglet actif d'un superset. */
    private val actifs get() = brouillon.champs(brouillon.actif)

    private val texteNom = snapshotFlow { actifs.nom.text.toString() }

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
        snapshotFlow { brouillon.actif },
    ) { etat, saisie, erreurNom, noms, actif ->
        val enCours = etat as? EtatSaisie.EnCours
        // En superset (pastille « Historique A/B ») : l'exercice de l'onglet actif.
        val verrouille = enCours?.etatSuperset?.let { it.nomVerrouille(actif) } ?: enCours?.nomVerrouille.takeIf { enCours?.superset == null }
        val nom = verrouille ?: saisie.takeUnless { enCours == null || erreurNom }
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
        // Une bascule d'onglet ne compte pas : la validation bascule elle-même sur l'onglet fautif pour y montrer l'erreur.
        effacerSiModifie({ it.nom }) { it.copy(erreurNom = false) }
        effacerSiModifie({ it.repetitions }) { it.copy(erreurRepetitions = false) }
    }

    /** Applique [effacer] quand le champ [champ] de l'onglet affiché est modifié, pas quand l'onglet change. */
    private fun effacerSiModifie(champ: (ChampsOnglet) -> TextFieldState, effacer: (Formulaire) -> Formulaire) {
        viewModelScope.launch {
            var ongletPrecedent: RoleSuperset? = null
            snapshotFlow { brouillon.actif to champ(actifs).text.toString() }.collect { (onglet, _) ->
                if (onglet == ongletPrecedent) _formulaire.update(effacer)
                ongletPrecedent = onglet
            }
        }
    }

    override fun choisir(suggestion: Suggestion) {
        actifs.nom.setTextAndPlaceCursorAtEnd(suggestion.nom)
        _formulaire.update { it.copy(nomChoisi = suggestion.nom) }
    }

    override fun ajouterAuPoids(valeur: Int) = actifs.poids.let { it.setTextAndPlaceCursorAtEnd(ajouterPoids(it.text.toString(), valeur)) }

    override fun ajouterAuxRepetitions(valeur: Int) =
        actifs.repetitions.let { it.setTextAndPlaceCursorAtEnd(ajouterRepetitions(it.text.toString(), valeur)) }

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

    /** Attend les écritures déjà lancées (elles s'enchaînent : la dernière suffit), pendant lesquelles les appuis sont ignorés. */
    @VisibleForTesting
    internal suspend fun attendreEcritures() {
        derniereEcriture?.join()
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
        enCours.etatSuperset?.let { return enregistrerTour(enCours, it, fin = suite == Suite.NouvelExercice) }
        // « Nouvel exercice » sans rien de saisi (ni poids, ni répétitions, ni palier gardé) sur un exercice verrouillé :
        // rien à enregistrer, on confirme qu'il faut passer au suivant. Un champ rempli garde la validation normale.
        if (suite == Suite.NouvelExercice && enCours.verrouille && enCours.paliersPrecedents.isEmpty() && brouillon.principal.vide) {
            val courant = enCours.exercices.last()
            _formulaire.update {
                it.copy(erreurNom = false, erreurRepetitions = false, passage = PassageExercice.Simple(courant.exercice.nom, courant.series.size))
            }
            return
        }
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
        enCours.etatSuperset?.let { return degressiveSuperset(enCours, it) }
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

    // --- Superset (spec §4.3.10) ---

    /** Pastille « Superset » : l'exercice courant devient l'onglet A, avec ce qui est déjà saisi ; B est vide. */
    override fun commencerSuperset() {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        if (!enCours.supersetPossible || enregistrement?.isActive == true) return
        enregistrement = ecrire {
            val superset = dao.commencerSuperset(enCours.entrainement.id, nom.text.toString(), UUID.randomUUID().toString())
            if (superset != null) {
                brouillon.viderOnglet(RoleSuperset.B, nom = true)
                brouillon.actif = RoleSuperset.A
                _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, nomChoisi = null) }
                dao.memoriserSuperset(brouillon.instantane(superset))
            }
        }
    }

    /** Appui sur l'onglet inactif : ses valeurs déjà saisies sont réaffichées ; le superset est mémorisé. */
    override fun basculer(role: RoleSuperset) {
        val superset = (etat.value as? EtatSaisie.EnCours)?.superset ?: return
        // Pendant un enregistrement, qui vide les onglets et revient sur A.
        if (brouillon.actif == role || enregistrement?.isActive == true) return
        brouillon.actif = role
        _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, nomChoisi = null) }
        memoriser(superset)
    }

    /** Écran en arrière-plan : le superset est mémorisé, l'appli peut être tuée sans le perdre. */
    override fun memoriserSaisie() {
        (etat.value as? EtatSaisie.EnCours)?.superset?.let(::memoriser)
    }

    /** L'instantané est pris après les écritures en attente : un tour ou un palier tout juste vidé n'est pas rétabli. */
    private fun memoriser(superset: SupersetEnCours) {
        ecrire { dao.memoriserSuperset(brouillon.instantane(superset)) }
    }

    /**
     * Valeurs de l'onglet [role] prêtes à enregistrer, ou `null` après avoir basculé sur cet onglet et signalé
     * le nom ou les répétitions manquants (rien n'est enregistré).
     */
    private fun validerOnglet(superset: EtatSuperset, role: RoleSuperset): SaisieOnglet? {
        val champs = brouillon.champs(role)
        val nomManquant = !superset.verrouille(role) && champs.nom.text.isBlank()
        val repetitions = lireRepetitions(champs.repetitions.text.toString())
        if (nomManquant || repetitions == null) {
            brouillon.actif = role
            _formulaire.update { it.copy(erreurNom = nomManquant, erreurRepetitions = repetitions == null, nomChoisi = null) }
            return null
        }
        return SaisieOnglet(champs.nom.text.toString(), lirePoids(champs.poids.text.toString()), repetitions)
    }

    /**
     * « Nouvelle série » : A et B ensemble, dans un même tour ; « Nouvel exercice » ([fin]) : les onglets non vides
     * (ni poids, ni répétitions, ni palier gardé : ignoré), puis le superset se termine. Les deux onglets vides : on
     * confirme d'abord la fin du superset.
     */
    private fun enregistrerTour(enCours: EtatSaisie.EnCours, superset: EtatSuperset, fin: Boolean) {
        val roles = RoleSuperset.entries.filter { role -> !fin || !brouillon.champs(role).vide || superset.paliers(role).isNotEmpty() }
        if (roles.isEmpty()) {
            _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, passage = PassageExercice.Superset(superset.tour - 1)) }
            return
        }
        val saisies = HashMap<RoleSuperset, SaisieOnglet>()
        for (role in roles) saisies[role] = validerOnglet(superset, role) ?: return
        enregistrement = ecrireTour(enCours, saisies, fin)
    }

    /** Écrit le tour des onglets [saisies], vide les onglets (et les noms si [fin]) et revient sur A. */
    private fun ecrireTour(enCours: EtatSaisie.EnCours, saisies: Map<RoleSuperset, SaisieOnglet>, fin: Boolean) = ecrire {
        val tour = dao.enregistrerTour(
            entrainementId = enCours.entrainement.id,
            a = saisies[RoleSuperset.A],
            b = saisies[RoleSuperset.B],
            fin = fin,
            maintenant = horloge.maintenant(),
            realtime = horloge.realtime(),
        ) ?: return@ecrire
        RoleSuperset.entries.forEach { brouillon.viderOnglet(it, nom = fin) }
        brouillon.actif = RoleSuperset.A
        _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, nomChoisi = null) }
        // Sans série (fin confirmée avec les deux onglets vides), pas de toast : les onglets disparaissent.
        if (tour.series.isNotEmpty()) afficherToast(messageTour(tour))
    }

    /** « Série 2 enregistrée · A 8 kg × 12 · B 12 kg × 10 », ou « … · A seul · 8 kg × 12 ». */
    private fun messageTour(tour: TourEnregistre): String {
        val series = tour.series
        return if (series.size == 1) {
            series.entries.single().let { (role, serie) -> "Série ${tour.tour} enregistrée · $role seul · ${decrireSerie(serie.valeurs)}" }
        } else {
            "Série ${tour.tour} enregistrée · " + series.entries.joinToString(" · ") { (role, serie) -> "$role ${decrireSerie(serie.valeurs)}" }
        }
    }

    // --- Passer à l'exercice suivant sans rien enregistrer ---

    /**
     * Confirmation de « Nouvel exercice » sans rien de saisi : l'exercice courant (ou le superset) est laissé tel quel,
     * le suivant sera nouveau. Rien n'est enregistré, le chrono n'est pas touché.
     */
    override fun confirmerPassage() {
        val passage = _formulaire.value.passage ?: return
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        _formulaire.update { it.copy(passage = null) }
        if (enregistrement?.isActive == true) return
        enregistrement = when (passage) {
            is PassageExercice.Simple -> ecrire {
                // Refusé si un palier vient d'être gardé : le prochain appui fera la validation normale.
                if (dao.passerExercice(enCours.entrainement.id)) vider(Suite.NouvelExercice)
            }
            is PassageExercice.Superset -> ecrireTour(enCours, emptyMap(), fin = true)
        }
    }

    override fun annulerPassage() = _formulaire.update { it.copy(passage = null) }

    /** « Dégressive » sur l'onglet actif : le palier est gardé pour cet onglet, dont le nom se verrouille. */
    private fun degressiveSuperset(enCours: EtatSaisie.EnCours, superset: EtatSuperset) {
        val role = brouillon.actif
        val saisie = validerOnglet(superset, role) ?: return
        val repetitions = checkNotNull(saisie.repetitions)
        enregistrement = ecrire {
            val garde = dao.ajouterPalierEnCours(enCours.entrainement.id, saisie.nom, saisie.poidsKg, repetitions, role)
            if (garde != null) {
                brouillon.viderOnglet(role, nom = false)
                _formulaire.update { it.copy(erreurNom = false, erreurRepetitions = false, nomChoisi = brouillon.champs(role).nom.text.toString()) }
                enCours.superset?.let { dao.memoriserSuperset(brouillon.instantane(it)) }
            }
        }
    }

    // --- Historique de l'exercice ---

    /** Ouvre la popup 3g sur l'exercice verrouillé, ou sur le nom saisi. Lecture seule : la saisie n'est pas touchée. */
    override fun ouvrirHistorique() {
        val enCours = etat.value as? EtatSaisie.EnCours ?: return
        if (lectureHistorique?.isActive == true || _formulaire.value.historique != null) return
        val superset = enCours.etatSuperset
        val nomExercice = if (superset != null) {
            superset.nomVerrouille(brouillon.actif) ?: actifs.nom.text.toString()
        } else {
            enCours.nomVerrouille ?: nom.text.toString()
        }
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
            val enCours = EtatSaisie.EnCours.de(lu)
            enCours.etatSuperset?.let { superset ->
                _formulaire.update { it.copy(dialogue = dialogueFinSuperset(superset)) }
                return@launch
            }
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

    /**
     * Fin pendant un superset (S5), selon les onglets saisis (répétitions valides, ou paliers gardés, et un nom) :
     * les deux (4a), un seul (série incomplète), aucun (4b).
     */
    private fun dialogueFinSuperset(superset: EtatSuperset): DialogueFin {
        val saisis = RoleSuperset.entries.mapNotNull { role ->
            val champs = brouillon.champs(role)
            val courant = lireRepetitions(champs.repetitions.text.toString())?.let { ValeursPalier(lirePoids(champs.poids.text.toString()), it) }
            val nomPresent = superset.verrouille(role) || champs.nom.text.isNotBlank()
            if (!nomPresent || (courant == null && superset.paliers(role).isEmpty())) return@mapNotNull null
            Triple(role, SaisieOnglet(champs.nom.text.toString(), courant?.poidsKg, courant?.repetitions), decrireSerie(superset.paliers(role) + listOfNotNull(courant)))
        }
        return when (saisis.size) {
            2 -> DialogueFin.SupersetComplet(saisis[0].second, saisis[1].second, saisis.joinToString(" · ") { (role, _, texte) -> "$role $texte" })
            1 -> saisis.single().let { (role, saisie, texte) -> DialogueFin.SupersetIncomplet(role, saisie, texte) }
            else -> DialogueFin.Simple
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
        _formulaire.update { it.copy(dialogue = null) }
        ecrire {
            val id = enCours.entrainement.id
            when {
                !avecSerie || dialogue == DialogueFin.Simple -> dao.terminer(id, horloge.maintenant())
                dialogue is DialogueFin.AvecSerie -> dialogue.serie.let { serie ->
                    val courant = serie.courant
                    dao.enregistrerSerieEtTerminer(id, serie.nom, courant?.poidsKg, courant?.repetitions, horloge.maintenant(), horloge.realtime())
                }
                dialogue is DialogueFin.SupersetComplet ->
                    dao.enregistrerTourEtTerminer(id, dialogue.a, dialogue.b, horloge.maintenant(), horloge.realtime())
                dialogue is DialogueFin.SupersetIncomplet -> dao.enregistrerTourEtTerminer(
                    id,
                    a = dialogue.saisie.takeIf { dialogue.role == RoleSuperset.A },
                    b = dialogue.saisie.takeIf { dialogue.role == RoleSuperset.B },
                    maintenant = horloge.maintenant(),
                    realtime = horloge.realtime(),
                )
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
