package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.SocialError
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/** The actions of `0011` answer `bad_action` on a server that does not have the script yet: that is "unavailable", not a bug. */
class NewActionErrorsTest {

    private fun raised(code: String) = PostgrestRestException(code, null, null, POSTGRES_RAISE_EXCEPTION, fakeHttpResponse(400))

    @Test
    fun badAction_ofANewAction_isUnavailable() {
        assertEquals(SocialError.Unavailable, classifyNewActionFailure(raised("bad_action")))
    }

    @Test
    fun badAction_ofAnOldAction_isStillUnknown() {
        assertEquals(SocialError.Unknown, classifySocialFailure(raised("bad_action")))
    }

    @Test
    fun theOtherFailures_ofANewAction_areClassifiedAsUsual() {
        assertEquals(SocialError.RateLimited, classifyNewActionFailure(raised("rate_limited")))
        assertEquals(SocialError.InvalidInput, classifyNewActionFailure(raised("bad_query")))
        assertEquals(SocialError.NotEnabled, classifyNewActionFailure(raised("social_not_enabled")))
        assertEquals(SocialError.Unknown, classifyNewActionFailure(raised("algo_nuevo")))
        assertEquals(SocialError.Offline, classifyNewActionFailure(IOException("sin red")))
    }

    @Test
    fun aBadActionThatIsNotARaisedException_isNotUnavailable() {
        val notRaised = PostgrestRestException("bad_action", null, null, "42883", fakeHttpResponse(404))

        assertEquals(SocialError.Unknown, classifyNewActionFailure(notRaised))
    }
}
