package com.example.nightwatchprotocol.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    private val url = "https://abc.supabase.co"
    private val key = "sb_publishable_x"
    private val client = "123.apps.googleusercontent.com"

    @Test
    fun completeSettings_haveNoError() {
        assertNull(appConfigError(url, key, client))
    }

    @Test
    fun aMissingUrl_isNamed() {
        val message = appConfigError("", key, client)!!
        assertTrue(message.contains("SUPABASE_URL"))
        assertFalse(message.contains("SUPABASE_PUBLISHABLE_KEY"))
        assertFalse(message.contains("GOOGLE_WEB_CLIENT_ID"))
    }

    @Test
    fun aMissingGoogleClientId_isNamed() {
        val message = appConfigError(url, key, "")!!
        assertTrue(message.contains("GOOGLE_WEB_CLIENT_ID"))
        assertFalse(message.contains("SUPABASE_URL"))
    }

    @Test
    fun everythingMissing_isNamed_andTheMessageSaysWhereToFixIt() {
        val message = appConfigError("  ", "", " ")!!
        assertTrue(message.contains("SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, GOOGLE_WEB_CLIENT_ID"))
        assertTrue(message.contains("local.properties.example"))
    }

    @Test
    fun aBlankKeyCountsAsMissing() {
        assertEquals(true, appConfigError(url, " ", client)?.contains("SUPABASE_PUBLISHABLE_KEY"))
    }
}
