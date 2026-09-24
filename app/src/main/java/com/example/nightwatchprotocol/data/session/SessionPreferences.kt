package com.example.nightwatchprotocol.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_preferences")

class SessionPreferences(private val dataStore: DataStore<Preferences>) {
    private val rememberSessionKey = booleanPreferencesKey("remember_session")

    val rememberSessionFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[rememberSessionKey] ?: true
    }

    suspend fun setRememberSession(remember: Boolean) {
        dataStore.edit { preferences -> preferences[rememberSessionKey] = remember }
    }
}
