package com.example.nightwatchprotocol.data.common

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.TestStrings
import com.example.nightwatchprotocol.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiTextTest {

    /** Ids made up for the test: the resolver is a fake, so no real resource is needed. */
    private val fake = object : TextResolver {
        override fun string(id: Int, args: List<Any>) = "string:$id:" + args.joinToString(",")
        override fun plural(id: Int, count: Int, args: List<Any>) = "plural:$id:$count:" + args.joinToString(",")
    }

    @Test
    fun aDynamicText_isReturnedAsItIs() {
        assertEquals("Nicolás", UiText.dynamic("Nicolás").resolve(fake))
    }

    @Test
    fun aResourceText_isResolvedWithItsArguments() {
        assertEquals("string:7:a,2", UiText.of(7, "a", 2).resolve(fake))
        assertEquals("string:7:", UiText.of(7).resolve(fake))
    }

    @Test
    fun aPluralText_isResolvedWithTheCount() {
        assertEquals("plural:9:3:3", UiText.plural(9, 3, 3).resolve(fake))
    }

    @Test
    fun anArgumentThatIsAUiText_isResolvedFirst() {
        val nested = UiText.of(1, UiText.dynamic("inner"), UiText.of(2))

        assertEquals("string:1:inner,string:2:", nested.resolve(fake))
    }

    @Test
    fun aConcatenation_joinsItsPartsInOrder() {
        val text = UiText.Concat(listOf(UiText.of(1), UiText.dynamic("\n"), UiText.dynamic("aviso")))

        assertEquals("string:1:\naviso", text.resolve(fake))
    }

    @Test
    fun twoEqualTexts_areEqual() {
        assertEquals(UiText.of(5, "x"), UiText.of(5, "x"))
        assertEquals(UiText.plural(6, 2, 2), UiText.Plural(6, 2, listOf(2)))
    }

    @Test
    fun theTestResolver_readsTheRealStringsXml() {
        assertEquals("NightWatchProtocol", UiText.of(R.string.app_name).text())
        assertEquals("1 día 🔥", UiText.plural(R.plurals.learning_streak_value, 1, 1).text())
        assertEquals("7 días 🔥", UiText.plural(R.plurals.learning_streak_value, 7, 7).text())
        assertEquals("3 de 9 insignias desbloqueadas", UiText.of(R.string.learning_badges_counter, 3, 9).text())
        assertNull(null.text())
    }

    @Test
    fun aaptRules_areFollowedByTheTestResolver() {
        assertEquals("a b", TestStrings.aaptValue("  a \n   b  "))
        assertEquals("🚪  Cerrar", TestStrings.aaptValue("\"🚪  Cerrar\""))
        assertEquals("Don't", TestStrings.aaptValue("Don\\'t"))
        assertEquals("a\nb", TestStrings.aaptValue("a\\nb"))
        assertEquals("100%", TestStrings.aaptValue("100%"))
        assertEquals("é", TestStrings.aaptValue("\\u00e9"))
        assertEquals("@x", TestStrings.aaptValue("\\@x"))
    }
}
