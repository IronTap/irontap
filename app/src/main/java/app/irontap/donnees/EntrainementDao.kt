package app.irontap.donnees

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.irontap.logique.EntrainementImporte
import app.irontap.logique.ecarterDoublons
import app.irontap.logique.normaliserNom
import app.irontap.logique.versImport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Accès aux données. Les opérations qui touchent plusieurs lignes portent les règles de la spec
 * (numérotation, suppressions en chaîne, redémarrage du chrono) et s'exécutent en transaction.
 * Les sources de temps sont passées en paramètre (cf. [Horloge]).
 */
@Dao
abstract class EntrainementDao {

    /** Ce qui suit l'enregistrement d'une série (spec §4.3.5). */
    enum class Suite { NouvelleSerie, NouvelExercice }

    // --- Lectures ---

    @Query("SELECT * FROM entrainement WHERE termine_le IS NULL LIMIT 1")
    abstract fun observerEnCours(): Flow<Entrainement?>

    /**
     * Entraînement en cours avec ses exercices, séries et chrono, lus dans une même transaction : une écriture
     * ne produit jamais d'état intermédiaire mêlant l'ancien entraînement et les nouvelles séries.
     */
    fun observerEnCoursComplet(): Flow<EntrainementEnCours?> = observerEnCoursCompletBrut().map { it?.trie() }

    /** Entraînements terminés, du plus récent au plus ancien (accueil). */
    @Query("SELECT * FROM entrainement WHERE termine_le IS NOT NULL ORDER BY date DESC, cree_le DESC")
    abstract fun observerHistorique(): Flow<List<Entrainement>>

    @Query("SELECT * FROM entrainement WHERE id = :id")
    abstract fun observer(id: Long): Flow<Entrainement?>

    fun observerExercices(entrainementId: Long): Flow<List<ExerciceAvecSeries>> =
        observerExercicesBruts(entrainementId).map { it.trie() }

    @Query("SELECT * FROM etat_chrono WHERE entrainement_id = :entrainementId")
    abstract fun observerChrono(entrainementId: Long): Flow<EtatChrono?>

    /**
     * Noms d'exercices déjà saisis, du plus récemment utilisé au plus ancien (autocomplétion).
     * Distincts à la casse près seulement : le dédoublonnage sans casse ni accents se fait à l'affichage.
     */
    @Query(
        """
        SELECT e.nom FROM exercice e JOIN entrainement t ON t.id = e.entrainement_id
        GROUP BY e.nom ORDER BY MAX(t.date) DESC, MAX(e.id) DESC
        """,
    )
    abstract fun observerNomsExercices(): Flow<List<String>>

    /** Noms d'exercices des autres entraînements que [entrainementId] (visibilité du bouton Historique, spec §4.3.8). */
    @Query("SELECT DISTINCT nom FROM exercice WHERE entrainement_id != :entrainementId")
    abstract fun observerNomsExercicesHors(entrainementId: Long): Flow<List<String>>

    /**
     * Séries de l'exercice [nom] (casse, accents et espaces de bord ignorés) dans les autres entraînements que
     * [entrainementId] : de la séance la plus récente à la plus ancienne, puis exercices et séries dans l'ordre.
     */
    @Transaction
    open suspend fun lireHistoriqueExercice(entrainementId: Long, nom: String): List<SerieHistorique> {
        val cle = normaliserNom(nom)
        if (cle.isEmpty()) return emptyList()
        // SQLite ne sait pas retirer les accents : on retient ici les graphies enregistrées qui correspondent.
        val noms = lireNomsExercicesHors(entrainementId).filter { normaliserNom(it) == cle }
        return if (noms.isEmpty()) emptyList() else lireSeriesHistorique(entrainementId, noms)
    }

    suspend fun lireComplet(id: Long): EntrainementComplet? = lireCompletBrut(id)?.trie()

    /** Un entraînement, ses exercices et ses séries, lus ensemble (détail). `null` une fois supprimé. */
    fun observerComplet(id: Long): Flow<EntrainementComplet?> = observerCompletBrut(id).map { it?.trie() }

    /** Tout l'historique terminé, dans l'ordre de l'accueil, lu en une transaction (export). */
    fun observerHistoriqueComplet(): Flow<List<EntrainementComplet>> = observerHistoriqueCompletBrut().map { liste -> liste.map { it.trie() } }

    @Query("SELECT * FROM entrainement WHERE termine_le IS NULL LIMIT 1")
    abstract suspend fun lireEnCours(): Entrainement?

    /** Lecture ponctuelle de [observerEnCoursComplet] (notification du chrono). */
    suspend fun lireEnCoursComplet(): EntrainementEnCours? = lireEnCoursCompletBrut()?.trie()

    @Query("SELECT * FROM etat_chrono WHERE entrainement_id = :entrainementId")
    abstract suspend fun lireChrono(entrainementId: Long): EtatChrono?

    // --- Entraînements ---

