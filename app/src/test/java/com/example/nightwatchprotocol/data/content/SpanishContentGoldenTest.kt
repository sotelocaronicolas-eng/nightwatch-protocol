package com.example.nightwatchprotocol.data.content

import com.example.nightwatchprotocol.data.language.AppLanguage
import com.example.nightwatchprotocol.data.learning.LearningAssets
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Spanish content must stay exactly as it was before the English content existed (Stage 9). The hashes are the SHA-256 of the four
 * files as they were in `assets/` on the commit before the move to `assets/content/es/` (line endings normalized to LF).
 *
 * If the Spanish content is edited on purpose, update the hash here in the same commit and say why in the commit message.
 */
class SpanishContentGoldenTest {
    private val golden = mapOf(
        "learning_lessons.json" to "e3e1ceeddcb0af12b38a4603d94388fd96d0fd304ba553e92da2983faabc46aa",
        "learning_labs.json" to "111a637915e70c9bd564744b7907fdd8fd4abeb4d4d5d4046e5950acde05c832",
        "learning_exam.json" to "94fb88d686b2f0e6f58c0e3fcb7c3e580f01b98be67ecb50ade68b764fae6c65",
        "quiz_questions.json" to "410b548039caaa1a7472d668659666df759d08994044fba1e3a99aa1e7a1b2cc",
    )

    @Test
    fun spanishFiles_areByteForByteTheOriginals() {
        golden.forEach { (file, expected) ->
            val text = LearningAssets.read(contentAssetPath(AppLanguage.ES, file)).replace("\r\n", "\n")
            val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(file, expected, hash)
        }
    }
}
