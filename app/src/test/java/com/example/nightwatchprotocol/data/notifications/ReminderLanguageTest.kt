package com.example.nightwatchprotocol.data.notifications

import com.example.nightwatchprotocol.data.common.TextResolver
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.common.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The texts of a reminder are looked up when the notification is posted, with the language of that moment (D11): changing the language
 * changes the very next notification. The resolver is a fake keyed by language, so no Android is needed.
 */
class ReminderLanguageTest {
    private class LangResolver(var lang: String) : TextResolver {
        override fun string(id: Int, args: List<Any>) = "$lang:$id"
        override fun plural(id: Int, count: Int, args: List<Any>) = "$lang:$id:$count"
    }

    @Test
    fun theNextNotification_followsTheLanguageChange() {
        val resolver = LangResolver("es")
        ReminderKind.entries.forEach { kind ->
            val before = reminderTexts(kind, resolver)
            assertEquals(UiText.resolve(kind.title, resolver), before.title)

            resolver.lang = "en"
            val after = reminderTexts(kind, resolver)

            assertNotEquals(before.title, after.title)
            assertNotEquals(before.text, after.text)
            assertNotEquals(before.channelName, after.channelName)
            assertEquals(true, after.title.startsWith("en:") && after.text.startsWith("en:") && after.channelName.startsWith("en:"))
            resolver.lang = "es"
        }
    }

    private fun UiText.Companion.resolve(text: UiText, resolver: TextResolver) = text.resolve(resolver)
}
