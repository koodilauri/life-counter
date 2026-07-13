package com.example.life_counter

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLegalityTest {

    private fun variant(pitch: String, legal: List<String>, banned: List<String>) =
        CardVariant(
            color = CardColor.RED,
            pitch = pitch,
            cost = "0",
            functionalText = "",
            imageUrl = null,
            legality = Legality(legalFormats = legal, bannedFormats = banned),
        )

    @Test
    fun `a format banned in only some pitches is annotated with those pitches`() {
        // Golden Tipple: red (1) and yellow (2) are CC-banned; blue (3) is legal.
        val card = Card(
            name = "Golden Tipple",
            typeText = "",
            variants = listOf(
                variant("1", legal = listOf("Blitz", "Commoner"), banned = listOf("CC")),
                variant("2", legal = listOf("Blitz", "Commoner"), banned = listOf("CC")),
                variant("3", legal = listOf("Blitz", "Commoner", "CC"), banned = emptyList()),
            ),
        )

        val summary = card.legalitySummary()

        assertEquals(listOf("Blitz", "Commoner"), summary.legalFormats)
        assertEquals(listOf("CC (pitch 1, 2)"), summary.bannedFormats)
    }

    @Test
    fun `a format banned at every pitch is listed without annotation`() {
        val card = Card(
            name = "Fully Banned",
            typeText = "",
            variants = listOf(
                variant("1", legal = listOf("Blitz"), banned = listOf("CC")),
                variant("2", legal = listOf("Blitz"), banned = listOf("CC")),
            ),
        )

        val summary = card.legalitySummary()

        assertEquals(listOf("Blitz"), summary.legalFormats)
        assertEquals(listOf("CC"), summary.bannedFormats)
    }

    @Test
    fun `a single-pitch card reports plain legality`() {
        val card = Card(
            name = "Command and Conquer",
            typeText = "",
            variants = listOf(
                variant("", legal = listOf("Blitz", "Commoner"), banned = listOf("CC")),
            ),
        )

        val summary = card.legalitySummary()

        assertEquals(listOf("Blitz", "Commoner"), summary.legalFormats)
        // Only one (blank) pitch, so no pitch annotation.
        assertEquals(listOf("CC"), summary.bannedFormats)
    }
}
