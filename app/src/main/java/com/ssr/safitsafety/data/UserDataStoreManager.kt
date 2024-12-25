package com.ssr.safitsafety.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssr.safitsafety.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object UserDataStoreManager {
    private const val DATASTORE_NAME = "user-data"
    
    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val USER_WEIGHT_KEY = floatPreferencesKey(MainActivity.USER_WEIGHT_PREF_KEY)
    private val USER_AGE_KEY = intPreferencesKey(MainActivity.USER_AGE_PREF_KEY)

    suspend fun saveUserData(context: Context, userData: UserData): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[USER_WEIGHT_KEY] = userData.weight
                preferences[USER_AGE_KEY] = userData.age
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserData(context: Context): Flow<UserData?> {
        return context.dataStore.data
            .map { preferences ->
                UserData(
                    preferences[USER_WEIGHT_KEY] ?: 0f,
                    preferences[USER_AGE_KEY] ?: 0
                )
            }
    }

    suspend fun clearUserData(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_WEIGHT_KEY)
            preferences.remove(USER_AGE_KEY)
        }
    }

}