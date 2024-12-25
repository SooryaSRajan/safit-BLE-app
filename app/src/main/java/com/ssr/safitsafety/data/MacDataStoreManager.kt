package com.ssr.safitsafety.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssr.safitsafety.MainActivity.Companion.MAC_PREF_KEY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object MacDataStoreManager {
    private const val DATASTORE_NAME = "settings"

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val MAC_ADDRESS_KEY = stringPreferencesKey(MAC_PREF_KEY)

    suspend fun saveMacAddress(context: Context, macAddress: String) {
        context.dataStore.edit { preferences ->
            preferences[MAC_ADDRESS_KEY] = macAddress
        }
    }

    fun getMacAddress(context: Context): Flow<String?> {
        return context.dataStore.data
            .map { preferences -> preferences[MAC_ADDRESS_KEY] }
    }

    suspend fun clearMacAddress(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(MAC_ADDRESS_KEY)
        }
    }
}
