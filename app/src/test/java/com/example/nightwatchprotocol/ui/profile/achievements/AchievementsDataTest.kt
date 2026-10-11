package com.example.nightwatchprotocol.ui.profile.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsDataTest {
    @Test
    fun thereAreNineBadgesAndThreeAreUnlocked_asInTheDesign() {
        assertEquals(9, FixedBadges.size)
        assertEquals(3, FixedBadges.count { it.unlocked })
    }

    @Test
    fun unlockedBadgesComeFirstAndAreTheOnesFromTheDesign() {
        assertEquals(
            listOf("Primeros pasos", "Racha de 7 días", "Maestro de Contraseñas"),
            FixedBadges.filter { it.unlocked }.map { it.name },
        )
        assertTrue(FixedBadges.take(3).all { it.unlocked })
    }

    @Test
    fun lockedBadgesShowALock() {
        assertTrue(FixedBadges.filter { !it.unlocked }.all { it.emoji == "🔒" })
    }

    @Test
    fun badgeNamesAreUnique() {
        assertEquals(FixedBadges.size, FixedBadges.map { it.name }.toSet().size)
    }
}
