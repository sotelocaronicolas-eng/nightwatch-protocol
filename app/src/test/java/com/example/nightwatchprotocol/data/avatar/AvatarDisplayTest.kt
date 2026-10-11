package com.example.nightwatchprotocol.data.avatar

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarDisplayTest {
    private val photo = byteArrayOf(1, 2, 3)

    @Test
    fun withPhoto_showsThePhoto() {
        assertEquals(AvatarDisplay.Photo(photo), avatarDisplay(photo, "Nicolás", "n@example.com"))
    }

    @Test
    fun withoutPhoto_showsTheUppercaseInitialOfTheName() {
        assertEquals(AvatarDisplay.Initial("N"), avatarDisplay(null, "nicolás sotelo", "x@example.com"))
    }

    @Test
    fun emptyPhoto_isTreatedAsNoPhoto() {
        assertEquals(AvatarDisplay.Initial("N"), avatarDisplay(ByteArray(0), "Nicolás", null))
    }

    @Test
    fun blankName_fallsBackToTheEmailInitial() {
        assertEquals(AvatarDisplay.Initial("E"), avatarDisplay(null, "   ", "euzuki@gmail.com"))
    }

    @Test
    fun nullName_fallsBackToTheEmailInitial() {
        assertEquals(AvatarDisplay.Initial("E"), avatarDisplay(null, null, "euzuki@gmail.com"))
    }

    @Test
    fun noNameAndNoEmail_showsQuestionMark() {
        assertEquals(AvatarDisplay.Initial("?"), avatarDisplay(null, null, null))
        assertEquals(AvatarDisplay.Initial("?"), avatarDisplay(null, "  ", ""))
    }

    @Test
    fun nameWithLeadingSpaces_usesTheFirstRealCharacter() {
        assertEquals(AvatarDisplay.Initial("Á"), avatarDisplay(null, "  álvaro", null))
    }
}
