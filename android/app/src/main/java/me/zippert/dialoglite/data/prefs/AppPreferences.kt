package me.zippert.dialoglite.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dialoglite")

/**
 * Guarda apenas o endereco base. Nao existe token, senha ou segredo no app:
 * o servidor nao tem autenticacao e o acesso e restrito pela mesh netbird —
 * a mesh E a autenticacao. O APK e publicado num repositorio publico.
 */
class AppPreferences(private val context: Context) : PreferencesSource {

    override val baseUrl: Flow<String?> = context.dataStore.data.map { it[KEY_BASE_URL]?.ifBlank { null } }

    override suspend fun setBaseUrl(value: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = value.trim() }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
    }
}
