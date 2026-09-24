package com.example.nightwatchprotocol.data.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionPreferencesTest {
    @Test
    fun defaultValue_isTrue() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = SessionPreferences(context.sessionDataStore)

        assertTrue(preferences.rememberSessionFlow.first())
    }

    @Test
    fun setRememberSession_persistsValue() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = SessionPreferences(context.sessionDataStore)

        preferences.setRememberSession(false)

        assertEquals(false, preferences.rememberSessionFlow.first())

        preferences.setRememberSession(true)
    }
}
