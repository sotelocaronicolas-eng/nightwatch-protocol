package com.example.nightwatchprotocol

import com.example.nightwatchprotocol.data.common.TextResolver
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.common.resolve
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Resolves resources in a plain JVM test by reading the real `res/values/strings*.xml` files, so a test can compare the TEXT
 * a screen would show (the Spanish originals) without Robolectric. Ids are mapped to names with the generated `R` class.
 * The unescaping follows aapt: `\'`, `\"`, `\n`, `\@`, `\?`, `\\`, `\uXXXX`, quoted values keep their spaces and any other
 * value has its whitespace collapsed and trimmed.
 */
object TestStrings : TextResolver {

    private val valuesFolder: File = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }

    /** name -> text, of every `<string>` in the strings files of `res/values`. */
    val strings: Map<String, String> by lazy {
        val result = LinkedHashMap<String, String>()
        xmlFiles().forEach { file ->
            elements(file, "string").forEach { result[it.getAttribute("name")] = aaptValue(it.textContent) }
        }
        result
    }

    /** name -> quantity -> text, of every `<plurals>`. */
    val plurals: Map<String, Map<String, String>> by lazy {
        val result = LinkedHashMap<String, Map<String, String>>()
        xmlFiles().forEach { file ->
            elements(file, "plurals").forEach { plural ->
                val items = plural.getElementsByTagName("item")
                result[plural.getAttribute("name")] = (0 until items.length).associate {
                    val item = items.item(it) as Element
                    item.getAttribute("quantity") to aaptValue(item.textContent)
                }
            }
        }
        result
    }

    private val stringNames: Map<Int, String> by lazy { idsOf("com.example.nightwatchprotocol.R\$string") }
    private val pluralNames: Map<Int, String> by lazy { idsOf("com.example.nightwatchprotocol.R\$plurals") }

    override fun string(id: Int, args: List<Any>): String {
        val name = stringNames[id] ?: error("no hay un recurso string con el id $id")
        val template = strings[name] ?: error("el recurso $name no está en ningún strings*.xml")
        return String.format(Locale.ROOT, template, *args.toTypedArray())
    }

    override fun plural(id: Int, count: Int, args: List<Any>): String {
        val name = pluralNames[id] ?: error("no hay un recurso plurals con el id $id")
        val items = plurals[name] ?: error("el plural $name no está en ningún strings*.xml")
        val template = items[if (count == 1) "one" else "other"] ?: items.getValue("other")
        return String.format(Locale.ROOT, template, *args.toTypedArray())
    }

    private fun idsOf(className: String): Map<Int, String> =
        Class.forName(className).declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }

    private fun xmlFiles(): List<File> = valuesFolder.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!.sorted()

    private fun elements(file: File, tag: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** The text aapt would produce from the raw XML text of a resource. */
    fun aaptValue(raw: String): String {
        val quoted = raw.trim().length >= 2 && raw.trim().startsWith("\"") && raw.trim().endsWith("\"")
        val body = if (quoted) raw.trim().drop(1).dropLast(1) else raw.trim().replace(Regex("\\s+"), " ")
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                val next = body[i + 1]
                when (next) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> {
                        out.append(body.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

/** The Spanish text of a [UiText] in a test (null stays null). */
fun UiText?.text(): String? = this?.resolve(TestStrings)