    /** Crée l'entraînement et son chrono arrêté. Retourne `null` sans rien créer s'il en existe déjà un en cours. */
    @Transaction
    open suspend fun creerEntrainement(date: LocalDate, libelle: String?, maintenant: Instant): Long? {
        if (lireEnCours() != null) return null
        val id = inserer(Entrainement(date = date, libelle = libelle.nettoye(), creeLe = maintenant))
        enregistrer(EtatChrono(entrainementId = id))
        return id
    }

    suspend fun modifierEntrainement(id: Long, date: LocalDate, libelle: String?) =
        modifierEntrainementBrut(id, date, libelle.nettoye())

    /**
     * Termine l'entraînement (spec §4.3.6) : son chrono est supprimé et, s'il n'a aucune série,
     * l'entraînement lui-même. Une dégressive ou un superset en attente est abandonné, avec l'exercice que le
     * premier palier d'une dégressive avait ouvert si aucune série n'y a été enregistrée ; un exercice resté
     * seul de son superset redevient ordinaire.
     */
    @Transaction
    open suspend fun terminer(id: Long, maintenant: Instant) {
        supprimerSupersetEnCours(id)
        supprimerPaliersEnCours(id)
        supprimerExercicesSansSerie(id)
        dissoudreSupersetsIncomplets(id)
        if (compterSeriesEntrainement(id) == 0) {
            supprimerEntrainement(id)
        } else {
            marquerTermine(id, maintenant)
            supprimerChrono(id)
        }
    }

    /** Supprime l'entraînement, ses exercices, ses séries et son chrono (cascade). */
    @Query("DELETE FROM entrainement WHERE id = :id")
    abstract suspend fun supprimerEntrainement(id: Long)

    // --- Saisie ---

    /**
     * Enregistre une série de l'entraînement en cours et relance le chrono. Ses paliers sont ceux gardés par
     * « Dégressive » (spec §4.3.9), puis le palier courant [poidsKg] × [repetitions] ; [repetitions] `null` :
     * pas de palier courant (dialogue 4a, dégressive en attente et champs vides).
     * La série ouvre un nouvel exercice nommé [nomExercice] si c'est la première de l'entraînement
     * ou si la précédente a été suivie de « Nouvel exercice » ; sinon [nomExercice] est ignoré
     * (nom verrouillé). Retourne la série enregistrée, avec son numéro, ou `null` sans rien enregistrer
     * s'il n'y a aucun palier, si un nouvel exercice doit être ouvert et que [nomExercice] est vide, ou si
     * l'entraînement n'est plus en cours (terminé ou supprimé entre-temps).
     */
    @Transaction
    open suspend fun enregistrerSerie(
        entrainementId: Long,
        nomExercice: String,
        poidsKg: Double?,
        repetitions: Int?,
        suite: Suite,
        maintenant: Instant,
        realtime: Long,
    ): SerieAvecPaliers? {
        val entrainement = lire(entrainementId)?.takeIf { it.termineLe == null } ?: return null
        val paliers = paliersGardes(entrainementId, role = null) + listOfNotNull(repetitions?.let { ValeursPalier(poidsKg, it) })
        if (paliers.isEmpty()) return null
        val exerciceId = exerciceCourant(entrainement, nomExercice) ?: return null
        val serie = ajouterSerie(exerciceId, paliers, maintenant)
        supprimerPaliersEnCours(entrainementId, role = null)
        modifierNouvelExercice(entrainementId, suite == Suite.NouvelExercice)
        enregistrer((lireChrono(entrainementId) ?: EtatChrono(entrainementId)).relance(realtime))
        return serie
    }

    /**
     * « Dégressive » (spec §4.3.9) : garde le palier [poidsKg] × [repetitions] pour la série en cours, sans
     * toucher au chrono ni au numéro de série. Le nom se verrouille : un nouvel exercice est ouvert dès ce
     * premier palier, comme le ferait [enregistrerSerie]. Dans un superset ([role]), le palier est gardé pour
     * cet onglet et son nom mémorisé ; l'exercice ne sera créé qu'avec le tour, après A s'il s'agit de B.
     * Retourne le palier gardé, ou `null` sans rien enregistrer si un nouvel exercice doit être ouvert et que
     * [nomExercice] est vide, si [role] est donné sans superset en cours, ou si l'entraînement n'est plus en cours.
     */
    @Transaction
    open suspend fun ajouterPalierEnCours(
        entrainementId: Long,
        nomExercice: String,
        poidsKg: Double?,
        repetitions: Int,
        role: RoleSuperset? = null,
    ): PalierEnCours? {
        verifier(ValeursPalier(poidsKg, repetitions))
        val entrainement = lire(entrainementId)?.takeIf { it.termineLe == null } ?: return null
        if (role == null) {
            exerciceCourant(entrainement, nomExercice) ?: return null
        } else {
            val superset = lireSupersetEnCours(entrainementId) ?: return null
            val existant = lireExercicesSuperset(superset.supersetId).firstOrNull { it.supersetRole == role }
            if (existant == null) {
                val nom = nomExercice.trim().ifEmpty { superset.nom(role).trim() }.ifEmpty { return null }
                memoriserSuperset(if (role == RoleSuperset.A) superset.copy(nomA = nom) else superset.copy(nomB = nom))
            }
        }
        val palier = PalierEnCours(
            entrainementId = entrainementId,
            ordre = compterPaliersEnCours(entrainementId, role) + 1,
            poidsKg = poidsKg,
            repetitions = repetitions,
            role = role,
        )
        return palier.copy(id = inserer(palier))
    }

