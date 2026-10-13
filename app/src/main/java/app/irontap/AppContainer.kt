package app.irontap

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

/**
 * Conteneur de dépendances manuel (pas de bibliothèque d'injection).
 * Créé une seule fois par [IronTapApplication]. La base Room y sera ajoutée avec le modèle de données.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val preferences: DataStore<Preferences> = appContext.preferencesStore
}
