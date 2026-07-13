package com.example.life_counter

import android.content.Context

/**
 * Process-wide access to the parsed CR keyword glossary.
 *
 * `current` returns immediately from the cache-or-bundle text (parsed once, then
 * memoized). `refreshOnce` does a single background update check per app launch;
 * if it adopts a newer CR it clears the memo so the next `current` reparses.
 */
object RulesGlossaryProvider {

    @Volatile private var glossary: RulesGlossary? = null
    @Volatile private var refreshAttempted = false

    /** Parsed glossary from the best local text available. Cheap after the first call. */
    fun current(context: Context): RulesGlossary =
        glossary ?: synchronized(this) {
            glossary ?: RulesGlossary.parse(repository(context).loadText()).also { glossary = it }
        }

    /**
     * At most once per process: check for a newer CR and adopt it if plausible.
     * Returns true if the glossary changed (so the caller can re-read `current`).
     */
    suspend fun refreshOnce(context: Context): Boolean {
        synchronized(this) {
            if (refreshAttempted) return false
            refreshAttempted = true
        }
        val changed = repository(context).refresh()
        if (changed) {
            synchronized(this) { glossary = null }
        }
        return changed
    }

    private fun repository(context: Context): CrRepository {
        val app = context.applicationContext
        return CrRepository(AndroidCrStore(app), KtorCrRemote())
    }
}
