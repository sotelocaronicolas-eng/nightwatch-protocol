package com.example.nightwatchprotocol.data.quiz

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalQuizContentRepositoryTest {
    @Test
    fun getAttempt_returnsElevenQuestionsFromTheBundledBank() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalQuizContentRepository(context)

        val attempt = repository.getAttempt()

        assertEquals(11, attempt.size)
    }
}
