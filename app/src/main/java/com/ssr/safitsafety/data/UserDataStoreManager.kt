package com.ssr.safitsafety.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssr.safitsafety.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object UserDataStoreManager {
    private const val DATASTORE_NAME = "user-data"

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val USER_NAME_PREF_KEY = stringPreferencesKey(MainActivity.USER_NAME_PREF_KEY)
    private val USER_WEIGHT_KEY = intPreferencesKey(MainActivity.USER_WEIGHT_PREF_KEY)
    private val USER_AGE_KEY = intPreferencesKey(MainActivity.USER_AGE_PREF_KEY)
    private val EMERGENCY_CONTACTS_PREF_KEY =
        stringPreferencesKey(MainActivity.EMERGENCY_CONTACTS_PREF_KEY)
    private val FORCE_UPDATE_KEY = intPreferencesKey("force_update_key")

    private fun serializePhoneNumbers(numbers: List<String>): String {
        return numbers.joinToString(",")
    }

    private fun deserializePhoneNumbers(serialized: String?): List<String> {
        return when {
            serialized.isNullOrEmpty() -> emptyList()
            else -> serialized.split(",")
        }
    }

    suspend fun saveUserData(context: Context, userData: UserData): Result<Unit> {
        Log.d("UserData", "Save has been triggered")
        return try {
            context.dataStore.edit { preferences ->
                preferences[USER_NAME_PREF_KEY] = userData.name
                preferences[USER_WEIGHT_KEY] = userData.weight
                preferences[USER_AGE_KEY] = userData.age
                preferences[EMERGENCY_CONTACTS_PREF_KEY] =
                    serializePhoneNumbers(userData.phoneNumber)
                preferences[FORCE_UPDATE_KEY] = (preferences[FORCE_UPDATE_KEY] ?: 0) + 1
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
                    preferences[USER_NAME_PREF_KEY] ?: "",
                    preferences[USER_WEIGHT_KEY] ?: 0,
                    preferences[USER_AGE_KEY] ?: 0,
                    deserializePhoneNumbers(preferences[EMERGENCY_CONTACTS_PREF_KEY])
                )
            }
    }

    suspend fun getUserDataImmediately(context: Context): UserData? {
        return try {
            val preferences =
                context.dataStore.data.first() // This suspends and retrieves the current data
            UserData(
                preferences[USER_NAME_PREF_KEY] ?: "",
                preferences[USER_WEIGHT_KEY] ?: 0,
                preferences[USER_AGE_KEY] ?: 0,
                deserializePhoneNumbers(preferences[EMERGENCY_CONTACTS_PREF_KEY])
            )
        } catch (e: Exception) {
            Log.e("UserData", "Error getting user data", e)
            null
        }
    }
}