    /** Paliers gardés par « Dégressive » pour la série en cours ([role] : onglet d'un superset), dans l'ordre. */
    private suspend fun paliersGardes(entrainementId: Long, role: RoleSuperset?): List<ValeursPalier> =
        lirePaliersEnCours(entrainementId).filter { it.role == role }.sortedBy { it.ordre }.map { it.valeurs }

    /**
     * Exercice qui reçoit la prochaine série ou le prochain palier : le dernier de l'entraînement, ou un nouveau
     * nommé [nomExercice] si c'est le premier ou après « Nouvel exercice ». `null` si ce nom est alors vide.
     */
    private suspend fun exerciceCourant(entrainement: Entrainement, nomExercice: String): Long? {
        val dernier = lireDernierExercice(entrainement.id)
        if (dernier != null && !entrainement.nouvelExercice) return dernier.id
        val nom = nomExercice.trim().ifEmpty { return null }
        val id = inserer(Exercice(entrainementId = entrainement.id, nom = nom, ordre = (dernier?.ordre ?: 0) + 1))
        modifierNouvelExercice(entrainement.id, false)
        return id
    }

    /**
     * Dialogue 4a : enregistre la série affichée (paliers en attente compris, cf. [enregistrerSerie]) puis
     * termine l'entraînement, dans une même transaction. Si la fin échoue, la série n'est pas enregistrée
     * non plus : un nouvel essai ne la double pas.
     */
    @Transaction
    open suspend fun enregistrerSerieEtTerminer(
        entrainementId: Long,
        nomExercice: String,
        poidsKg: Double?,
        repetitions: Int?,
        maintenant: Instant,
        realtime: Long,
    ) {
        enregistrerSerie(entrainementId, nomExercice, poidsKg, repetitions, Suite.NouvelleSerie, maintenant, realtime)
        terminer(entrainementId, maintenant)
    }

    /**
     * « Nouvel exercice » confirmé sans rien de saisi (DECISIONS.md) : l'exercice courant reste tel quel et la
     * prochaine série ouvrira un nouvel exercice. Rien n'est enregistré, le chrono n'est pas touché. Retourne
     * `false` sans rien changer s'il n'y a pas d'exercice courant (nom pas verrouillé), si une dégressive ou un
     * superset est en cours, ou si l'entraînement n'est plus en cours.
     */
    @Transaction
    open suspend fun passerExercice(entrainementId: Long): Boolean {
        val entrainement = lire(entrainementId)?.takeIf { it.termineLe == null } ?: return false
        if (entrainement.nouvelExercice || lireDernierExercice(entrainementId) == null) return false
        if (lirePaliersEnCours(entrainementId).isNotEmpty() || lireSupersetEnCours(entrainementId) != null) return false
        modifierNouvelExercice(entrainementId, true)
        return true
    }

    // --- Superset (spec §4.3.10) ---

    /**
     * Pastille « Superset » : l'exercice courant devient A, nommé [nomA] tel que saisi, et B est vide. Retourne le
     * superset commencé, ou `null` sans rien changer si un superset est déjà en cours, si l'exercice courant a
     * déjà une série ou un palier gardé, ou si l'entraînement n'est plus en cours.
     */
    @Transaction
    open suspend fun commencerSuperset(entrainementId: Long, nomA: String, supersetId: String): SupersetEnCours? {
        val entrainement = lire(entrainementId)?.takeIf { it.termineLe == null } ?: return null
        if (lireSupersetEnCours(entrainementId) != null) return null
        if (lireDernierExercice(entrainementId) != null && !entrainement.nouvelExercice) return null
        if (lirePaliersEnCours(entrainementId).isNotEmpty()) return null
        val superset = SupersetEnCours(entrainementId, supersetId, nomA = nomA)
        inserer(superset)
        return superset
    }

