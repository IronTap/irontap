package app.irontap.donnees

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.irontap.logique.normaliserNom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

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
     * l'entraînement lui-même. Une dégressive en attente est abandonnée, avec l'exercice que son premier
     * palier avait ouvert si aucune série n'y a été enregistrée.
     */
    @Transaction
    open suspend fun terminer(id: Long, maintenant: Instant) {
        supprimerPaliersEnCours(id)
        supprimerExercicesSansSerie(id)
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
        val paliers = lirePaliersEnCours(entrainementId).sortedBy { it.ordre }.map { it.valeurs } +
            listOfNotNull(repetitions?.let { ValeursPalier(poidsKg, it) })
        if (paliers.isEmpty()) return null
        val exerciceId = exerciceCourant(entrainement, nomExercice) ?: return null
        val serie = ajouterSerie(exerciceId, paliers, maintenant)
        supprimerPaliersEnCours(entrainementId)
        modifierNouvelExercice(entrainementId, suite == Suite.NouvelExercice)
        enregistrer((lireChrono(entrainementId) ?: EtatChrono(entrainementId)).relance(realtime))
        return serie
    }

    /**
     * « Dégressive » (spec §4.3.9) : garde le palier [poidsKg] × [repetitions] pour la série en cours, sans
     * toucher au chrono ni au numéro de série. Le nom se verrouille : un nouvel exercice est ouvert dès ce
     * premier palier, comme le ferait [enregistrerSerie]. Retourne le palier gardé, ou `null` sans rien
     * enregistrer si un nouvel exercice doit être ouvert et que [nomExercice] est vide, ou si l'entraînement
     * n'est plus en cours.
     */
    @Transaction
    open suspend fun ajouterPalierEnCours(
        entrainementId: Long,
        nomExercice: String,
        poidsKg: Double?,
        repetitions: Int,
    ): PalierEnCours? {
        verifier(ValeursPalier(poidsKg, repetitions))
        val entrainement = lire(entrainementId)?.takeIf { it.termineLe == null } ?: return null
        exerciceCourant(entrainement, nomExercice) ?: return null
        val palier = PalierEnCours(
            entrainementId = entrainementId,
            ordre = compterPaliersEnCours(entrainementId) + 1,
            poidsKg = poidsKg,
            repetitions = repetitions,
        )
        return palier.copy(id = inserer(palier))
    }

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

    /** Ajoute une série de [paliers] (au moins un, le principal d'abord) en dernière position de l'exercice. */
    @Transaction
    open suspend fun ajouterSerie(exerciceId: Long, paliers: List<ValeursPalier>, maintenant: Instant): SerieAvecPaliers {
        require(paliers.isNotEmpty()) { "Série sans palier" }
        paliers.forEach(::verifier)
        val serie = Serie(exerciceId = exerciceId, ordre = compterSeries(exerciceId) + 1, creeLe = maintenant)
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
     */
    @Transaction
    open suspend fun supprimerSerie(id: Long) {
        val serie = lireSerie(id) ?: return
        supprimerSerieBrut(id)
        decalerSeries(serie.exerciceId, serie.ordre)
        if (compterSeries(serie.exerciceId) == 0) {
            val exercice = lireExercice(serie.exerciceId) ?: return
            supprimerExercice(exercice.id)
            decalerExercices(exercice.entrainementId, exercice.ordre)
        }
    }

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

    @Query("SELECT * FROM entrainement WHERE id = :id")
    protected abstract suspend fun lire(id: Long): Entrainement?

    @Query("SELECT * FROM exercice WHERE id = :id")
    protected abstract suspend fun lireExercice(id: Long): Exercice?

    @Query("SELECT * FROM exercice WHERE entrainement_id = :entrainementId ORDER BY ordre DESC LIMIT 1")
    protected abstract suspend fun lireDernierExercice(entrainementId: Long): Exercice?

    @Query("SELECT DISTINCT nom FROM exercice WHERE entrainement_id != :entrainementId")
    protected abstract suspend fun lireNomsExercicesHors(entrainementId: Long): List<String>

    @Query(
        """
        SELECT t.id AS entrainement_id, t.date, t.libelle, x.nom, s.id AS serie_id, p.poids_kg, p.repetitions
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

    @Query("SELECT COUNT(*) FROM palier_en_cours WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun compterPaliersEnCours(entrainementId: Long): Int

    @Query("DELETE FROM palier_en_cours WHERE entrainement_id = :entrainementId")
    protected abstract suspend fun supprimerPaliersEnCours(entrainementId: Long)

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
