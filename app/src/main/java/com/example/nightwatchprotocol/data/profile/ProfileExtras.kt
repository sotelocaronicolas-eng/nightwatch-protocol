package com.example.nightwatchprotocol.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nightwatchprotocol.data.preferences.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Optional personal data of the profile. Both are personal data: never put them in logs or error messages. */
data class ProfileExtras(
    val gender: Gender? = null,
    val birthDate: BirthDate? = null,
)

enum class ProfileSyncResult { Synced, LocalOnly }

/** Local copy of the extras, one per user, so they show instantly and offline. */
interface ProfileExtrasCache {
    /** Null when nothing was ever cached for [uid] (as opposed to "cached, and both are empty"). */
    suspend fun read(uid: String): ProfileExtras?

    suspend fun write(uid: String, extras: ProfileExtras)
}

class DataStoreProfileExtrasCache(private val dataStore: DataStore<Preferences>) : ProfileExtrasCache {

    override suspend fun read(uid: String): ProfileExtras? {
        val preferences = dataStore.data.first()
        if (preferences[cachedKey(uid)] != true) return null
        return ProfileExtras(
            gender = Gender.fromKey(preferences[genderKey(uid)]),
            birthDate = BirthDate.parseIso(preferences[birthDateKey(uid)]),
        )
    }

    override suspend fun write(uid: String, extras: ProfileExtras) {
        dataStore.edit { preferences ->
            preferences[cachedKey(uid)] = true
            extras.gender?.let { preferences[genderKey(uid)] = it.key } ?: preferences.remove(genderKey(uid))
            extras.birthDate?.let { preferences[birthDateKey(uid)] = it.iso() } ?: preferences.remove(birthDateKey(uid))
        }
    }

    private fun cachedKey(uid: String) = booleanPreferencesKey("profile_extras_cached_$uid")
    private fun genderKey(uid: String) = stringPreferencesKey("profile_extras_gender_$uid")
    private fun birthDateKey(uid: String) = stringPreferencesKey("profile_extras_birth_date_$uid")

    companion object {
        fun inMemory() = DataStoreProfileExtrasCache(InMemoryPreferencesDataStore())
    }
}

/**
 * Single source of the user's gender and birth date for every screen. Shows the local copy first, then refreshes
 * from `users/{uid}/profile/main`; a save writes the local copy first and then Firestore.
 */
class ProfileExtrasRepository(
    private val profileRepository: ProfileRepository,
    private val cache: ProfileExtrasCache,
) {
    private val _extras = MutableStateFlow(ProfileExtras())
    val extras: StateFlow<ProfileExtras> = _extras.asStateFlow()

    private var loadedUid: String? = null

    /** A different uid empties the state first, so one account never sees another's data. */
    suspend fun load(uid: String) {
        if (loadedUid != uid) {
            loadedUid = uid
            _extras.value = ProfileExtras()
        }
        cache.read(uid)?.let { if (loadedUid == uid) _extras.value = it }

        val remote = runCatchingKeepCancellation { profileRepository.getProfile(uid) }.getOrNull() ?: return
        val fresh = ProfileExtras(Gender.fromKey(remote.gender), BirthDate.parseIso(remote.birthDate))
        if (loadedUid != uid) return
        cache.write(uid, fresh)
        _extras.value = fresh
    }

    /** Remote failure keeps the local copy and reports [ProfileSyncResult.LocalOnly]; Firestore may sync it later. */
    suspend fun save(uid: String, extras: ProfileExtras): ProfileSyncResult {
        loadedUid = uid
        cache.write(uid, extras)
        _extras.value = extras
        val synced = runCatchingKeepCancellation {
            profileRepository.saveExtras(uid, extras.gender?.key, extras.birthDate?.iso())
        }.isSuccess
        return if (synced) ProfileSyncResult.Synced else ProfileSyncResult.LocalOnly
    }

    /** What the screens should show right now, trying the cache and then Firestore. */
    suspend fun current(uid: String): ProfileExtras {
        load(uid)
        return _extras.value
    }
}
