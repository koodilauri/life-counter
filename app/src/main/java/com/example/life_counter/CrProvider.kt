package com.example.life_counter

import android.content.Context

/**
 * Process-wide access to the parsed Comprehensive Rules — both the keyword
 * [glossary] and the full [document] — from the same cache-or-bundle text.
 *
 * Each is parsed once and memoized. [refreshOnce] does a single background update
 * check per app launch; if it adopts a newer CR it clears both memos so the next
 * access reparses.
 */
object CrProvider {

    @Volatile private var glossary: RulesGlossary? = null
    @Volatile private var document: CrDocument? = null
    @Volatile private var refreshAttempted = false

    fun glossary(context: Context): RulesGlossary =
        glossary ?: synchronized(this) {
            glossary ?: RulesGlossary.parse(loadText(context)).also { glossary = it }
        }

    fun document(context: Context): CrDocument =
        document ?: synchronized(this) {
            document ?: CrDocument.parse(loadText(context)).also { document = it }
        }

    /**
     * At most once per process: check for a newer CR and adopt it if plausible.
     * Returns true if the content changed (so the caller can re-read).
     */
    suspend fun refreshOnce(context: Context): Boolean {
        synchronized(this) {
            if (refreshAttempted) return false
            refreshAttempted = true
        }
        val changed = repository(context).refresh()
        if (changed) {
            synchronized(this) {
                glossary = null
                document = null
            }
        }
        return changed
    }

    private fun loadText(context: Context): String = repository(context).loadText()

    private fun repository(context: Context): CrRepository {
        val app = context.applicationContext
        return CrRepository(AndroidCrStore(app), KtorCrRemote())
    }
}
