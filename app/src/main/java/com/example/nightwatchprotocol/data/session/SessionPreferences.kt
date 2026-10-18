package com.example.nightwatchprotocol.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    private val recoveryInProgressKey = booleanPreferencesKey("password_recovery_in_progress")

    /**
     * True from the moment a password-recovery code is verified (which opens a session without a typed password) until
     * the new password is saved or the user backs out. If it is still set when the app starts, that session was
     * abandoned and the startup closes it.
     */
    suspend fun isRecoveryInProgress(): Boolean = dataStore.data.first()[recoveryInProgressKey] ?: false

    suspend fun setRecoveryInProgress(inProgress: Boolean) {
        dataStore.edit { preferences ->
            if (inProgress) preferences[recoveryInProgressKey] = true else preferences.remove(recoveryInProgressKey)
        }
    }

    companion object {
        fun inMemory(initialRememberSession: Boolean = true): SessionPreferences =
            SessionPreferences(InMemoryPreferencesDataStore(initialRememberSession))
    }
}

private class InMemoryPreferencesDataStore(
    initialRememberSession: Boolean,
) : DataStore<Preferences> {
    private val key = booleanPreferencesKey("remember_session")
    private val state = MutableStateFlow(preferencesOf(key to initialRememberSession))

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
