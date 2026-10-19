package app.irontap.ui.detail

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateListOf
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
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.SerieAvecPaliers
import app.irontap.donnees.ValeursPalier
import app.irontap.logique.LecturePaliers
import app.irontap.logique.Suggestion
import app.irontap.logique.ajouterPoids
import app.irontap.logique.ajouterRepetitions
import app.irontap.logique.decrireSerie
import app.irontap.logique.formaterPoids
import app.irontap.logique.lirePaliers
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

/**
 * Feuille d'édition : [serie] `null` pour « Ajouter une série ». [tableau] : tableau des paliers (7·D), pour une
 * dégressive ou après « + Ajouter un palier » ; sinon écran 7 avec ses boutons +X.
 */
data class EditionSerie(
    val exercice: Exercice,
    val serie: SerieAvecPaliers?,
    val tableau: Boolean = false,
    /** Appui sur « — » d'un superset : la série manquante est ajoutée à ce tour, pas en dernier. */
    val tour: Int? = null,
)

/**
 * Feuille « Ajouter un tour » (3·S+ T1), ou « Modifier le tour N » ([existant]) : A et B ensemble, au clavier.
 * [indications] : valeurs du tour précédent, en placeholder ; [precedent] : son numéro.
 */
data class EditionTour(
    val a: Exercice,
    val b: Exercice,
    val tour: Int,
    val existant: Boolean,
    val series: Map<RoleSuperset, SerieAvecPaliers>,
    val indications: Map<RoleSuperset, ValeursPalier> = emptyMap(),
    val precedent: Int? = null,
) {
    fun exercice(role: RoleSuperset) = if (role == RoleSuperset.A) a else b
}

/** « Supprimer le tour N ? » (H) : les séries de A et de B de ce tour, s'il y en a. */
data class SuppressionTour(val a: Exercice, val b: Exercice, val tour: TourSuperset)

/** Champs d'un palier de la feuille d'édition. [cle] identifie la ligne (focus, erreurs) quand d'autres sont retirées. */
class ChampsPalier(val cle: Long, poids: String = "", repetitions: String = "") {
    val poids = TextFieldState(poids)
    val repetitions = TextFieldState(repetitions)

    /** Ni poids, ni répétitions : bloc ignoré par « Enregistrer le tour ». */
    val vide get() = poids.text.isBlank() && repetitions.text.isBlank()

    fun remplir(poids: String, repetitions: String) {
        this.poids.setTextAndPlaceCursorAtEnd(poids)
        this.repetitions.setTextAndPlaceCursorAtEnd(repetitions)
    }

    fun vider() = remplir("", "")
}

/** Série dont la suppression attend confirmation (6b). */
data class SuppressionSerie(val exercice: Exercice, val serie: SerieAvecPaliers) {
    val description get() = decrireSerie(serie.valeurs)
}

