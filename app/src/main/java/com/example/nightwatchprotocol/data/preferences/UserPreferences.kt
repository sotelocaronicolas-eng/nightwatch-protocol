package com.example.nightwatchprotocol.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/** Every on/off setting stored on this device, with the default shown in the Figma designs. */
enum class UserSetting(val storageKey: String, val default: Boolean) {
    DAILY_REMINDER("daily_reminder", true),
    ACHIEVEMENT_NOTIFICATIONS("achievement_notifications", true),
    STREAK_AT_RISK("streak_at_risk", true),
    NEW_LABS("new_labs", false),
    NEWS_AND_UPDATES("news_and_updates", false),
    HIDE_PUBLIC_PROGRESS("hide_public_progress", false),
    BIOMETRIC_LOCK("biometric_lock_enabled", false),
}

data class UserSettings(private val values: Map<UserSetting, Boolean> = emptyMap()) {
    operator fun get(setting: UserSetting): Boolean = values[setting] ?: setting.default
}

/** Per-device settings (not per account), in their own DataStore, separate from `session_preferences`. */
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data.map { preferences ->
        UserSettings(UserSetting.entries.associateWith { preferences[it.preferencesKey()] ?: it.default })
    }

    suspend fun set(setting: UserSetting, value: Boolean) {
        dataStore.edit { it[setting.preferencesKey()] = value }
    }

    private fun UserSetting.preferencesKey() = booleanPreferencesKey(storageKey)

    companion object {
        fun inMemory(): UserPreferences = UserPreferences(InMemoryPreferencesDataStore())
    }
}

/** Test double for any DataStore-backed class in this module. */
internal class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
