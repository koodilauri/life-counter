package com.example.life_counter

import android.content.Context

/**
 * Process-wide access to the parsed rules documents — the full [document] for
 * any [RulesDoc], plus the keyword [glossary] (Comprehensive Rules only).
 *
 * Each parse is memoized. [refreshOnce] does a single background update check
 * per app launch across all documents; anything it adopts clears the relevant
 * memo so the next access reparses.
 */
object CrProvider {

    private val documents = mutableMapOf<RulesDoc, CrDocument>()
    @Volatile private var glossary: RulesGlossary? = null
    @Volatile private var refreshAttempted = false

    fun document(context: Context, doc: RulesDoc): CrDocument = synchronized(this) {
        documents.getOrPut(doc) { CrDocument.parse(loadText(context, doc)) }
    }

    fun glossary(context: Context): RulesGlossary =
        glossary ?: synchronized(this) {
            glossary ?: RulesGlossary.parse(loadText(context, RulesDoc.CR)).also { glossary = it }
        }

    /**
     * At most once per process: check every document for a newer version and
     * adopt any that are plausible. Returns true if anything changed (so the
     * caller can re-read).
     */
    suspend fun refreshOnce(context: Context): Boolean {
        synchronized(this) {
            if (refreshAttempted) return false
            refreshAttempted = true
        }
        var anyChanged = false
        for (doc in RulesDoc.entries) {
            if (repository(context, doc).refresh()) {
                anyChanged = true
                synchronized(this) {
                    documents.remove(doc)
                    if (doc == RulesDoc.CR) glossary = null
                }
            }
        }
        return anyChanged
    }

    private fun loadText(context: Context, doc: RulesDoc): String =
        repository(context, doc).loadText()

    private fun repository(context: Context, doc: RulesDoc): CrRepository {
        val app = context.applicationContext
        return CrRepository(AndroidCrStore(app, doc.asset), KtorCrRemote(doc.url))
    }
}
