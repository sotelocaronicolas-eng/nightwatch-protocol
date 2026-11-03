package com.example.nightwatchprotocol

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Module 5, Stage 8: the English (`res/values-en`) must have exactly what the Spanish (`res/values`) has, file by file, so the
 * translation cannot fall apart in silence. For every translated file it checks: the same keys (none missing, none extra), the same
 * placeholders (`%1$s`, `%2$d`... by position and type), the same plural categories that English needs, the same `translatable`
 * attribute, and that no English text is empty or equal to the Spanish unless it is on the documented exceptions list.
 */
class TranslationParityTest {

    /**
     * Part 2 of Stage 8: Spanish files that do not have their `values-en` twin yet. This list MUST be empty when the Stage ends.
     * A file that is here but already has a translation (or a file with no translation that is not here) fails the test, so the
     * list cannot go stale.
     */
    private val PENDING_PARTE_2: Set<String> = setOf(
        "strings_social.xml",
        "strings_social_pantallas.xml",
    )

    /**
     * Keys whose English text is allowed to equal the Spanish one (checked per key, in any file). Each one says why.
     *  - `app_name`, `common_app_title`: proper names of the app (they are never translated).
     *  - `common_error_icon_description`: "Error" is the same word in both languages.
     *  - `settings_menu_footer` ("Nightwatch Protocol · v%1$s") and `profile_member_since_value` ("%1$s %2$d"): only a proper name,
     *    a symbol and placeholders; nothing to translate.
     *  - `common_month_short_N` for February, March, June, July, September, October and November: the abbreviation is the same in both.
     *  - `learning_tab_social`, `learning_tab_labs`, `learning_lab_terminal`, `learning_home_app_name`: "Social", "Labs", "Terminal" and
     *    the app name are the same word in both languages. `learning_lesson_close` is the letter "X".
     *  - A text with no letters at all once the placeholders are removed (emojis, "…", "—", ">", "$ %1$s", "%1$d%%") is also the same in
     *    both languages by nature: see [hasNoWords].
     */
    private val sameInBothLanguages: Set<String> = setOf(
        "app_name",
        "common_app_title",
        "common_error_icon_description",
        "settings_menu_footer",
        "profile_member_since_value",
        "learning_tab_social",
        "learning_tab_labs",
        "learning_lab_terminal",
        "learning_home_app_name",
        "learning_lesson_close",
    ) + listOf(2, 3, 6, 7, 9, 10, 11).map { "common_month_short_$it" }

    /** True when the text has no letter once the placeholders are taken out: symbols and emojis only. */
    private fun hasNoWords(text: String): Boolean =
        text.replace(Regex("""%(?:\d+\$)?[sdfxXc]"""), "").replace("%%", "").none { it.isLetter() }

    private fun mayBeSame(key: String, text: String): Boolean = key in sameInBothLanguages || hasNoWords(text)

    private fun folder(name: String): File = listOf(File("src/main/res/$name"), File("app/src/main/res/$name")).firstOrNull { it.isDirectory }
        ?: File("src/main/res/$name")

    private val spanish = folder("values")
    private val english = folder("values-en")

    private fun stringsFiles(dir: File): List<String> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml") }?.map { it.name }?.sorted().orEmpty()

    private class Entries(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
        /** Names marked `translatable="false"`: they are left out of the three maps above and must exist only in `res/values`. */
        val nonTranslatable: Set<String>,
    )

