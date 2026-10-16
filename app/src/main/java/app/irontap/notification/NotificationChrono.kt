package app.irontap.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.irontap.MainActivity
import app.irontap.R
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.Horloge
import app.irontap.logique.formaterChrono
import app.irontap.ui.theme.Couleurs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Notification persistante du chrono de repos (spec §4.3.4, DECISIONS.md « Chronomètre et notification ») :
 * sans service, elle est (ré)affichée à chaque changement de l'entraînement en cours. En marche, le temps est
 * le chronomètre système, qui avance seul même appli tuée ; en pause, un texte figé.
 */
class NotificationChrono(
    private val context: Context,
    private val dao: EntrainementDao,
    private val horloge: Horloge,
) {
    private val gestionnaire = NotificationManagerCompat.from(context)

    /** Permission de notifier (toujours accordée avant Android 13). */
    fun autorisee(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Suit l'entraînement en cours tant que l'appelant vit (activité) : chaque changement met la notification à jour. */
    suspend fun suivre() {
        dao.observerEnCoursComplet().map(ContenuNotification::de).distinctUntilChanged().collect(::afficher)
    }

    /** Réaffiche la notification d'après la base (permission accordée, action du récepteur). */
    suspend fun actualiser() = afficher(ContenuNotification.de(dao.lireEnCoursComplet()))

    /** Action d'un bouton de la notification, sur l'entraînement en cours. [realtime] : instant de l'appui. */
    suspend fun executer(action: ActionChrono, realtime: Long) {
        try {
            dao.lireEnCours()?.let { entrainement -> dao.modifierChrono(entrainement.id) { action.appliquer(it, realtime) } }
            actualiser()
        } catch (e: SQLiteException) {
            // Stockage indisponible : la notification reste telle quelle, l'écran de saisie signalera l'erreur.
        }
    }

    fun afficher(contenu: ContenuNotification?) {
        if (contenu == null) {
            gestionnaire.cancel(ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gestionnaire.notify(ID, construire(contenu))
    }

    internal fun construire(contenu: ContenuNotification): Notification {
        val ecouleMs = contenu.chrono.ecouleMs(horloge.realtime())
        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_barbell_compact)
            .setColor(Couleurs.Accent.toArgb())
            .setSubText("Chrono de repos")
            .setContentTitle(if (contenu.enCours) "Repos en cours" else "${formaterChrono(ecouleMs)} · En pause")
            .setContentText(contenu.ligne)
            .setContentIntent(ouvrirSaisie())
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .apply {
                if (contenu.enCours) {
                    // Le chronomètre système compte à partir de `when` (heure murale).
                    setUsesChronometer(true)
                    setWhen(horloge.maintenant().toEpochMilli() - ecouleMs)
                    setShowWhen(true)
                } else {
                    setShowWhen(false)
                }
            }
            .addAction(
                if (contenu.enCours) action(R.drawable.ic_pause, "Pause", ActionChrono.Pause)
                else action(R.drawable.ic_play, "Reprendre", ActionChrono.Reprendre),
            )
            .addAction(action(R.drawable.ic_arrow_counter_clockwise, "Relancer", ActionChrono.Relancer))
            .build()
    }

    /**
     * Intentions explicites et immuables : seule l'appli peut les recevoir, personne ne peut les modifier.
     * Ouvrir la saisie ramène l'activité existante au premier plan (CLEAR_TOP + SINGLE_TOP), même si une autre
     * appli s'est ouverte par-dessus dans la même tâche (partage d'un export), au lieu d'en empiler une seconde.
     */
    private fun ouvrirSaisie(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OUVRIR_SAISIE)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(@DrawableRes icone: Int, titre: String, action: ActionChrono) = NotificationCompat.Action(
        icone,
        titre,
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RecepteurChrono::class.java).setAction(action.intentAction),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    )

    companion object {
        const val CANAL = "chrono"
        const val ID = 1

        /** Canal discret : ni son ni vibration, pas de pastille sur l'icône. Sans effet s'il existe déjà. */
        fun creerCanal(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Chrono de repos")
                    .setDescription("Temps de repos de l’entraînement en cours")
                    .setShowBadge(false)
                    .build(),
            )
        }
    }
}
