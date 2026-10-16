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
 * Une exécution d'un exercice. [ordre] commence à 1. Ses poids et répétitions sont dans ses [Palier] :
 * un seul pour une série simple, plusieurs pour une dégressive (spec §6, v1.4).
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
    @ColumnInfo(name = "cree_le") val creeLe: Instant,
    @ColumnInfo(name = "modifie_le") val modifieLe: Instant? = null,
)

/**
 * Un couple poids × répétitions d'une série. [ordre] commence à 1 (palier principal) ; les suivants sont
 * les paliers dégressifs, enchaînés sans repos. [poidsKg] vide = poids du corps ; les multiples de 0,25
 * sont exacts en `Double`.
 */
@Entity(
    tableName = "palier",
    foreignKeys = [
        ForeignKey(
            entity = Serie::class,
            parentColumns = ["id"],
            childColumns = ["serie_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("serie_id")],
)
data class Palier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "serie_id") val serieId: Long,
    val ordre: Int,
    @ColumnInfo(name = "poids_kg") val poidsKg: Double?,
    val repetitions: Int,
) {
    val valeurs get() = ValeursPalier(poidsKg, repetitions)
}

/**
 * Palier d'une dégressive en cours de saisie (spec §4.3.9) : gardé par « Dégressive », pas encore une série.
 * En base pour être retrouvé si l'appli est tuée ; « Nouvelle série » ou « Nouvel exercice » le versent dans
 * la série enregistrée, la fin de l'entraînement le supprime.
 */
@Entity(
    tableName = "palier_en_cours",
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
data class PalierEnCours(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entrainement_id") val entrainementId: Long,
    val ordre: Int,
    @ColumnInfo(name = "poids_kg") val poidsKg: Double?,
    val repetitions: Int,
) {
    val valeurs get() = ValeursPalier(poidsKg, repetitions)
}

/** Poids et répétitions d'un palier, hors base (saisie, édition, affichage). [poidsKg] vide = poids du corps. */
data class ValeursPalier(val poidsKg: Double?, val repetitions: Int)

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

/** Une série et ses paliers, triés par [Palier.ordre] : le premier est le palier principal. */
data class SerieAvecPaliers(
    @Embedded val serie: Serie,
    @Relation(parentColumn = "id", entityColumn = "serie_id")
    val paliers: List<Palier>,
) {
    val valeurs get() = paliers.map { it.valeurs }
}

/** Un exercice et ses séries, triées par [Serie.ordre]. */
data class ExerciceAvecSeries(
    @Embedded val exercice: Exercice,
    @Relation(entity = Serie::class, parentColumn = "id", entityColumn = "exercice_id")
    val series: List<SerieAvecPaliers>,
)

/** Un entraînement, ses exercices et leurs séries, triés par ordre (détail, export). */
data class EntrainementComplet(
    @Embedded val entrainement: Entrainement,
    @Relation(entity = Exercice::class, parentColumn = "id", entityColumn = "entrainement_id")
    val exercices: List<ExerciceAvecSeries>,
)

/** L'entraînement en cours, ses exercices, leurs séries et son chrono, lus ensemble (écran de saisie). */
data class EntrainementEnCours(
    @Embedded val entrainement: Entrainement,
    @Relation(entity = Exercice::class, parentColumn = "id", entityColumn = "entrainement_id")
    val exercices: List<ExerciceAvecSeries>,
    @Relation(parentColumn = "id", entityColumn = "entrainement_id")
    val chrono: EtatChrono?,
    /** Paliers d'une dégressive gardés par « Dégressive », triés par [PalierEnCours.ordre]. */
    @Relation(parentColumn = "id", entityColumn = "entrainement_id")
    val paliersEnCours: List<PalierEnCours> = emptyList(),
)

/** Un palier d'une série passée d'un exercice, avec sa série et sa séance (historique de l'exercice, spec §4.3.8). */
data class SerieHistorique(
    @ColumnInfo(name = "entrainement_id") val entrainementId: Long,
    val date: LocalDate,
    val libelle: String?,
    val nom: String,
    @ColumnInfo(name = "serie_id") val serieId: Long,
    @ColumnInfo(name = "poids_kg") val poidsKg: Double?,
    val repetitions: Int,
)

/** Room ne garantit pas l'ordre des relations : on trie après lecture. */
internal fun SerieAvecPaliers.trie() = copy(paliers = paliers.sortedBy { it.ordre })

internal fun ExerciceAvecSeries.trie() = copy(series = series.sortedBy { it.serie.ordre }.map { it.trie() })

internal fun List<ExerciceAvecSeries>.trie() = sortedBy { it.exercice.ordre }.map { it.trie() }

internal fun EntrainementComplet.trie() = copy(exercices = exercices.trie())

internal fun EntrainementEnCours.trie() = copy(exercices = exercices.trie(), paliersEnCours = paliersEnCours.sortedBy { it.ordre })
