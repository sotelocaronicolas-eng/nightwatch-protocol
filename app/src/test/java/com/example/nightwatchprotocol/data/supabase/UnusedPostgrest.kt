package com.example.nightwatchprotocol.data.supabase

import io.github.jan.supabase.postgrest.Postgrest
import java.lang.reflect.Proxy

/** A [Postgrest] that fails the test if it is used: for proving that a call stops before reaching the network. */
fun unusedPostgrest(): Postgrest =
    Proxy.newProxyInstance(Postgrest::class.java.classLoader, arrayOf(Postgrest::class.java)) { _, method, _ ->
        throw AssertionError("Postgrest no debía usarse (${method.name})")
    } as Postgrest
