package com.example.nightwatchprotocol.data.notifications

import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCoordinatorTest {
    private val scheduler = FakeReminderScheduler()
    private val permission = FakeNotificationPermission()
    private val preferences = UserPreferences.inMemory()
    private val coordinator = ReminderCoordinator(scheduler, permission, preferences)

    @Test
    fun onlyTheTwoLocalRemindersHaveAKind() {
        assertEquals(ReminderKind.DAILY_PRACTICE, ReminderKind.forSetting(UserSetting.DAILY_REMINDER))
        assertEquals(ReminderKind.STREAK_AT_RISK, ReminderKind.forSetting(UserSetting.STREAK_AT_RISK))
        assertNull(ReminderKind.forSetting(UserSetting.ACHIEVEMENT_NOTIFICATIONS))
        assertNull(ReminderKind.forSetting(UserSetting.NEW_LABS))
        assertNull(ReminderKind.forSetting(UserSetting.NEWS_AND_UPDATES))
    }

    @Test
    fun turningAReminderOn_schedulesIt() {
        coordinator.apply(UserSetting.DAILY_REMINDER, true)

        assertEquals(setOf(ReminderKind.DAILY_PRACTICE), scheduler.active)
    }

    @Test
    fun turningItOff_cancelsTheScheduledWork() {
        coordinator.apply(UserSetting.STREAK_AT_RISK, true)

        coordinator.apply(UserSetting.STREAK_AT_RISK, false)

        assertTrue(scheduler.active.isEmpty())
        assertEquals(listOf(ReminderKind.STREAK_AT_RISK), scheduler.cancelled)
    }

    @Test
    fun withoutNotificationPermission_nothingIsScheduled() {
        permission.granted = false

        coordinator.apply(UserSetting.DAILY_REMINDER, true)

        assertTrue(scheduler.active.isEmpty())
    }

    @Test
    fun switchesWithoutALocalReminder_doNothing() {
        coordinator.apply(UserSetting.NEW_LABS, true)
        coordinator.apply(UserSetting.NEWS_AND_UPDATES, false)

        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun sync_schedulesTheEnabledOnesAndCancelsTheRest() = runTest {
        // Defaults: daily reminder and streak are on.
        preferences.set(UserSetting.STREAK_AT_RISK, false)

        coordinator.sync()

        assertEquals(setOf(ReminderKind.DAILY_PRACTICE), scheduler.active)
        assertTrue(ReminderKind.STREAK_AT_RISK in scheduler.cancelled)
    }

    @Test
    fun sync_withoutPermission_cancelsEverything() = runTest {
        scheduler.active += ReminderKind.entries
        permission.granted = false

        coordinator.sync()

        assertTrue(scheduler.active.isEmpty())
        assertFalse(scheduler.scheduled.isNotEmpty())
    }
}
