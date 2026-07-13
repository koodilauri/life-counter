package com.example.life_counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CrDocumentTest {

    private val sample = """
        1 Game Concepts
        1.0 General
        1.0.1 The rules in this document apply to any game.
        1.0.2a A restriction takes precedence over a requirement.
        Example: This is an example line about restrictions.
        8 Keywords
        8.3 Ability Keywords
        8.3.5 Go again
        Go again generates an action point.
        3 cards are drawn in some situations.
    """.trimIndent()

    private val doc = CrDocument.parse(sample)

    @Test
    fun `assigns levels from the reference shape`() {
        fun entry(ref: String) = doc.entries.first { it.reference == ref }
        assertEquals(CrLevel.CHAPTER, entry("1").level)
        assertEquals(CrLevel.SECTION, entry("1.0").level)
        assertEquals(CrLevel.RULE, entry("1.0.1").level)
        assertEquals(CrLevel.RULE, entry("1.0.2a").level)
    }

    @Test
    fun `prose and examples have no reference`() {
        val example = doc.entries.first { it.isExample }
        assertEquals(null, example.reference)
        assertEquals(CrLevel.TEXT, example.level)
    }

    @Test
    fun `a line that merely starts with a number is prose, not a chapter`() {
        val entry = doc.entries.first { it.text.contains("cards are drawn") }
        assertEquals(null, entry.reference)
        // The whole original line is kept as text.
        assertTrue(entry.text.startsWith("3 cards are drawn"))
    }

    @Test
    fun `a reference query jumps to that rule`() {
        val result = doc.search("1.0.2a")
        assertTrue(result is CrSearchResult.Jump)
        assertEquals("1.0.2a", (result as CrSearchResult.Jump).reference)
        assertEquals(doc.indexOfReference("1.0.2a"), result.index)
    }

    @Test
    fun `a section reference jumps too`() {
        val result = doc.search("8.3")
        assertTrue(result is CrSearchResult.Jump)
        assertEquals("8.3", (result as CrSearchResult.Jump).reference)
    }

    @Test
    fun `an exact keyword jumps to its definition via the glossary`() {
        val glossary = RulesGlossary.parse(
            """
            8.3 Ability Keywords
            8.3.5 Go again
            Go again generates an action point.
            """.trimIndent(),
        )
        val result = doc.search("go again", glossary)
        assertTrue(result is CrSearchResult.Jump)
        assertEquals("8.3.5", (result as CrSearchResult.Jump).reference)
    }

    @Test
    fun `free text returns matches in document order`() {
        val result = doc.search("precedence")
        assertTrue(result is CrSearchResult.Matches)
        val hits = (result as CrSearchResult.Matches).hits
        assertEquals(1, hits.size)
        assertEquals("1.0.2a", hits.first().entry.reference)
    }

    @Test
    fun `ruleBlock gathers a rule with its sub-rules and prose, stopping at a sibling`() {
        val cr = """
            1.3.2 A base rule.
            1.3.2a A sub-rule.
            Example: prose under the sub-rule.
            1.3.20 A numeric sibling, not a child.
            1.3.3 The next rule.
        """.trimIndent()
        val d = CrDocument.parse(cr)

        val block = d.ruleBlock("1.3.2").map { it.reference ?: it.text }

        assertEquals(listOf("1.3.2", "1.3.2a", "Example: prose under the sub-rule."), block)
    }

    @Test
    fun `ruleBlock is empty for an unknown reference`() {
        assertTrue(doc.ruleBlock("9.9.9").isEmpty())
    }

    @Test
    fun `empty query browses and gibberish finds nothing`() {
        assertTrue(doc.search("   ") is CrSearchResult.Browse)
        assertTrue(doc.search("zzzznotfound") is CrSearchResult.NoMatch)
    }

    // Smoke test against the real bundled CR; skipped if unreachable from cwd.
    @Test
    fun `real bundled CR parses and jumps to a known rule`() {
        val file = File("src/main/assets/fab-cr.txt")
        assumeTrue("bundled CR not reachable from test cwd", file.exists())

        val real = CrDocument.parse(file.readText())

        val goAgain = real.entries.first { it.reference == "8.3.5" }
        assertEquals(CrLevel.RULE, goAgain.level)
        assertTrue(goAgain.text.contains("Go again"))

        val jump = real.search("8.3.5")
        assertTrue(jump is CrSearchResult.Jump)
        assertEquals(real.indexOfReference("8.3.5"), (jump as CrSearchResult.Jump).index)

        assertTrue(real.search("go again") is CrSearchResult.Matches)
    }
}
