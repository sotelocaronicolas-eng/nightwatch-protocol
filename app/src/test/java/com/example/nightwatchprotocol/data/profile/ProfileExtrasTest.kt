package com.example.nightwatchprotocol.data.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileExtrasTest {
    private val profile = FakeProfileRepository()
    private val cache = DataStoreProfileExtrasCache.inMemory()
    private val repository = ProfileExtrasRepository(profile, cache)

    // ---- Gender ----

    @Test
    fun theGenderOptions_areTheFourFromTheSpec_withTheirLabels() {
        assertEquals(
            listOf("Masculino", "Femenino", "Otro", "Prefiero no decir"),
            Gender.entries.map { it.label },
        )
        assertEquals(listOf("male", "female", "other", "prefer_not_to_say"), Gender.entries.map { it.key })
    }

    @Test
    fun aGenderIsRestoredFromItsKey_andUnknownKeysAreIgnored() {
        Gender.entries.forEach { assertEquals(it, Gender.fromKey(it.key)) }
        assertNull(Gender.fromKey("alien"))
        assertNull(Gender.fromKey(""))
        assertNull(Gender.fromKey(null))
        assertNull(Gender.fromKey("Masculino")) // only the keys are stored, never the labels
    }

    // ---- Local cache ----

    @Test
    fun theCache_startsEmptyAndRemembersWhatWasWritten() = runTest {
        assertNull(cache.read("uid-1"))

        cache.write("uid-1", ProfileExtras(Gender.MALE, BirthDate(1990, 1, 2)))

        assertEquals(ProfileExtras(Gender.MALE, BirthDate(1990, 1, 2)), cache.read("uid-1"))
    }

    @Test
    fun theCache_keepsOneEntryPerUser() = runTest {
        cache.write("uid-1", ProfileExtras(Gender.MALE, null))
        cache.write("uid-2", ProfileExtras(Gender.FEMALE, BirthDate(2001, 3, 4)))

        assertEquals(ProfileExtras(Gender.MALE, null), cache.read("uid-1"))
        assertEquals(ProfileExtras(Gender.FEMALE, BirthDate(2001, 3, 4)), cache.read("uid-2"))
    }

    @Test
    fun clearingAFieldInTheCache_removesIt() = runTest {
        cache.write("uid-1", ProfileExtras(Gender.OTHER, BirthDate(1980, 6, 7)))

        cache.write("uid-1", ProfileExtras(null, null))

        assertEquals(ProfileExtras(null, null), cache.read("uid-1"))
    }

    // ---- Repository ----

    @Test
    fun load_showsFirestoreValuesAndCachesThem() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(gender = "female", birthDate = "1999-03-07"))

        repository.load("uid-1")

        assertEquals(ProfileExtras(Gender.FEMALE, BirthDate(1999, 3, 7)), repository.extras.value)
        assertEquals(ProfileExtras(Gender.FEMALE, BirthDate(1999, 3, 7)), cache.read("uid-1"))
    }

    @Test
    fun load_whenOffline_keepsTheLocalCopy() = runTest {
        cache.write("uid-1", ProfileExtras(Gender.OTHER, BirthDate(1985, 5, 5)))
        profile.getProfileResult = Result.failure(RuntimeException("sin red"))

        repository.load("uid-1")

        assertEquals(ProfileExtras(Gender.OTHER, BirthDate(1985, 5, 5)), repository.extras.value)
    }

    @Test
    fun load_ignoresInvalidStoredValues() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(gender = "x", birthDate = "2001-02-30"))

        repository.load("uid-1")

        assertEquals(ProfileExtras(), repository.extras.value)
    }

    @Test
    fun anotherUser_neverSeesThePreviousUsersData() = runTest {
        cache.write("uid-1", ProfileExtras(Gender.MALE, BirthDate(1990, 1, 1)))
        repository.load("uid-1")
        profile.getProfileResult = Result.failure(RuntimeException("sin red"))

        repository.load("uid-2")

        assertEquals(ProfileExtras(), repository.extras.value)
    }

    @Test
    fun save_writesTheCacheFirstAndThenFirestoreWithTheRightFormats() = runTest {
        val result = repository.save("uid-1", ProfileExtras(Gender.PREFER_NOT_TO_SAY, BirthDate(2000, 5, 7)))

        assertEquals(ProfileSyncResult.Synced, result)
        assertEquals(ProfileExtras(Gender.PREFER_NOT_TO_SAY, BirthDate(2000, 5, 7)), repository.extras.value)
        assertEquals(ProfileExtras(Gender.PREFER_NOT_TO_SAY, BirthDate(2000, 5, 7)), cache.read("uid-1"))
        assertEquals(listOf("prefer_not_to_say" to "2000-05-07"), profile.savedExtras)
    }

    @Test
    fun save_withNoValues_sendsNullsSoFirestoreDeletesTheFields() = runTest {
        repository.save("uid-1", ProfileExtras(null, null))

        assertEquals(listOf<Pair<String?, String?>>(null to null), profile.savedExtras)
    }

    @Test
    fun save_whenFirestoreFails_keepsTheLocalCopy() = runTest {
        profile.saveExtrasResult = Result.failure(RuntimeException("sin red"))

        val result = repository.save("uid-1", ProfileExtras(Gender.MALE, BirthDate(1990, 1, 2)))

        assertEquals(ProfileSyncResult.LocalOnly, result)
        assertEquals(ProfileExtras(Gender.MALE, BirthDate(1990, 1, 2)), cache.read("uid-1"))
        assertEquals(ProfileExtras(Gender.MALE, BirthDate(1990, 1, 2)), repository.extras.value)
    }

    @Test
    fun current_triesTheCacheAndFirestore() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(gender = "male"))

        assertEquals(ProfileExtras(Gender.MALE, null), repository.current("uid-1"))
    }
}