    /**
     * Enregistre un tour du superset en cours : la série de chaque onglet saisi ([a], [b] ; `null` : onglet ignoré),
     * paliers gardés par « Dégressive » compris, avec le même numéro de tour, puis relance le chrono. Les exercices
     * A et B sont créés au premier tour où ils ont une série, à la suite de l'entraînement. [fin] (« Nouvel
     * exercice », fin de l'entraînement) : le superset se termine, l'exercice suivant sera nouveau, et un exercice
     * resté seul redevient ordinaire ; sinon les valeurs saisies sont effacées et l'onglet A redevient actif.
     * Retourne le tour enregistré (sans série si les deux onglets sont ignorés avec [fin]), ou `null` sans rien
     * enregistrer : pas de superset en cours, onglet sans aucun palier, nom vide pour un exercice à créer, ou
     * aucun onglet sans [fin]. Le tour s'enregistre en entier ou pas du tout.
     */
    @Transaction
    open suspend fun enregistrerTour(
        entrainementId: Long,
        a: SaisieOnglet?,
        b: SaisieOnglet?,
        fin: Boolean,
        maintenant: Instant,
        realtime: Long,
    ): TourEnregistre? {
        lire(entrainementId)?.takeIf { it.termineLe == null } ?: return null
        val superset = lireSupersetEnCours(entrainementId) ?: return null
        val saisies = listOfNotNull(a?.let { RoleSuperset.A to it }, b?.let { RoleSuperset.B to it })
        if (saisies.isEmpty() && !fin) return null
        val existants = lireExercicesSuperset(superset.supersetId).associateBy { it.supersetRole }
        // Tout est vérifié avant la première écriture.
        val aEnregistrer = saisies.map { (role, saisie) ->
            val paliers = paliersGardes(entrainementId, role) + listOfNotNull(saisie.repetitions?.let { ValeursPalier(saisie.poidsKg, it) })
            if (paliers.isEmpty()) return null
            paliers.forEach(::verifier)
            val nom = saisie.nom.trim().ifEmpty { superset.nom(role).trim() }
            if (existants[role] == null && nom.isEmpty()) return null
            Triple(role, nom, paliers)
        }
        val tour = (lireDernierTourSuperset(superset.supersetId) ?: 0) + 1
        var dernierOrdre = lireDernierExercice(entrainementId)?.ordre ?: 0
        val series = LinkedHashMap<RoleSuperset, SerieAvecPaliers>()
        for ((role, nom, paliers) in aEnregistrer) {
            val exerciceId = existants[role]?.id ?: inserer(
                Exercice(entrainementId = entrainementId, nom = nom, ordre = ++dernierOrdre, supersetId = superset.supersetId, supersetRole = role),
            )
            series[role] = ajouterSerie(exerciceId, paliers, maintenant, ordre = tour)
            supprimerPaliersEnCours(entrainementId, role)
        }
        if (series.isNotEmpty()) enregistrer((lireChrono(entrainementId) ?: EtatChrono(entrainementId)).relance(realtime))
        if (fin) {
            supprimerSupersetEnCours(entrainementId)
            supprimerPaliersEnCours(entrainementId)
            modifierNouvelExercice(entrainementId, true)
            dissoudreSupersetsIncomplets(entrainementId)
        } else {
            reinitialiserSuperset(entrainementId)
        }
        return TourEnregistre(tour, series)
    }

    // --- Annulation du dernier enregistrement (toast « Annuler », DECISIONS.md) ---

    /** [enregistrerSerie], avec de quoi l'annuler. */
    @Transaction
    open suspend fun enregistrerSerieAnnulable(
        entrainementId: Long,
        nomExercice: String,
        poidsKg: Double?,
        repetitions: Int?,
        suite: Suite,
        maintenant: Instant,
        realtime: Long,
    ): Annulable<SerieAvecPaliers>? {
        val avant = etatAvant(entrainementId) ?: return null
        val serie = enregistrerSerie(entrainementId, nomExercice, poidsKg, repetitions, suite, maintenant, realtime) ?: return null
        return Annulable(serie, avant.apres(listOf(serie.serie.id)))
    }

    /** [enregistrerTour], avec de quoi l'annuler (un tour sans série n'a rien à annuler : `null`). */
    @Transaction
    open suspend fun enregistrerTourAnnulable(
        entrainementId: Long,
        a: SaisieOnglet?,
        b: SaisieOnglet?,
        fin: Boolean,
        maintenant: Instant,
        realtime: Long,
    ): Annulable<TourEnregistre>? {
        val avant = etatAvant(entrainementId) ?: return null
        val tour = enregistrerTour(entrainementId, a, b, fin, maintenant, realtime) ?: return null
        return Annulable(tour, avant.apres(tour.series.values.map { it.serie.id }))
    }

    private suspend fun etatAvant(entrainementId: Long): Annulation? {
        val entrainement = lire(entrainementId) ?: return null
        return Annulation(
            entrainementId = entrainementId,
            nouvelExercice = entrainement.nouvelExercice,
            exercices = lireExercices(entrainementId),
            paliersEnCours = lirePaliersEnCours(entrainementId),
            superset = lireSupersetEnCours(entrainementId),
        )
    }

    private suspend fun Annulation.apres(series: List<Long>): Annulation {
        val existants = exercices.mapTo(HashSet()) { it.id }
        return copy(series = series, exercicesCrees = lireExercices(entrainementId).map { it.id }.filter { it !in existants })
    }

    /**
     * Annule l'enregistrement décrit par [annulation] : ses séries, et les exercices créés avec elles, sont supprimés ;
     * la saisie retrouve son état d'avant (exercice courant ou nouveau, paliers gardés, superset en cours, exercices
     * de nouveau dans leur superset). Le chrono n'est pas touché. Retourne `false` sans rien changer si l'entraînement
     * n'est plus en cours, ou si autre chose a été enregistré depuis (série, palier gardé, exercice).
     */
    @Transaction
    open suspend fun annuler(annulation: Annulation): Boolean {
        val id = annulation.entrainementId
        lire(id)?.takeIf { it.termineLe == null } ?: return false
        if (annulation.series.isEmpty() || lireDerniereSerie(id) != annulation.series.max()) return false
        if (annulation.series.any { lireSerie(it) == null } || lirePaliersEnCours(id).isNotEmpty()) return false
        val exercices = annulation.exercices.map { it.id } + annulation.exercicesCrees
        if (lireExercices(id).map { it.id }.toSet() != exercices.toSet()) return false
        annulation.series.forEach { supprimerSerieBrut(it) }
        annulation.exercicesCrees.forEach { supprimerExercice(it) }
        annulation.exercices.forEach { modifierSupersetExercice(it.id, it.supersetId, it.supersetRole) }
        modifierNouvelExercice(id, annulation.nouvelExercice)
        annulation.paliersEnCours.forEach { inserer(it.copy(id = 0)) }
        supprimerSupersetEnCours(id)
        annulation.superset?.let { inserer(it) }
        return true
    }

