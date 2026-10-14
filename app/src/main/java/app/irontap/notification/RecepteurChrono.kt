package app.irontap.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.irontap.IronTapApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boutons Pause / Reprendre / Relancer de la notification. Non exporté (manifeste) : seules les intentions
 * explicites de [NotificationChrono] l'atteignent. Met à jour la base, puis réaffiche la notification.
 */
class RecepteurChrono : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = ActionChrono.depuis(intent.action) ?: return
        val conteneur = (context.applicationContext as IronTapApplication).container
        val realtime = conteneur.horloge.realtime()
        // L'écriture en base ne doit pas bloquer le fil principal ; le système garde le processus jusqu'à finish().
        val resultat = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                conteneur.notificationChrono.executer(action, realtime)
            } finally {
                resultat.finish()
            }
        }
    }
}
