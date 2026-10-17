@file:OptIn(InternalAPI::class)

package com.example.nightwatchprotocol.data.supabase

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.runBlocking

/**
 * A real Ktor [HttpResponse] with the given status and no network: supabase-kt's exceptions need one to be built.
 * The request carries a fake Authorization header so the tests can prove it never leaks into our exceptions.
 */
fun fakeHttpResponse(status: Int): HttpResponse {
    val engine = object : HttpClientEngineBase("fake") {
        override val config = HttpClientEngineConfig()

        override suspend fun execute(data: HttpRequestData): HttpResponseData = HttpResponseData(
            statusCode = HttpStatusCode.fromValue(status),
            requestTime = GMTDate(),
            headers = Headers.Empty,
            version = HttpProtocolVersion.HTTP_1_1,
            body = ByteReadChannel(""),
            callContext = callContext(),
        )
    }
    return runBlocking {
        HttpClient(engine) { expectSuccess = false }.get("https://example.invalid/test") {
            header("Authorization", FAKE_BEARER_TOKEN)
        }
    }
}

const val FAKE_BEARER_TOKEN = "Bearer secret-token-do-not-leak"
