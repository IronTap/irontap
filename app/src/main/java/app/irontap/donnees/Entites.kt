package app.irontap.donnees

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.time.LocalDate

/** Une séance à la salle (spec §6). En cours tant que [termineLe] est vide, au plus une à la fois. */
@Entity(tableName = "entrainement")
data class Entrainement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val libelle: String? = null,
    @ColumnInfo(name = "cree_le") val creeLe: Instant,
    @ColumnInfo(name = "termine_le") val termineLe: Instant? = null,
    /**
     * Entraînement en cours uniquement : vrai après « Nouvel exercice ». La prochaine série ouvre
     * alors un nouvel exercice, dont le nom est encore éditable. Absent de la spec §6, mais
     * nécessaire pour reprendre la saisie dans l'état où elle a été laissée (spec §4.3.7).
     */
    @ColumnInfo(name = "nouvel_exercice") val nouvelExercice: Boolean = false,
)

/** Un mouvement de l'entraînement. [ordre] commence à 1. */
@Entity(
    tableName = "exercice",
    foreignKeys = [
        ForeignKey(
            entity = Entrainement::class,
            parentColumns = ["id"],
            childColumns = ["entrainement_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entrainement_id")],
)
data class Exercice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entrainement_id") val entrainementId: Long,
    val nom: String,
    val ordre: Int,
)

/**
 * Une exécution d'un exercice. [ordre] commence à 1.
 * [poidsKg] vide = poids du corps ; les multiples de 0,25 sont exacts en `Double`.
 */
@Entity(
    tableName = "serie",
    foreignKeys = [
        ForeignKey(
            entity = Exercice::class,
            parentColumns = ["id"],
            childColumns = ["exercice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exercice_id")],
)
data class Serie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercice_id") val exerciceId: Long,
    val ordre: Int,
    @ColumnInfo(name = "poids_kg") val poidsKg: Double?,
    val repetitions: Int,
    @ColumnInfo(name = "cree_le") val creeLe: Instant,
    @ColumnInfo(name = "modifie_le") val modifieLe: Instant? = null,
)

/**
 * Chrono de repos de l'entraînement en cours (spec §4.3.4 et §6), supprimé à la fin de l'entraînement.
 * Les temps sont en millisecondes de `SystemClock.elapsedRealtime()` : rien ne tourne en arrière-plan,
 * le temps écoulé se recalcule à partir de [demarreA] et [cumulMs].
 */
@Entity(
    tableName = "etat_chrono",
    foreignKeys = [
        ForeignKey(
            entity = Entrainement::class,
            parentColumns = ["id"],
            childColumns = ["entrainement_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EtatChrono(
    @PrimaryKey @ColumnInfo(name = "entrainement_id") val entrainementId: Long,
    val etat: Etat = Etat.Arrete,
    /** `elapsedRealtime` au dernier démarrage, vide si le chrono est arrêté ou en pause. */
    @ColumnInfo(name = "demarre_a") val demarreA: Long? = null,
    /** Temps cumulé avant le dernier démarrage. */
    @ColumnInfo(name = "cumul_ms") val cumulMs: Long = 0,
) {
    enum class Etat { Arrete, EnCours, Pause }

    /**
     * Temps écoulé à l'instant [realtime]. La part en cours est bornée à 0 : après un redémarrage
     * du téléphone, `elapsedRealtime` repart de zéro (cas non géré, cf. DECISIONS.md).
     */
    fun ecouleMs(realtime: Long): Long =
        cumulMs + if (etat == Etat.EnCours && demarreA != null) (realtime - demarreA).coerceAtLeast(0) else 0

    /** Play : démarre ou reprend depuis la valeur courante. Sans effet s'il tourne déjà. */
    fun demarre(realtime: Long): EtatChrono =
        if (etat == Etat.EnCours) this else copy(etat = Etat.EnCours, demarreA = realtime)

    /** Pause : fige la valeur courante. Sans effet s'il ne tourne pas. */
    fun enPause(realtime: Long): EtatChrono =
        if (etat != Etat.EnCours) this else copy(etat = Etat.Pause, demarreA = null, cumulMs = ecouleMs(realtime))

    /** Relancer (bouton ou série enregistrée) : repart de 0 et démarre, quel que soit l'état. */
    fun relance(realtime: Long): EtatChrono = copy(etat = Etat.EnCours, demarreA = realtime, cumulMs = 0)
}

/** Un exercice et ses séries, triées par [Serie.ordre]. */
data class ExerciceAvecSeries(
    @Embedded val exercice: Exercice,
    @Relation(parentColumn = "id", entityColumn = "exercice_id")
    val series: List<Serie>,
)

/** Un entraînement, ses exercices et leurs séries, triés par ordre (détail, export). */
data class EntrainementComplet(
    @Embedded val entrainement: Entrainement,
    @Relation(entity = Exercice::class, parentColumn = "id", entityColumn = "entrainement_id")
    val exercices: List<ExerciceAvecSeries>,
)

/** Room ne garantit pas l'ordre des relations : on trie après lecture. */
internal fun ExerciceAvecSeries.trie() = copy(series = series.sortedBy { it.ordre })

internal fun List<ExerciceAvecSeries>.trie() = sortedBy { it.exercice.ordre }.map { it.trie() }

internal fun EntrainementComplet.trie() = copy(exercices = exercices.trie())
