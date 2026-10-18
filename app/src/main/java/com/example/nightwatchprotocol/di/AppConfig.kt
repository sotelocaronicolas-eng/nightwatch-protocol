package com.example.nightwatchprotocol.di

/**
 * A message naming what is missing from local.properties, or null when every setting the app needs is there:
 * the Supabase URL and publishable key, and the Google web client ID (login with Google).
 */
fun appConfigError(supabaseUrl: String, supabasePublishableKey: String, googleWebClientId: String): String? {
    val missing = buildList {
        if (supabaseUrl.isBlank()) add("SUPABASE_URL")
        if (supabasePublishableKey.isBlank()) add("SUPABASE_PUBLISHABLE_KEY")
        if (googleWebClientId.isBlank()) add("GOOGLE_WEB_CLIENT_ID")
    }
    if (missing.isEmpty()) return null
    return "Falta ${missing.joinToString(", ")} en local.properties. " +
        "Copia local.properties.example a local.properties y completa los valores."
}
