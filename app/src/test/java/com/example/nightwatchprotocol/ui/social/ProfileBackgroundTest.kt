package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileBackgroundTest {

    @Test
    fun zero_isNoBackground() {
        assertNull(profileBackgroundRes(0))
    }

    @Test
    fun oneToFive_areTheFivePictures_inOrder() {
        assertEquals(R.drawable.profile_bg_1, profileBackgroundRes(1))
        assertEquals(R.drawable.profile_bg_2, profileBackgroundRes(2))
        assertEquals(R.drawable.profile_bg_3, profileBackgroundRes(3))
        assertEquals(R.drawable.profile_bg_4, profileBackgroundRes(4))
        assertEquals(R.drawable.profile_bg_5, profileBackgroundRes(5))
        assertEquals(5, (1..5).map { profileBackgroundRes(it) }.map { assertNotNull(it); it }.toSet().size)
    }

    @Test
    fun anUnknownNumber_fallsBackToNoBackground() {
        assertNull(profileBackgroundRes(6))
        assertNull(profileBackgroundRes(-1))
        assertNull(profileBackgroundRes(Int.MAX_VALUE))
        assertNull(profileBackgroundRes(Int.MIN_VALUE))
    }
}