    /** Dialogues de fin d'un superset (4a, série incomplète) : le tour, puis la fin de l'entraînement, ensemble. */
    @Transaction
    open suspend fun enregistrerTourEtTerminer(
        entrainementId: Long,
        a: SaisieOnglet?,
        b: SaisieOnglet?,
        maintenant: Instant,
        realtime: Long,
    ) {
        enregistrerTour(entrainementId, a, b, fin = true, maintenant = maintenant, realtime = realtime)
        terminer(entrainementId, maintenant)
    }

    /** Mémorise noms, onglet actif et valeurs saisies ; sans effet si ce superset s'est terminé entre-temps. */
    suspend fun memoriserSuperset(superset: SupersetEnCours) = with(superset) {
        memoriserSupersetBrut(
            entrainementId, supersetId, nomA, nomB, actif, poidsA, repetitionsA, poidsB, repetitionsB,
        )
    }

    /**
     * Détail (maquette 3·S+ T1) : écrit le tour [tour] du superset de [exerciceA] et [exerciceB] (un nouveau tour
     * après le dernier si `null`). Pour chaque exercice, [a] ou [b] remplace les paliers de sa série de ce tour, ou
     * la crée s'il n'en a pas ; `null` : rien pour cet exercice. Retourne le numéro du tour, ou `null` sans rien
     * écrire si A et B ne forment plus ce superset (l'un supprimé entre-temps).
     */
    @Transaction
    open suspend fun ecrireTour(
        exerciceA: Long,
        exerciceB: Long,
        tour: Int?,
        a: List<ValeursPalier>?,
        b: List<ValeursPalier>?,
        maintenant: Instant,
    ): Int? {
        val supersetId = lireExercice(exerciceA)?.supersetId ?: return null
        if (lirePartenaire(supersetId, exerciceA)?.id != exerciceB) return null
        val numero = tour ?: ((lireDernierTourSuperset(supersetId) ?: 0) + 1)
        for ((exerciceId, paliers) in listOf(exerciceA to a, exerciceB to b)) {
            if (paliers == null) continue
            val existante = lireSerieTour(exerciceId, numero)
            if (existante != null) modifierSerie(existante.id, paliers, maintenant) else ajouterSerie(exerciceId, paliers, maintenant, ordre = numero)
        }
        return numero
    }

    // --- Import (DECISIONS.md) ---

    /** Tout l'historique terminé, lu en une transaction (doublons de l'import). */
    suspend fun lireHistoriqueComplet(): List<EntrainementComplet> = lireHistoriqueCompletBrut().map { it.trie() }

    /**
     * Ajoute à l'historique les [entrainements] importés, sauf ceux déjà présents (cf. [ecarterDoublons]), tout ou
     * rien. Ils sont terminés à [maintenant] ; entre eux, l'accueil garde l'ordre du fichier pour un même jour. Les
     * deux exercices d'un superset reçoivent un nouvel identifiant commun, leurs séries le numéro de leur tour.
     * L'entraînement en cours n'est pas touché. Retourne le nombre d'entraînements ajoutés.
     */
    @Transaction
    open suspend fun importer(entrainements: List<EntrainementImporte>, maintenant: Instant): Int {
        val (nouveaux, _) = ecarterDoublons(entrainements, lireHistoriqueComplet().map { it.versImport() })
        nouveaux.forEachIndexed { index, importe ->
            // Accueil : même date, le plus récemment créé d'abord ; le fichier va du plus récent au plus ancien.
            val creeLe = maintenant.minusMillis(index.toLong())
            val id = inserer(Entrainement(date = importe.date, libelle = importe.libelle.nettoye(), creeLe = creeLe, termineLe = maintenant))
            val supersets = importe.exercices.mapNotNull { it.superset }.distinct().associateWith { UUID.randomUUID().toString() }
            importe.exercices.forEachIndexed { ordre, exercice ->
                val exerciceId = inserer(
                    Exercice(
                        entrainementId = id,
                        nom = exercice.nom,
                        ordre = ordre + 1,
                        supersetId = exercice.superset?.let { supersets.getValue(it) },
                        supersetRole = exercice.role,
                    ),
                )
                exercice.series.forEach { ajouterSerie(exerciceId, it.paliers, creeLe, ordre = it.ordre) }
            }
        }
        return nouveaux.size
    }

