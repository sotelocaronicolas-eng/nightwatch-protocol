package com.example.nightwatchprotocol.data.preferences

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {
    @Test
    fun defaultsMatchTheFigmaDesign() = runTest {
        val settings = UserPreferences.inMemory().settings.first()

        assertTrue(settings[UserSetting.DAILY_REMINDER])
        assertTrue(settings[UserSetting.ACHIEVEMENT_NOTIFICATIONS])
        assertTrue(settings[UserSetting.STREAK_AT_RISK])
        assertFalse(settings[UserSetting.NEW_LABS])
        assertFalse(settings[UserSetting.NEWS_AND_UPDATES])
        assertFalse(settings[UserSetting.HIDE_PUBLIC_PROGRESS])
        assertFalse(settings[UserSetting.BIOMETRIC_LOCK])
    }

    @Test
    fun aChangedSetting_isPersistedAndOthersAreUntouched() = runTest {
        val preferences = UserPreferences.inMemory()

        preferences.set(UserSetting.HIDE_PUBLIC_PROGRESS, true)
        preferences.set(UserSetting.DAILY_REMINDER, false)
        val settings = preferences.settings.first()

        assertTrue(settings[UserSetting.HIDE_PUBLIC_PROGRESS])
        assertFalse(settings[UserSetting.DAILY_REMINDER])
        assertTrue(settings[UserSetting.STREAK_AT_RISK])
    }

    @Test
    fun aSettingCanBeTurnedBackOff() = runTest {
        val preferences = UserPreferences.inMemory()
        preferences.set(UserSetting.BIOMETRIC_LOCK, true)

        preferences.set(UserSetting.BIOMETRIC_LOCK, false)

        assertFalse(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
    }

    @Test
    fun everySettingHasADistinctStorageKey() {
        val keys = UserSetting.entries.map { it.storageKey }

        assertTrue(keys.size == keys.toSet().size)
    }
}
