package app.irontap

import android.app.Application
import app.irontap.notification.NotificationChrono

class IronTapApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChrono.creerCanal(this)
    }
}
