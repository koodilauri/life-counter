package com.example.life_counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RulesGlossaryTest {

    // A trimmed slice of the CR shaped exactly like the real file.
    private val sampleCr = """
        8.1 Type Keywords
        8.1.1 Action
        An action card is a deck-card.
        8.1.1a An action card can only be played when the stack is empty.
        8.3 Ability Keywords
        8.3.4 Dominate
        Dominate is a keyword.
        8.3.4a The defending player can't defend with more than 1 card.
        8.3.5 Go again
        Go again is a keyword.
        8.3.5a A source with go again generates an action point.
        8.5 Effect Keywords
        8.5.1 Freeze
        Freeze is a keyword effect.
        9 Additional Rules
        9.0.1 This should not be parsed as a keyword.
    """.trimIndent()

    private val glossary = RulesGlossary.parse(sampleCr)

    @Test
    fun `parses a keyword with its reference and definition body`() {
        val goAgain = glossary.lookup("Go again")!!
        assertEquals("8.3.5", goAgain.reference)
        assertEquals("8.3", goAgain.section)
        assertTrue(goAgain.definition.startsWith("Go again is a keyword."))
        // Sub-rules are folded into the definition; the next header is not.
        assertTrue("generates an action point" in goAgain.definition)
        assertTrue("Freeze" !in goAgain.definition)
    }

    @Test
    fun `lookup is case-insensitive and trims`() {
        assertEquals("8.3.4", glossary.lookup("  dominate  ")?.reference)
    }

    @Test
    fun `only ability_label_effect_token sections are linkable`() {
        // 8.3/8.5 in; 8.1 type keyword out; chapter 9 not captured at all.
        assertTrue("Dominate" in glossary.linkableKeywords)
        assertTrue("Go again" in glossary.linkableKeywords)
        assertTrue("Freeze" in glossary.linkableKeywords)
        assertTrue("Action" !in glossary.linkableKeywords)
    }

    @Test
    fun `content after chapter 8 is not parsed as a keyword`() {
        assertNull(glossary.lookup("This should not be parsed as a keyword."))
    }

    @Test
    fun `finds whole-word keywords and prefers the longer match`() {
        val keywords = listOf("Go again", "Dominate").sortedByDescending { it.length }
        val spans = findKeywordSpans("This gains go again, then dominate.", keywords)

        assertEquals(2, spans.size)
        assertEquals("go again", "This gains go again, then dominate.".substring(spans[0].start, spans[0].end))
        assertEquals("Go again", spans[0].keyword)
        assertEquals("dominate", "This gains go again, then dominate.".substring(spans[1].start, spans[1].end))
    }

    @Test
    fun `does not match a keyword inside a larger word`() {
        // "again" must not be matched inside "against".
        val spans = findKeywordSpans("fights against the odds", listOf("again"))
        assertTrue(spans.isEmpty())
    }

    // Smoke test against the real bundled CR (unit tests run with the module as
    // working dir). Skipped if the file isn't reachable from here.
    @Test
    fun `real bundled CR parses the expected keywords`() {
        val file = File("src/main/assets/fab-cr.txt")
        assumeTrue("bundled CR not reachable from test cwd", file.exists())

        val real = RulesGlossary.parse(file.readText())

        assertEquals("8.3.5", real.lookup("Go again")?.reference)
        assertEquals("8.3.22", real.lookup("Overpower")?.reference)
        assertTrue("Arcane Barrier" in real.linkableKeywords)
        // A type keyword must not be linkable, even though it's defined in ch 8.
        assertTrue("Action" !in real.linkableKeywords)
    }
}
