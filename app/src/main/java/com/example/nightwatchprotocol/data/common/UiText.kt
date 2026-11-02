package com.example.nightwatchprotocol.data.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A text that is **resolved when it is shown**, not when it is built (Module 5, decision D10). View models, data classes and
 * notifications carry a [UiText]; the screen (or a [Context]) turns it into a `String` in the language of that moment.
 * It knows no Compose and no `Context`: only the ids of the resources, so any layer can build one and a unit test can compare
 * two of them with `assertEquals`.
 */
sealed interface UiText {

    /** A text that is already final and has no translation: a name typed by the person, a number, an email, a server text. */
    data class Dynamic(val value: String) : UiText

    /** A string resource. [args] fill `%1$s`, `%2$d`... in order; an argument that is a [UiText] is resolved first. */
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A `<plurals>` resource for [count]; [args] fill the placeholders (usually the count itself comes first). */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    /** Several texts one after the other (for example a message, a line break and a notice). */
    data class Concat(val parts: List<UiText>) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())

        fun dynamic(value: String): UiText = Dynamic(value)
    }
}

/** Reads strings from wherever the resources are: a [Context] in the app, the real XML files in a JVM test. */
interface TextResolver {
    fun string(@StringRes id: Int, args: List<Any>): String

    fun plural(@PluralsRes id: Int, count: Int, args: List<Any>): String
}

/** The text of this [UiText] with [resolver]. */
fun UiText.resolve(resolver: TextResolver): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> resolver.string(id, args.map { it.resolvedWith(resolver) })
    is UiText.Plural -> resolver.plural(id, count, args.map { it.resolvedWith(resolver) })
    is UiText.Concat -> parts.joinToString(separator = "") { it.resolve(resolver) }
}

private fun Any.resolvedWith(resolver: TextResolver): Any = if (this is UiText) resolve(resolver) else this

/** A [TextResolver] over Android resources: used outside Compose (notifications, workers) with the context that has the right language. */
class ContextTextResolver(private val context: Context) : TextResolver {
    override fun string(id: Int, args: List<Any>): String = context.getString(id, *args.toTypedArray())

    override fun plural(id: Int, count: Int, args: List<Any>): String =
        context.resources.getQuantityString(id, count, *args.toTypedArray())
}

/** The text of this [UiText] with the resources of [context]. */
fun UiText.asString(context: Context): String = resolve(ContextTextResolver(context))