    /** Applique une action du chrono (Play, Pause, Relancer) à l'état enregistré. */
    @Transaction
    open suspend fun modifierChrono(entrainementId: Long, action: (EtatChrono) -> EtatChrono): EtatChrono? {
        val etat = lireChrono(entrainementId) ?: return null
        return action(etat).also { enregistrer(it) }
    }

    // --- Historique : exercices et séries ---

    suspend fun renommerExercice(id: Long, nom: String) {
        val nomNettoye = nom.trim()
        require(nomNettoye.isNotEmpty()) { "Nom d'exercice vide" }
        renommerExerciceBrut(id, nomNettoye)
    }

    /**
     * Ajoute une série de [paliers] (au moins un, le principal d'abord) en dernière position de l'exercice, ou au
     * numéro [ordre] (tour d'un superset où cet exercice n'a pas encore de série).
     */
    @Transaction
    open suspend fun ajouterSerie(exerciceId: Long, paliers: List<ValeursPalier>, maintenant: Instant, ordre: Int? = null): SerieAvecPaliers {
        require(paliers.isNotEmpty()) { "Série sans palier" }
        paliers.forEach(::verifier)
        val serie = Serie(exerciceId = exerciceId, ordre = ordre ?: ((lireDernierOrdre(exerciceId) ?: 0) + 1), creeLe = maintenant)
        val enregistree = serie.copy(id = inserer(serie))
        return SerieAvecPaliers(enregistree, insererPaliers(enregistree.id, paliers))
    }

    /** Remplace tous les paliers de la série (édition, spec §4.5 et §4.5.1) et renseigne `modifie_le`. */
    @Transaction
    open suspend fun modifierSerie(id: Long, paliers: List<ValeursPalier>, maintenant: Instant) {
        require(paliers.isNotEmpty()) { "Série sans palier" }
        paliers.forEach(::verifier)
        supprimerPaliers(id)
        insererPaliers(id, paliers)
        marquerModifiee(id, maintenant)
    }

    private suspend fun insererPaliers(serieId: Long, paliers: List<ValeursPalier>): List<Palier> =
        paliers.mapIndexed { index, valeurs ->
            val palier = Palier(serieId = serieId, ordre = index + 1, poidsKg = valeurs.poidsKg, repetitions = valeurs.repetitions)
            palier.copy(id = inserer(palier))
        }

    private fun verifier(palier: ValeursPalier) {
        require(palier.repetitions > 0) { "Répétitions : ${palier.repetitions}" }
        require(palier.poidsKg == null || palier.poidsKg >= 0) { "Poids : ${palier.poidsKg}" }
    }

    /**
     * Supprime la série et renumérote les suivantes (spec §4.4). Un exercice sans série est supprimé
     * et les exercices suivants renumérotés ; l'entraînement, lui, reste dans l'historique.
     * Dans un superset (spec §4.3.10), le numéro est celui du tour : les tours ne sont renumérotés, pour A et B,
     * que si ce tour devient vide ; un exercice resté seul redevient ordinaire, ses séries numérotées à la suite.
     */
    @Transaction
    open suspend fun supprimerSerie(id: Long) {
        val serie = lireSerie(id) ?: return
        val exercice = lireExercice(serie.exerciceId) ?: return
        val partenaire = exercice.supersetId?.let { lirePartenaire(it, exercice.id) }
        supprimerSerieBrut(id)
        if (partenaire == null || lireSerieTour(partenaire.id, serie.ordre) == null) {
            decalerSeries(exercice.id, serie.ordre)
            if (partenaire != null) decalerSeries(partenaire.id, serie.ordre)
        }
        if (compterSeries(exercice.id) == 0) {
            supprimerExercice(exercice.id)
            decalerExercices(exercice.entrainementId, exercice.ordre)
            if (partenaire != null) {
                dissoudreSupersetsIncomplets(exercice.entrainementId)
                lireSeries(partenaire.id).sortedBy { it.ordre }.forEachIndexed { index, s -> modifierOrdreSerie(s.id, index + 1) }
            }
        }
    }

    /** « Supprimer le tour N ? » (H) : les séries choisies (A, B ou les deux), ensemble ; cf. [supprimerSerie]. */
    @Transaction
    open suspend fun supprimerSeries(ids: List<Long>) = ids.forEach { supprimerSerie(it) }

    // --- Requêtes élémentaires ---

    @Transaction
    @Query("SELECT * FROM exercice WHERE entrainement_id = :entrainementId")
    protected abstract fun observerExercicesBruts(entrainementId: Long): Flow<List<ExerciceAvecSeries>>

    @Transaction
    @Query("SELECT * FROM entrainement WHERE id = :id")
    protected abstract suspend fun lireCompletBrut(id: Long): EntrainementComplet?

    @Transaction
    @Query("SELECT * FROM entrainement WHERE id = :id")
    protected abstract fun observerCompletBrut(id: Long): Flow<EntrainementComplet?>

    @Transaction
    @Query("SELECT * FROM entrainement WHERE termine_le IS NULL LIMIT 1")
    protected abstract fun observerEnCoursCompletBrut(): Flow<EntrainementEnCours?>

    @Transaction
    @Query("SELECT * FROM entrainement WHERE termine_le IS NULL LIMIT 1")
    protected abstract suspend fun lireEnCoursCompletBrut(): EntrainementEnCours?

