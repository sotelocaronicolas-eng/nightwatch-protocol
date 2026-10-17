package com.example.nightwatchprotocol.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendTest {

    @Test
    fun firebase_isParsed() {
        assertEquals(Backend.FIREBASE, parseBackend("firebase"))
    }

    @Test
    fun supabase_isParsed() {
        assertEquals(Backend.SUPABASE, parseBackend("supabase"))
    }

    @Test
    fun parsing_ignoresCase() {
        assertEquals(Backend.SUPABASE, parseBackend("SUPABASE"))
        assertEquals(Backend.SUPABASE, parseBackend("Supabase"))
        assertEquals(Backend.FIREBASE, parseBackend("FireBase"))
    }

    @Test
    fun parsing_ignoresSurroundingSpaces() {
        assertEquals(Backend.SUPABASE, parseBackend("  supabase "))
    }

    @Test
    fun anEmptyValue_fallsBackToFirebase() {
        assertEquals(Backend.FIREBASE, parseBackend(""))
        assertEquals(Backend.FIREBASE, parseBackend("   "))
    }

    @Test
    fun aNullValue_fallsBackToFirebase() {
        assertEquals(Backend.FIREBASE, parseBackend(null))
    }

    @Test
    fun anUnknownValue_fallsBackToFirebase() {
        assertEquals(Backend.FIREBASE, parseBackend("mongodb"))
        assertEquals(Backend.FIREBASE, parseBackend("supabase2"))
    }

    @Test
    fun completeSupabaseConfig_hasNoError() {
        assertNull(supabaseConfigError("https://abc.supabase.co", "sb_publishable_x"))
    }

    @Test
    fun aMissingUrl_isNamedInTheError() {
        val error = supabaseConfigError("", "sb_publishable_x")!!

        assertTrue(error.contains("SUPABASE_URL"))
        assertTrue(!error.contains("SUPABASE_PUBLISHABLE_KEY"))
        assertTrue(error.contains("local.properties"))
    }

    @Test
    fun aMissingKey_isNamedInTheError() {
        val error = supabaseConfigError("https://abc.supabase.co", "")!!

        assertTrue(error.contains("SUPABASE_PUBLISHABLE_KEY"))
        assertTrue(!error.contains("SUPABASE_URL"))
    }

    @Test
    fun whenBothAreMissing_bothAreNamed() {
        val error = supabaseConfigError(" ", "")!!

        assertTrue(error.contains("SUPABASE_URL"))
        assertTrue(error.contains("SUPABASE_PUBLISHABLE_KEY"))
    }
}
