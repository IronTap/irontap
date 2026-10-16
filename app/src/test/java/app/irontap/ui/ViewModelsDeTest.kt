package app.irontap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * ViewModels créés par un test. [vider] les détruit comme le ferait l'écran, ce qui annule leurs coroutines
 * (`viewModelScope`) : à appeler avant de fermer la base, sinon un `stateIn` encore actif la relit après fermeture.
 */
class ViewModelsDeTest {

    private val magasin = ViewModelStore()
    private var compteur = 0

    fun <T : ViewModel> garder(vm: T): T {
        val fabrique = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = vm as V
        }
        return ViewModelProvider(magasin, fabrique)["vm${compteur++}", vm.javaClass]
    }

    fun vider() = magasin.clear()
}
