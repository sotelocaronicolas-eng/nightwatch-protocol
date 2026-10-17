package com.example.nightwatchprotocol.di

/** Which remote backend the app talks to. Chosen at build time from `BACKEND` in local.properties. */
enum class Backend { FIREBASE, SUPABASE }

/** Case-insensitive; an empty or unknown value falls back to [Backend.FIREBASE]. */
fun parseBackend(value: String?): Backend =
    Backend.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: Backend.FIREBASE

/** A message naming what is missing from local.properties, or null when the Supabase settings are complete. */
fun supabaseConfigError(url: String, publishableKey: String): String? {
    val missing = buildList {
        if (url.isBlank()) add("SUPABASE_URL")
        if (publishableKey.isBlank()) add("SUPABASE_PUBLISHABLE_KEY")
    }
    if (missing.isEmpty()) return null
    return "BACKEND=supabase pero falta ${missing.joinToString(" y ")} en local.properties. " +
        "Copia local.properties.example a local.properties y completa los valores."
}
