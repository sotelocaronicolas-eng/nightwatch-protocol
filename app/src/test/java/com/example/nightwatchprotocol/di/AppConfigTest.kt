package com.example.nightwatchprotocol.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun completeSettings_haveNoError() {
        assertNull(supabaseConfigError("https://abc.supabase.co", "sb_publishable_x"))
    }

    @Test
    fun aMissingUrl_isNamed() {
        val message = supabaseConfigError("", "sb_publishable_x")!!
        assertTrue(message.contains("SUPABASE_URL"))
        assertTrue(!message.contains("SUPABASE_PUBLISHABLE_KEY"))
    }

    @Test
    fun bothMissing_areNamed_andTheMessageSaysWhereToFixIt() {
        val message = supabaseConfigError("  ", "")!!
        assertTrue(message.contains("SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY"))
        assertTrue(message.contains("local.properties.example"))
    }

    @Test
    fun aBlankKeyCountsAsMissing() {
        assertEquals(true, supabaseConfigError("https://abc.supabase.co", " ")?.contains("SUPABASE_PUBLISHABLE_KEY"))
    }
}
