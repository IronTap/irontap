package app.irontap

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.Horloge
import app.irontap.donnees.HorlogeSysteme
import app.irontap.donnees.Reglages
import app.irontap.export.Exporteur
import app.irontap.notification.NotificationChrono
import app.irontap.ui.saisie.BrouillonSaisie

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

/**
 * Conteneur de dépendances manuel (pas de bibliothèque d'injection).
 * Créé une seule fois par [IronTapApplication].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val reglages = Reglages(appContext.preferencesStore)

    val base: BaseIronTap by lazy { BaseIronTap.creer(appContext) }

    val horloge: Horloge = HorlogeSysteme

    val notificationChrono: NotificationChrono by lazy { NotificationChrono(appContext, base.entrainements(), horloge) }

    val exporteur = Exporteur(appContext)

    /** Saisie en cours sur l'écran 3, conservée tant que l'application vit. */
    val brouillonSaisie = BrouillonSaisie()
}
