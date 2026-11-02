package com.example.nightwatchprotocol.data.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageRepositoryTest {

    @Test
    fun spanishIsTheDefaultLanguage() {
        assertEquals(AppLanguage.ES, AppLanguage.DEFAULT)
    }

    @Test
    fun aTag_mapsToItsLanguageIgnoringTheRegion() {
        assertEquals(AppLanguage.ES, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.ES, AppLanguage.fromTag("es-CO"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("EN"))
        assertNull(AppLanguage.fromTag("fr"))
        assertNull(AppLanguage.fromTag(null))
    }

    @Test
    fun theFake_startsWithNoLanguageAndKeepsTheOneSet() {
        val repository = FakeLanguageRepository()
        assertNull(repository.currentLanguage())

        repository.setLanguage(AppLanguage.EN)

        assertEquals(AppLanguage.EN, repository.currentLanguage())
    }
}
