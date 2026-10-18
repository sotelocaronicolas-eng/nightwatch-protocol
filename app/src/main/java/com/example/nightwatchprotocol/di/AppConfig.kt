package com.example.nightwatchprotocol.di

/** A message naming what is missing from local.properties, or null when the Supabase settings are complete. */
fun supabaseConfigError(url: String, publishableKey: String): String? {
    val missing = buildList {
        if (url.isBlank()) add("SUPABASE_URL")
        if (publishableKey.isBlank()) add("SUPABASE_PUBLISHABLE_KEY")
    }
    if (missing.isEmpty()) return null
    return "Falta ${missing.joinToString(" y ")} en local.properties. " +
        "Copia local.properties.example a local.properties y completa los valores."
}
