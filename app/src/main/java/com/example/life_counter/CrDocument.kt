package com.example.life_counter

/** How a line sits in the CR hierarchy, from its reference shape. */
enum class CrLevel { CHAPTER, SECTION, RULE, TEXT }

/**
 * One line of the Comprehensive Rules. A numbered line carries its reference
 * ("1.0.2a") and level; prose, examples, and glossary lines have no reference
 * and are TEXT.
 */
data class CrEntry(
    val reference: String?,
    val text: String,
    val level: CrLevel,
) {
    val isExample: Boolean get() = text.startsWith("Example:")
}

/** The outcome of a smart search over the document. */
sealed interface CrSearchResult {
    /** Empty query — show the whole document for reading. */
    data object Browse : CrSearchResult
    /** The query resolved to a specific rule; scroll the document to [index]. */
    data class Jump(val index: Int, val reference: String) : CrSearchResult
    /** Free-text hits, in document order. */
    data class Matches(val hits: List<CrHit>) : CrSearchResult
    /** A non-empty query that matched nothing. */
    data object NoMatch : CrSearchResult
}

data class CrHit(val index: Int, val entry: CrEntry)

/**
 * The full Comprehensive Rules, parsed once from the bundled/cached text into an
 * ordered list of entries that a reader can scroll and a search can query.
 */
class CrDocument(val entries: List<CrEntry>) {

    // First index at which each reference appears (bare chapter numbers can repeat
    // in the source; rule references are unique).
    private val refToIndex: Map<String, Int> = buildMap {
        // `entries` here would resolve to Map.entries — qualify to the class's list.
        this@CrDocument.entries.forEachIndexed { i, entry ->
            entry.reference?.let { ref -> putIfAbsent(ref, i) }
        }
    }

    /** Chapter/section headings with their indices — the table of contents. */
    val contents: List<CrHit> = entries.mapIndexedNotNull { index, entry ->
        if (entry.level == CrLevel.CHAPTER || entry.level == CrLevel.SECTION) {
            CrHit(index, entry)
        } else {
            null
        }
    }

    fun indexOfReference(reference: String): Int? = refToIndex[reference]

    /**
     * Whether [reference] resolves to a rule in this document. False for the
     * handful of references LSS's published CR cites but never defines (e.g. 5.5,
     * 4.6) — those are rendered as plain text rather than dead links.
     */
    fun hasReference(reference: String): Boolean = refToIndex.containsKey(reference)

    /** The reference of [index]'s entry, or the nearest numbered one above it. */
    fun nearestReference(index: Int): String? {
        for (i in index downTo 0) entries[i].reference?.let { return it }
        return null
    }

    /**
     * The referenced entry plus everything nested under it — lettered sub-rules
     * and the prose/examples that belong to them — for showing a rule in a peek
     * without leaving the reading position. Empty if the reference is unknown.
     */
    fun ruleBlock(reference: String): List<CrEntry> {
        val start = indexOfReference(reference) ?: return emptyList()
        val block = mutableListOf(entries[start])
        var i = start + 1
        while (i < entries.size) {
            val entry = entries[i]
            val ref = entry.reference
            when {
                // Prose/examples attach to the current rule.
                ref == null -> block += entry
                // A descendant: the reference then a letter or deeper dot ("1.3.2"
                // → "1.3.2a"), but not a numeric sibling ("1.3.2" → "1.3.20").
                ref.startsWith(reference) &&
                    ref.length > reference.length &&
                    (ref[reference.length].isLetter() || ref[reference.length] == '.') -> block += entry
                else -> return block
            }
            i++
        }
        return block
    }

    /**
     * Smart search:
     *  1. a reference-shaped query that exists → jump to that rule;
     *  2. an exact keyword (from the glossary) → jump to its definition;
     *  3. otherwise → free-text matches, in document order.
     */
    fun search(query: String, glossary: RulesGlossary? = null): CrSearchResult {
        val q = query.trim()
        if (q.isEmpty()) return CrSearchResult.Browse

        if (REFERENCE_QUERY.matches(q)) {
            indexOfReference(q)?.let { return CrSearchResult.Jump(it, q) }
        }

        glossary?.lookup(q)?.reference?.let { reference ->
            indexOfReference(reference)?.let { return CrSearchResult.Jump(it, reference) }
        }

        val hits = entries.mapIndexedNotNull { index, entry ->
            if (entry.text.contains(q, ignoreCase = true)) CrHit(index, entry) else null
        }
        return if (hits.isEmpty()) CrSearchResult.NoMatch else CrSearchResult.Matches(hits)
    }

    companion object {
        // A jump target: at least two dotted parts, optional trailing letter.
        private val REFERENCE_QUERY = Regex("""\d+\.\d+(?:\.\d+)*[a-z]?""")
        // A numbered line: leading reference then the rest of the line.
        private val NUMBERED = Regex("""^(\d+(?:\.\d+)*[a-z]?) (.*)$""")

        fun parse(crText: String): CrDocument {
            val entries = mutableListOf<CrEntry>()
            for (line in crText.lineSequence()) {
                if (line.isBlank()) continue
                val match = NUMBERED.matchEntire(line)
                if (match == null) {
                    entries += CrEntry(null, line, CrLevel.TEXT)
                    continue
                }
                val reference = match.groupValues[1]
                val body = match.groupValues[2]
                val dots = reference.trimEnd { it.isLetter() }.count { it == '.' }
                when {
                    dots >= 2 -> entries += CrEntry(reference, body, CrLevel.RULE)
                    dots == 1 -> entries += CrEntry(reference, body, CrLevel.SECTION)
                    // A bare number is only a chapter heading if it reads like one;
                    // otherwise it's prose that merely starts with a digit.
                    looksLikeHeading(body) -> entries += CrEntry(reference, body, CrLevel.CHAPTER)
                    else -> entries += CrEntry(null, line, CrLevel.TEXT)
                }
            }
            return CrDocument(entries)
        }

        private fun looksLikeHeading(text: String): Boolean =
            text.isNotEmpty() &&
                text.first().isUpperCase() &&
                !text.endsWith('.') &&
                text.length <= 40 &&
                text.split(' ').size <= 6
    }
}