    private fun parse(file: File): Entries {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val arrays = LinkedHashMap<String, List<String>>()
        val nonTranslatable = LinkedHashSet<String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            if (node.getAttribute("translatable") == "false") {
                nonTranslatable += name
                continue
            }
            when (node.tagName) {
                "string" -> strings[name] = node.textContent
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
                "string-array" -> {
                    val items = node.getElementsByTagName("item")
                    arrays[name] = (0 until items.length).map { items.item(it).textContent }
                }
            }
        }
        return Entries(strings, plurals, arrays, nonTranslatable)
    }

    /** index -> conversion for every placeholder: explicit `%2$d` keeps its number, a plain `%s` takes the next one. */
    private fun placeholders(text: String): Map<Int, Char> {
        var next = 1
        val result = LinkedHashMap<Int, Char>()
        Regex("""%(?:(\d+)\$)?([sdfxXc])""").findAll(text.replace("%%", "")).forEach { match ->
            val index = match.groupValues[1].ifEmpty { null }?.toInt() ?: next++
            result[index] = match.groupValues[2][0]
        }
        return result
    }

    private val translatedFiles: List<String> by lazy { stringsFiles(spanish).filter { it !in PENDING_PARTE_2 } }

    @Test
    fun everySpanishFile_isTranslated_orListedAsPending() {
        val spanishFiles = stringsFiles(spanish)
        val englishFiles = stringsFiles(english).toSet()
        assertTrue("no se encontró res/values", spanishFiles.isNotEmpty())

        spanishFiles.forEach { name ->
            if (name in PENDING_PARTE_2) {
                assertFalse("$name ya tiene values-en: quítalo de PENDING_PARTE_2", name in englishFiles)
            } else {
                assertTrue("$name no tiene values-en y no está en PENDING_PARTE_2", name in englishFiles)
            }
        }
        assertEquals("values-en tiene archivos sin gemelo en values", emptySet<String>(), englishFiles - spanishFiles.toSet())
        assertTrue("PENDING_PARTE_2 nombra un archivo que no existe", PENDING_PARTE_2.all { it in spanishFiles })
    }

    @Test
    fun theTranslatedFiles_haveExactlyTheSameKeys() {
        translatedFiles.forEach { name ->
            val es = parse(File(spanish, name))
            val en = parse(File(english, name))
            assertEquals("$name: cadenas que faltan o sobran", es.strings.keys, en.strings.keys)
            assertEquals("$name: plurales que faltan o sobran", es.plurals.keys, en.plurals.keys)
            assertEquals("$name: arreglos que faltan o sobran", es.arrays.keys, en.arrays.keys)
            es.arrays.forEach { (key, items) -> assertEquals("$name: $key tiene otra cantidad de elementos", items.size, en.arrays.getValue(key).size) }
        }
    }

    @Test
    fun theTranslatedFiles_keepThePlaceholders() {
        translatedFiles.forEach { name ->
            val es = parse(File(spanish, name))
            val en = parse(File(english, name))
            es.strings.forEach { (key, text) ->
                assertEquals("$name: $key cambió los placeholders", placeholders(text), placeholders(en.strings.getValue(key)))
            }
            es.plurals.forEach { (key, items) ->
                val enItems = en.plurals.getValue(key)
                assertTrue("$name: $key necesita 'other' en inglés", "other" in enItems)
                if ("one" in items) assertTrue("$name: $key necesita 'one' en inglés", "one" in enItems)
                // English only uses one and other: a quantity it has must keep the placeholders of the Spanish text of the same kind.
                enItems.forEach { (quantity, text) ->
                    val reference = items[quantity] ?: items.getValue("other")
                    assertEquals("$name: $key/$quantity cambió los placeholders", placeholders(reference), placeholders(text))
                }
            }
            es.arrays.forEach { (key, items) ->
                items.forEachIndexed { i, text ->
                    assertEquals("$name: $key[$i] cambió los placeholders", placeholders(text), placeholders(en.arrays.getValue(key)[i]))
                }
            }
        }
    }

    @Test
    fun theTranslatedFiles_keepTheTranslatableAttribute() {
        translatedFiles.forEach { name ->
            val es = parse(File(spanish, name))
            val en = parse(File(english, name))
            // What the Spanish marks translatable="false" (the language names) is not repeated in English, and English marks nothing itself.
            assertEquals("$name: values-en marca translatable=false", emptySet<String>(), en.nonTranslatable)
            val repeated = es.nonTranslatable.filter { it in en.strings.keys || it in en.plurals.keys || it in en.arrays.keys }
            assertEquals("$name: values-en repite cadenas translatable=false", emptyList<String>(), repeated)
        }
    }

    @Test
    fun noEnglishText_isEmpty_orEqualToTheSpanish_exceptTheDocumentedOnes() {
        translatedFiles.forEach { name ->
            val es = parse(File(spanish, name))
            val en = parse(File(english, name))
            en.strings.forEach { (key, text) ->
                assertTrue("$name: $key está vacío en inglés", text.isNotBlank())
                if (!mayBeSame(key, text)) assertFalse("$name: $key es idéntico al español", text == es.strings.getValue(key))
            }
            en.plurals.forEach { (key, items) ->
                items.forEach { (quantity, text) ->
                    assertTrue("$name: $key/$quantity está vacío en inglés", text.isNotBlank())
                    if (!mayBeSame(key, text)) assertFalse("$name: $key/$quantity es idéntico al español", text == es.plurals.getValue(key)[quantity])
                }
            }
            en.arrays.forEach { (key, items) -> items.forEach { assertTrue("$name: $key tiene un elemento vacío", it.isNotBlank()) } }
        }
    }

    @Test
    fun theExceptionsList_namesRealKeys() {
        val allKeys = stringsFiles(spanish).flatMap { parse(File(spanish, it)).strings.keys }.toSet()
        assertTrue("excepción que no existe: ${sameInBothLanguages - allKeys}", allKeys.containsAll(sameInBothLanguages))
    }
}