    @Transaction
    @Query("SELECT * FROM entrainement WHERE termine_le IS NOT NULL ORDER BY date DESC, cree_le DESC")
    protected abstract fun observerHistoriqueCompletBrut(): Flow<List<EntrainementComplet>>

    @Transaction
    @Query("SELECT * FROM entrainement WHERE termine_le IS NOT NULL ORDER BY date DESC, cree_le DESC")
    protected abstract suspend fun lireHistoriqueCompletBrut(): List<EntrainementComplet>

    @Query("SELECT * FROM entrainement WHERE id = :id")
    protected abstract suspend fun lire(id: Long): Entrainement?

    @Query("SELECT * FROM exercice WHERE id = :id")
    protected abstract suspend fun lireExercice(id: Long): Exercice?

    @Query("SELECT * FROM exercice WHERE entrainement_id = :entrainementId ORDER BY ordre DESC LIMIT 1")
    protected abstract suspend fun lireDernierExercice(entrainementId: Long): Exercice?

    @Query("SELECT * FROM exercice WHERE entrainement_id = :entrainementId ORDER BY ordre")
    protected abstract suspend fun lireExercices(entrainementId: Long): List<Exercice>

    /** Identifiant de la dernière série enregistrée de l'entraînement (identifiants croissants, jamais réutilisés). */
    @Query("SELECT MAX(s.id) FROM serie s JOIN exercice x ON x.id = s.exercice_id WHERE x.entrainement_id = :entrainementId")
    protected abstract suspend fun lireDerniereSerie(entrainementId: Long): Long?

    @Query("UPDATE exercice SET superset_id = :supersetId, superset_role = :role WHERE id = :id")
    protected abstract suspend fun modifierSupersetExercice(id: Long, supersetId: String?, role: RoleSuperset?)

    @Query("SELECT DISTINCT nom FROM exercice WHERE entrainement_id != :entrainementId")
    protected abstract suspend fun lireNomsExercicesHors(entrainementId: Long): List<String>

    @Query(
        """
        SELECT t.id AS entrainement_id, t.date, t.libelle, x.nom, s.id AS serie_id, p.poids_kg, p.repetitions,
        (SELECT y.nom FROM exercice y WHERE y.superset_id = x.superset_id AND y.id != x.id LIMIT 1) AS partenaire
        FROM palier p JOIN serie s ON s.id = p.serie_id JOIN exercice x ON x.id = s.exercice_id
        JOIN entrainement t ON t.id = x.entrainement_id
        WHERE x.nom IN (:noms) AND t.id != :entrainementId
        ORDER BY t.date DESC, t.cree_le DESC, t.id DESC, x.ordre, s.ordre, p.ordre
        """,
    )
    protected abstract suspend fun lireSeriesHistorique(entrainementId: Long, noms: List<String>): List<SerieHistorique>

    @Query("SELECT * FROM serie WHERE id = :id")
    protected abstract suspend fun lireSerie(id: Long): Serie?

