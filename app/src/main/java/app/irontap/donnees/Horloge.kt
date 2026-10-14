package app.irontap.donnees

import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sources de temps, remplaçables dans les tests. */
interface Horloge {
    /** Heure murale : horodatages enregistrés en base (`cree_le`, `termine_le`…). */
    fun maintenant(): Instant

    /** `SystemClock.elapsedRealtime()` : base du chrono, insensible aux changements d'heure. */
    fun realtime(): Long

    /** Date du jour dans le fuseau du téléphone. */
    fun aujourdhui(): LocalDate = maintenant().atZone(ZoneId.systemDefault()).toLocalDate()
}

object HorlogeSysteme : Horloge {
    override fun maintenant(): Instant = Instant.now()
    override fun realtime(): Long = SystemClock.elapsedRealtime()
}
