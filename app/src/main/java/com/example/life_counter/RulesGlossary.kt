package com.example.life_counter

/** One keyword definition from the Comprehensive Rules (chapter 8). */
data class GlossaryEntry(
    val keyword: String,      // "Go again"
    val reference: String,    // "8.3.5"
    val definition: String,   // the rule text + sub-rules/examples
) {
    /** The CR section, e.g. "8.3" — derived from the reference. */
    val section: String get() = reference.substringBeforeLast('.')
}

/** A detected keyword occurrence in a run of text: [start, end) and its keyword. */
data class KeywordSpan(val start: Int, val end: Int, val keyword: String)

/**
 * The FAB Comprehensive Rules keyword glossary, parsed from the bundled CR text.
 * Only chapter 8 keyword definitions are captured; only the ability/label/effect/
 * token sections (8.3–8.6) are offered as linkable, since 8.1/8.2 are ordinary
 * words (Action, Attack, …) that would over-link card text.
 */
class RulesGlossary(entries: List<GlossaryEntry>) {

    private val byKeyword: Map<String, GlossaryEntry> =
        entries.associateBy { it.keyword.lowercase() }

    /** Keywords worth linking in card text, longest first so multi-word ones win. */
    val linkableKeywords: List<String> =
        entries
            .filter { it.section in LINKABLE_SECTIONS }
            .map { it.keyword }
            .filter { it.lowercase() !in STOPWORDS }
            .distinct()
            .sortedByDescending { it.length }

    fun lookup(keyword: String): GlossaryEntry? = byKeyword[keyword.trim().lowercase()]

    companion object {
        private val LINKABLE_SECTIONS = setOf("8.3", "8.4", "8.5", "8.6")
        // Ability keyword "Attack" (8.3.1) collides with the everyday verb; drop it.
        private val STOPWORDS = setOf("attack")

        // A keyword header: "8.3.5 Go again". Three numeric parts, then a name.
        private val HEADER = Regex("""^(8\.\d+\.\d+) (.+)$""")
        // A section header ("8.4 Label Keywords") or the next chapter both close
        // the current entry. Sub-rules like "8.3.5a …" match neither, so they're
        // kept as body.
        private val SECTION = Regex("""^8\.\d+ \D""")
        private val NEXT_CHAPTER = Regex("""^9""")

        fun parse(crText: String): RulesGlossary {
            val entries = mutableListOf<GlossaryEntry>()
            var keyword: String? = null
            var reference = ""
            val body = StringBuilder()

            fun flush() {
                keyword?.let { entries += GlossaryEntry(it, reference, body.toString().trim()) }
                keyword = null
                reference = ""
                body.setLength(0)
            }

            for (line in crText.lineSequence()) {
                val header = HEADER.matchEntire(line)
                when {
                    header != null -> {
                        flush()
                        reference = header.groupValues[1]
                        keyword = header.groupValues[2].trim()
                    }
                    SECTION.containsMatchIn(line) || NEXT_CHAPTER.containsMatchIn(line) -> flush()
                    keyword != null -> body.appendLine(line)
                }
            }
            flush()
            return RulesGlossary(entries)
        }
    }
}

/**
 * Non-overlapping, case-insensitive, whole-word matches of [keywords] in [text].
 * [keywords] must be longest-first so "Arcane Barrier" claims its span before a
 * shorter keyword could grab part of it.
 */
fun findKeywordSpans(text: String, keywords: List<String>): List<KeywordSpan> {
    val taken = BooleanArray(text.length)
    val spans = mutableListOf<KeywordSpan>()
    for (keyword in keywords) {
        // Letters on either side would make it part of a larger word (e.g. "ward"
        // inside "warden"), so guard both edges with letter look-arounds.
        val regex = Regex(
            "(?<!\\p{L})" + Regex.escape(keyword) + "(?!\\p{L})",
            RegexOption.IGNORE_CASE,
        )
        for (match in regex.findAll(text)) {
            val range = match.range
            if (range.any { taken[it] }) continue
            for (i in range) taken[i] = true
            spans += KeywordSpan(range.first, range.last + 1, keyword)
        }
    }
    return spans.sortedBy { it.start }
}
