package com.example.nightwatchprotocol.data.device

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nightwatchprotocol.data.preferences.InMemoryPreferencesDataStore
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first

private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

/** A random id for this installation, created once and kept in DataStore. */
class DeviceIdStore(
    private val dataStore: DataStore<Preferences>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun getOrCreate(): String {
        dataStore.data.first()[DEVICE_ID_KEY]?.let { return it }
        var created: String? = null
        dataStore.edit { preferences ->
            // Re-check inside the edit so two concurrent callers end up with the same id.
            created = preferences[DEVICE_ID_KEY] ?: newId().also { preferences[DEVICE_ID_KEY] = it }
        }
        return checkNotNull(created)
    }

    companion object {
        fun inMemory(newId: () -> String = { UUID.randomUUID().toString() }) =
            DeviceIdStore(InMemoryPreferencesDataStore(), newId)
    }
}

interface DeviceInfoProvider {
    fun deviceName(): String
}

class AndroidDeviceInfoProvider : DeviceInfoProvider {
    override fun deviceName(): String = formatDeviceName(Build.MANUFACTURER, Build.MODEL)
}

/** "Samsung SM-S938B", without repeating the maker when the model already starts with it; at most 80 characters. */
fun formatDeviceName(manufacturer: String?, model: String?): String {
    val maker = manufacturer?.trim().orEmpty()
    val phone = model?.trim().orEmpty()
    val name = when {
        phone.isEmpty() && maker.isEmpty() -> "Dispositivo Android"
        phone.isEmpty() -> maker
        phone.startsWith(maker, ignoreCase = true) -> phone
        else -> "$maker $phone"
    }
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }.take(80)
}
