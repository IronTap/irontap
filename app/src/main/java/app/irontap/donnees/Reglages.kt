package app.irontap.donnees

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Petites préférences de l'application (DataStore). */
class Reglages(private val store: DataStore<Preferences>) {

    /** L'écran 9 (pré-permission des notifications) a déjà été montré : il ne l'est qu'une fois. */
    val prePermissionMontree: Flow<Boolean> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PRE_PERMISSION_MONTREE] ?: false }

    /** Stockage plein ou fichier illisible : rien n'est mémorisé, l'écran 9 sera simplement reproposé. */
    suspend fun marquerPrePermissionMontree() {
        try {
            store.edit { it[PRE_PERMISSION_MONTREE] = true }
        } catch (e: IOException) {
            // Cf. KDoc.
        }
    }

    private companion object {
        val PRE_PERMISSION_MONTREE = booleanPreferencesKey("pre_permission_notifications_montree")
    }
}
