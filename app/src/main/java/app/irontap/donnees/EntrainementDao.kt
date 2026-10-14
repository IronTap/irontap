package app.irontap.donnees

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    suspend fun lireComplet(id: Long): EntrainementComplet? = lireCompletBrut(id)?.trie()

    /** Tout l'historique terminé, dans l'ordre de l'accueil (export). */
    suspend fun lireHistoriqueComplet(): List<EntrainementComplet> = lireHistoriqueCompletBrut().map { it.trie() }

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
     * l'entraînement lui-même.
     */
    @Transaction
    open suspend fun terminer(id: Long, maintenant: Instant) {
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
     * Enregistre une série de l'entraînement en cours et relance le chrono.
     * La série ouvre un nouvel exercice nommé [nomExercice] si c'est la première de l'entraînement
     * ou si la précédente a été suivie de « Nouvel exercice » ; sinon [nomExercice] est ignoré
     * (nom verrouillé). Retourne la série enregistrée, avec son numéro, ou `null` sans rien enregistrer
     * si un nouvel exercice doit être ouvert et que [nomExercice] est vide, ou si l'entraînement n'est
     * plus en cours (terminé ou supprimé entre-temps).
     */
    @Transaction
    open suspend fun enregistrerSerie(
        entrainementId: Long,
        nomExercice: String,
        poidsKg: Double?,
        repetitions: Int,
        suite: Suite,
        maintenant: Instant,
        realtime: Long,
    ): Serie? {
        val entrainement = lire(entrainementId)?.takeIf { it.termineLe == null } ?: return null
        val dernier = lireDernierExercice(entrainementId)
        val exerciceId = if (dernier == null || entrainement.nouvelExercice) {
            val nom = nomExercice.trim().ifEmpty { return null }
            inserer(Exercice(entrainementId = entrainementId, nom = nom, ordre = (dernier?.ordre ?: 0) + 1))
        } else {
            dernier.id
        }
        val serie = ajouterSerie(exerciceId, poidsKg, repetitions, maintenant)
        modifierNouvelExercice(entrainementId, suite == Suite.NouvelExercice)
        enregistrer((lireChrono(entrainementId) ?: EtatChrono(entrainementId)).relance(realtime))
        return serie
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

    /** Ajoute une série en dernière position de l'exercice. */
    @Transaction
    open suspend fun ajouterSerie(exerciceId: Long, poidsKg: Double?, repetitions: Int, maintenant: Instant): Serie {
        require(repetitions > 0) { "Répétitions : $repetitions" }
        require(poidsKg == null || poidsKg >= 0) { "Poids : $poidsKg" }
        val serie = Serie(
            exerciceId = exerciceId,
            ordre = compterSeries(exerciceId) + 1,
            poidsKg = poidsKg,
            repetitions = repetitions,
            creeLe = maintenant,
        )
        return serie.copy(id = inserer(serie))
    }

    suspend fun modifierSerie(id: Long, poidsKg: Double?, repetitions: Int, maintenant: Instant) {
        require(repetitions > 0) { "Répétitions : $repetitions" }
        require(poidsKg == null || poidsKg >= 0) { "Poids : $poidsKg" }
        modifierSerieBrut(id, poidsKg, repetitions, maintenant)
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
    @Query("SELECT * FROM entrainement WHERE termine_le IS NULL LIMIT 1")
    protected abstract fun observerEnCoursCompletBrut(): Flow<EntrainementEnCours?>

    @Transaction
    @Query("SELECT * FROM entrainement WHERE termine_le IS NULL LIMIT 1")
    protected abstract suspend fun lireEnCoursCompletBrut(): EntrainementEnCours?

    @Transaction
    @Query("SELECT * FROM entrainement WHERE termine_le IS NOT NULL ORDER BY date DESC, cree_le DESC")
    protected abstract suspend fun lireHistoriqueCompletBrut(): List<EntrainementComplet>

    @Query("SELECT * FROM entrainement WHERE id = :id")
    protected abstract suspend fun lire(id: Long): Entrainement?

    @Query("SELECT * FROM exercice WHERE id = :id")
    protected abstract suspend fun lireExercice(id: Long): Exercice?

    @Query("SELECT * FROM exercice WHERE entrainement_id = :entrainementId ORDER BY ordre DESC LIMIT 1")
    protected abstract suspend fun lireDernierExercice(entrainementId: Long): Exercice?

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

    @Query(
        """
        UPDATE serie SET poids_kg = :poidsKg, repetitions = :repetitions, modifie_le = :maintenant
        WHERE id = :id
        """,
    )
    protected abstract suspend fun modifierSerieBrut(id: Long, poidsKg: Double?, repetitions: Int, maintenant: Instant)

    @Query("DELETE FROM serie WHERE id = :id")
    protected abstract suspend fun supprimerSerieBrut(id: Long)

    @Query("UPDATE serie SET ordre = ordre - 1 WHERE exercice_id = :exerciceId AND ordre > :ordre")
    protected abstract suspend fun decalerSeries(exerciceId: Long, ordre: Int)
}

/** Libellé sans espaces de début et de fin ; vide = pas de libellé. */
private fun String?.nettoye(): String? = this?.trim()?.ifEmpty { null }