    @Query("SELECT COUNT(*) FROM serie WHERE exercice_id = :exerciceId")
    protected abstract suspend fun compterSeries(exerciceId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM serie s JOIN exercice e ON e.id = s.exercice_id
        WHERE e.entrainement_id = :entrainementId
        """,
    )
    protected abstract suspend fun compterSeriesEntrainement(entrainementId: Long): Int

    @Insert
    protected abstract suspend fun inserer(entrainement: Entrainement): Long

    @Insert
    protected abstract suspend fun inserer(exercice: Exercice): Long

    @Insert
    protected abstract suspend fun inserer(serie: Serie): Long

    @Insert
    protected abstract suspend fun inserer(palier: Palier): Long

    @Insert
    protected abstract suspend fun inserer(palier: PalierEnCours): Long

    @Query("SELECT * FROM palier_en_cours WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun lirePaliersEnCours(entrainementId: Long): List<PalierEnCours>

    /** `role IS :role` : vrai aussi pour deux `NULL` (paliers hors superset). */
    @Query("SELECT COUNT(*) FROM palier_en_cours WHERE entrainement_id = :entrainementId AND role IS :role")
    protected abstract suspend fun compterPaliersEnCours(entrainementId: Long, role: RoleSuperset?): Int

    @Query("DELETE FROM palier_en_cours WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun supprimerPaliersEnCours(entrainementId: Long)

    @Query("DELETE FROM palier_en_cours WHERE entrainement_id = :entrainementId AND role IS :role")
    protected abstract suspend fun supprimerPaliersEnCours(entrainementId: Long, role: RoleSuperset?)

    @Insert
    protected abstract suspend fun inserer(superset: SupersetEnCours)

    @Query("SELECT * FROM superset_en_cours WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun lireSupersetEnCours(entrainementId: Long): SupersetEnCours?

    @Query(
        """
        UPDATE superset_en_cours SET nom_a = :nomA, nom_b = :nomB, actif = :actif, poids_a = :poidsA,
        repetitions_a = :repetitionsA, poids_b = :poidsB, repetitions_b = :repetitionsB
        WHERE entrainement_id = :entrainementId AND superset_id = :supersetId
        """,
    )
    protected abstract suspend fun memoriserSupersetBrut(
        entrainementId: Long,
        supersetId: String,
        nomA: String,
        nomB: String,
        actif: RoleSuperset,
        poidsA: String,
        repetitionsA: String,
        poidsB: String,
        repetitionsB: String,
    )

    @Query(
        """
        UPDATE superset_en_cours SET actif = 'A', poids_a = '', repetitions_a = '', poids_b = '', repetitions_b = ''
        WHERE entrainement_id = :entrainementId
        """,
    )
    protected abstract suspend fun reinitialiserSuperset(entrainementId: Long)

    @Query("DELETE FROM superset_en_cours WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun supprimerSupersetEnCours(entrainementId: Long)

    @Query("SELECT * FROM exercice WHERE superset_id = :supersetId")
    protected abstract suspend fun lireExercicesSuperset(supersetId: String): List<Exercice>

    @Query("SELECT * FROM exercice WHERE superset_id = :supersetId AND id != :exerciceId LIMIT 1")
    protected abstract suspend fun lirePartenaire(supersetId: String, exerciceId: Long): Exercice?

    @Query("SELECT MAX(s.ordre) FROM serie s JOIN exercice x ON x.id = s.exercice_id WHERE x.superset_id = :supersetId")
    protected abstract suspend fun lireDernierTourSuperset(supersetId: String): Int?

    @Query("SELECT MAX(ordre) FROM serie WHERE exercice_id = :exerciceId")
    protected abstract suspend fun lireDernierOrdre(exerciceId: Long): Int?

    @Query("SELECT * FROM serie WHERE exercice_id = :exerciceId AND ordre = :ordre LIMIT 1")
    protected abstract suspend fun lireSerieTour(exerciceId: Long, ordre: Int): Serie?

    @Query("SELECT * FROM serie WHERE exercice_id = :exerciceId")
    protected abstract suspend fun lireSeries(exerciceId: Long): List<Serie>

    @Query("UPDATE serie SET ordre = :ordre WHERE id = :id")
    protected abstract suspend fun modifierOrdreSerie(id: Long, ordre: Int)

    /** Un exercice resté seul de son superset (l'autre supprimé, ou jamais enregistré) redevient ordinaire. */
    @Query(
        """
        UPDATE exercice SET superset_id = NULL, superset_role = NULL
        WHERE entrainement_id = :entrainementId AND superset_id IS NOT NULL
        AND (SELECT COUNT(*) FROM exercice y WHERE y.superset_id = exercice.superset_id) < 2
        """,
    )
    protected abstract suspend fun dissoudreSupersetsIncomplets(entrainementId: Long)

    @Query(
        """
        DELETE FROM exercice WHERE entrainement_id = :entrainementId
        AND NOT EXISTS (SELECT 1 FROM serie s WHERE s.exercice_id = exercice.id)
        """,
    )
    protected abstract suspend fun supprimerExercicesSansSerie(entrainementId: Long)

    @Query("DELETE FROM palier WHERE serie_id = :serieId")
    protected abstract suspend fun supprimerPaliers(serieId: Long)

    @Query("UPDATE serie SET modifie_le = :maintenant WHERE id = :id")
    protected abstract suspend fun marquerModifiee(id: Long, maintenant: Instant)

    @Upsert
    protected abstract suspend fun enregistrer(etat: EtatChrono)

    @Query("UPDATE entrainement SET date = :date, libelle = :libelle WHERE id = :id")
    protected abstract suspend fun modifierEntrainementBrut(id: Long, date: LocalDate, libelle: String?)

    @Query("UPDATE entrainement SET termine_le = :maintenant, nouvel_exercice = 0 WHERE id = :id")
    protected abstract suspend fun marquerTermine(id: Long, maintenant: Instant)

    @Query("UPDATE entrainement SET nouvel_exercice = :valeur WHERE id = :id")
    protected abstract suspend fun modifierNouvelExercice(id: Long, valeur: Boolean)

    @Query("DELETE FROM etat_chrono WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun supprimerChrono(entrainementId: Long)

    @Query("UPDATE exercice SET nom = :nom WHERE id = :id")
    protected abstract suspend fun renommerExerciceBrut(id: Long, nom: String)

    @Query("DELETE FROM exercice WHERE id = :id")
    protected abstract suspend fun supprimerExercice(id: Long)

    @Query("UPDATE exercice SET ordre = ordre - 1 WHERE entrainement_id = :entrainementId AND ordre > :ordre")
    protected abstract suspend fun decalerExercices(entrainementId: Long, ordre: Int)

    @Query("DELETE FROM serie WHERE id = :id")
    protected abstract suspend fun supprimerSerieBrut(id: Long)

    @Query("UPDATE serie SET ordre = ordre - 1 WHERE exercice_id = :exerciceId AND ordre > :ordre")
    protected abstract suspend fun decalerSeries(exerciceId: Long, ordre: Int)
}

/** Libellé sans espaces de début et de fin ; vide = pas de libellé. */
private fun String?.nettoye(): String? = this?.trim()?.ifEmpty { null }