/** Ce que l'écran affiche par-dessus le détail, hors champs de saisie. */
data class Panneaux(
    val edition: EditionSerie? = null,
    val tour: EditionTour? = null,
    val suppressionTour: SuppressionTour? = null,
    /**
     * Paliers de l'édition dont les répétitions manquent : clé de la ligne → texte au moment de la validation.
     * L'erreur disparaît dès que ce texte change.
     */
    val paliersEnErreur: Map<Long, String> = emptyMap(),
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

    /** Paliers de la feuille d'édition, le principal d'abord (un seul pour une série simple). */
    val paliers = mutableStateListOf<ChampsPalier>()

    private var prochaineCle = 0L

    private fun champs(poids: String = "", repetitions: String = "") = ChampsPalier(prochaineCle++, poids, repetitions)

    /** Champs de la feuille « Ajouter un tour » : un palier pour A, un pour B. */
    val champsTour: Map<RoleSuperset, ChampsPalier> = RoleSuperset.entries.associateWith { champs() }

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
            snapshotFlow { (paliers + champsTour.values).associate { it.cle to it.repetitions.text.toString() } }.collect { textes ->
                _panneaux.update { panneaux ->
                    panneaux.copy(paliersEnErreur = panneaux.paliersEnErreur.filter { (cle, texte) -> textes[cle] == texte })
                }
            }
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

    // --- Édition d'une série (écrans 7 et 7·D) ---

    /**
     * Appui sur une série ([serie]) ou sur « Ajouter une série » (`null`) : un palier vide, ou les paliers de la
     * série ; une dégressive s'ouvre en tableau (7·D).
     */
    fun ouvrirEdition(exercice: Exercice, serie: SerieAvecPaliers?, tour: Int? = null) {
        paliers.clear()
        serie?.paliers?.forEach { paliers += champs(it.poidsKg?.let(::formaterPoids).orEmpty(), it.repetitions.toString()) }
        if (paliers.isEmpty()) paliers += champs()
        _panneaux.update { Panneaux(edition = EditionSerie(exercice, serie, tableau = paliers.size > 1, tour = tour)) }
    }

    /** Boutons +X de l'écran 7 (série simple : palier principal). */
    fun ajouterAuPoids(valeur: Int) = paliers.firstOrNull()?.poids?.let { it.setTextAndPlaceCursorAtEnd(ajouterPoids(it.text.toString(), valeur)) }

    fun ajouterAuxRepetitions(valeur: Int) =
        paliers.firstOrNull()?.repetitions?.let { it.setTextAndPlaceCursorAtEnd(ajouterRepetitions(it.text.toString(), valeur)) }

    /** « + Ajouter un palier » : une ligne vide en dernier, la feuille passe au tableau. Retourne sa clé (focus). */
    fun ajouterPalier(): Long {
        val ligne = champs()
        paliers += ligne
        _panneaux.update { it.copy(edition = it.edition?.copy(tableau = true)) }
        return ligne.cle
    }

    /** ✕ « Retirer ce palier » : jamais le palier principal. Sans palier dégressif, la série redevient simple. */
    fun retirerPalier(cle: Long) {
        val index = paliers.indexOfFirst { it.cle == cle }
        if (index > 0) paliers.removeAt(index)
    }

    /**
     * Enregistre tous les paliers (spec §4.5.1) : lignes entièrement vides ignorées, répétitions obligatoires
     * sur les autres, poids facultatif, comme en saisie (spec §4.3.5).
     */
    fun enregistrerEdition() {
        val edition = _panneaux.value.edition ?: return
        if (occupe) return
        val lignes = paliers.toList()
        when (val lecture = lirePaliers(lignes.map { it.poids.text.toString() to it.repetitions.text.toString() })) {
            is LecturePaliers.Erreurs -> _panneaux.update { panneaux ->
                panneaux.copy(paliersEnErreur = lecture.lignes.associate { lignes[it].cle to lignes[it].repetitions.text.toString() })
            }
            is LecturePaliers.Valide -> ecrire(apres = ::fermerEdition) {
                if (edition.serie == null) {
                    dao.ajouterSerie(edition.exercice.id, lecture.paliers, horloge.maintenant(), ordre = edition.tour)
                } else {
                    dao.modifierSerie(edition.serie.serie.id, lecture.paliers, horloge.maintenant())
                }
            }
        }
    }

    fun fermerEdition() = _panneaux.update { it.copy(edition = null, paliersEnErreur = emptyMap(), erreurEnregistrement = false) }

    // --- Tours d'un superset (maquettes H, 3·S+ T1) ---

    /**
     * « + Ajouter un tour » : champs vides ; en indication, la dernière série de chaque exercice (celle du tour
     * précédent, ou d'un tour antérieur s'il lui manque une série, comme la maquette T1).
     */
    fun ouvrirAjoutTour(bloc: BlocDetail.Superset) {
        val precedent = bloc.tours.lastOrNull()
        champsTour.values.forEach { it.vider() }
        val indications = buildMap {
            RoleSuperset.entries.forEach { role -> bloc.exercice(role).series.lastOrNull()?.valeurs?.firstOrNull()?.let { put(role, it) } }
        }
        val tour = (precedent?.numero ?: 0) + 1
        _panneaux.update { Panneaux(tour = EditionTour(bloc.a.exercice, bloc.b.exercice, tour, existant = false, series = emptyMap(), indications, precedent?.numero)) }
    }

    /** Appui sur le numéro d'un tour : « Modifier le tour N », palier principal de A et de B pré-remplis. */
    fun ouvrirModificationTour(bloc: BlocDetail.Superset, tour: TourSuperset) {
        val series = buildMap { RoleSuperset.entries.forEach { role -> tour.serie(role)?.let { put(role, it) } } }
        RoleSuperset.entries.forEach { role ->
            val principal = series[role]?.valeurs?.firstOrNull()
            champsTour.getValue(role).remplir(principal?.poidsKg?.let(::formaterPoids).orEmpty(), principal?.repetitions?.toString().orEmpty())
        }
        _panneaux.update { Panneaux(tour = EditionTour(bloc.a.exercice, bloc.b.exercice, tour.numero, existant = true, series = series)) }
    }

    /** Au moins un bloc rempli : « Enregistrer le tour » est actif. */
    fun tourEnregistrable() = champsTour.values.any { !it.vide }

    /**
     * « Enregistrer le tour » : un bloc vide est ignoré (la série de ce tour, s'il en a une, ne change pas) ; un bloc
     * rempli demande des répétitions. Le palier principal saisi remplace celui de la série, ses paliers dégressifs
     * sont gardés.
     */
    fun enregistrerTour() {
        val edition = _panneaux.value.tour ?: return
        if (occupe || !tourEnregistrable()) return
        val fautifs = champsTour.values.filter { !it.vide && lireRepetitions(it.repetitions.text.toString()) == null }
        if (fautifs.isNotEmpty()) {
            _panneaux.update { panneaux -> panneaux.copy(paliersEnErreur = fautifs.associate { it.cle to it.repetitions.text.toString() }) }
            return
        }
        val paliers = RoleSuperset.entries.associateWith { role ->
            val champs = champsTour.getValue(role)
            if (champs.vide) return@associateWith null
            val principal = ValeursPalier(lirePoids(champs.poids.text.toString()), lireRepetitions(champs.repetitions.text.toString())!!)
            listOf(principal) + edition.series[role]?.valeurs?.drop(1).orEmpty()
        }
        ecrire(apres = ::fermerTour) {
            // Superset défait entre-temps (série supprimée) : rien à écrire, la feuille se ferme.
            dao.ecrireTour(
                exerciceA = edition.a.id,
                exerciceB = edition.b.id,
                tour = edition.tour.takeIf { edition.existant },
                a = paliers[RoleSuperset.A],
                b = paliers[RoleSuperset.B],
                maintenant = horloge.maintenant(),
            )
        }
    }

    fun fermerTour() = _panneaux.update { it.copy(tour = null, paliersEnErreur = emptyMap(), erreurEnregistrement = false) }

    /** Balayage d'un tour, ou « Supprimer le tour » de sa feuille : choix A, B ou les deux, après confirmation. */
    fun demanderSuppressionTour(bloc: BlocDetail.Superset, tour: TourSuperset) =
        _panneaux.update { it.copy(suppressionTour = SuppressionTour(bloc.a.exercice, bloc.b.exercice, tour), erreurEnregistrement = false) }

    fun annulerSuppressionTour() = _panneaux.update { it.copy(suppressionTour = null, erreurEnregistrement = false) }

    /** Supprime la série de A, de B, ou les deux ([roles]) ; un tour devenu vide disparaît (cf. [EntrainementDao.supprimerSerie]). */
    fun confirmerSuppressionTour(roles: Set<RoleSuperset>) {
        val suppression = _panneaux.value.suppressionTour ?: return
        val ids = roles.mapNotNull { suppression.tour.serie(it)?.serie?.id }
        if (ids.isEmpty()) return
        // La ligne reste ouverte jusqu'à disparaître ; elle se referme si sa série A reste (seul B supprimé).
        val revelee = suppression.tour.a?.takeIf { RoleSuperset.A in roles }?.serie?.id
            ?: suppression.tour.b?.takeIf { suppression.tour.a == null }?.serie?.id
        _panneaux.update { panneaux ->
            // La feuille de ce tour se ferme avec lui.
            val tour = panneaux.tour?.takeUnless { it.existant && it.a.id == suppression.a.id && it.tour == suppression.tour.numero }
            panneaux.copy(suppressionTour = null, tour = tour, serieEnSuppression = revelee)
        }
        ecrire(echec = { it.copy(suppressionTour = suppression, serieEnSuppression = null) }) { dao.supprimerSeries(ids) }
    }

    // --- Suppression d'une série (6b) ---

    /** Balayage d'une série, ou « Supprimer » de la feuille d'édition : confirmation d'abord. */
    fun demanderSuppression(exercice: Exercice, serie: SerieAvecPaliers) =
        _panneaux.update { it.copy(suppression = SuppressionSerie(exercice, serie), erreurEnregistrement = false) }

    fun annulerSuppression() = _panneaux.update { it.copy(suppression = null, erreurEnregistrement = false) }

    /** Supprime la série ; les suivantes sont renumérotées et un exercice vide disparaît (spec §4.4). */
    fun confirmerSuppression() {
        val suppression = _panneaux.value.suppression ?: return
        _panneaux.update { panneaux ->
            // La feuille d'édition de cette série se ferme avec elle.
            val edition = panneaux.edition?.takeUnless { it.serie?.serie?.id == suppression.serie.serie.id }
            panneaux.copy(suppression = null, edition = edition, serieEnSuppression = suppression.serie.serie.id)
        }
        // En cas d'échec, le dialogue revient avec l'erreur.
        ecrire(echec = { it.copy(suppression = suppression, serieEnSuppression = null) }) {
            dao.supprimerSerie(suppression.serie.serie.id)
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
