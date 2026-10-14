package app.irontap.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.irontap.donnees.EtatChrono
import kotlinx.coroutines.delay

/**
 * Temps écoulé du chrono, rafraîchi à chaque changement de seconde tant qu'il tourne et que l'écran
 * est visible. Au retour au premier plan (téléphone rallumé…), la valeur est recalculée aussitôt.
 */
@Composable
fun tempsEcouleMs(chrono: EtatChrono?, realtime: () -> Long): Long {
    var maintenant by remember { mutableLongStateOf(realtime()) }
    val cycleDeVie = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(chrono, cycleDeVie) {
        cycleDeVie.repeatOnLifecycle(Lifecycle.State.STARTED) {
            maintenant = realtime()
            while (chrono?.etat == EtatChrono.Etat.EnCours) {
                delay(1_000 - chrono.ecouleMs(maintenant) % 1_000)
                maintenant = realtime()
            }
        }
    }
    return chrono?.ecouleMs(maintenant) ?: 0
}
