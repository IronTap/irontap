package app.irontap.donnees

import android.os.SystemClock
import java.time.Instant

/** Sources de temps, remplaçables dans les tests. */
interface Horloge {
    /** Heure murale : horodatages enregistrés en base (`cree_le`, `termine_le`…). */
    fun maintenant(): Instant

    /** `SystemClock.elapsedRealtime()` : base du chrono, insensible aux changements d'heure. */
    fun realtime(): Long
}

object HorlogeSysteme : Horloge {
    override fun maintenant(): Instant = Instant.now()
    override fun realtime(): Long = SystemClock.elapsedRealtime()
}
