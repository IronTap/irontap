package app.irontap

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.irontap.ui.IronTapApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Nombre d'appuis sur la notification du chrono : chacun ramène à l'écran de saisie. */
    private var demandesSaisie by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Thème sombre uniquement : icônes claires dans les barres système, quel que soit le réglage du téléphone.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Après une recréation (rotation…), l'intention d'origine a déjà été traitée.
        if (savedInstanceState == null) traiter(intent)
        // Tant que l'activité vit, la notification suit les changements faits depuis l'écran.
        val notification = (application as IronTapApplication).container.notificationChrono
        lifecycleScope.launch { notification.suivre() }
        setContent { IronTapApp(demandesSaisie) }
    }

    // `launchMode="singleTop"` : un appui sur la notification, appli ouverte, arrive ici.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        traiter(intent)
    }

    private fun traiter(intent: Intent?) {
        if (intent?.action == ACTION_OUVRIR_SAISIE) demandesSaisie++
    }

    companion object {
        const val ACTION_OUVRIR_SAISIE = "app.irontap.action.OUVRIR_SAISIE"
    }
}
