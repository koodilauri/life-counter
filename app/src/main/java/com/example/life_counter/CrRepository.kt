package com.example.life_counter

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File

/**
 * Where the Comprehensive Rules text lives locally: a writable cache (populated
 * by refreshes) plus the read-only bundled copy that always exists. A seam so
 * tests can supply fakes without touching the filesystem or app assets.
 */
interface CrStore {
    fun readCached(): String?           // null until a refresh has written one
    fun cachedLastModified(): String?   // the Last-Modified stored alongside the cache
    fun writeCached(text: String, lastModified: String?)
    fun readBundled(): String           // the packaged snapshot; always available
}

/** The outcome of a conditional fetch of the CR. */
sealed interface CrFetchResult {
    data object NotModified : CrFetchResult
    data class Updated(val text: String, val lastModified: String?) : CrFetchResult
    data class Failed(val error: Throwable) : CrFetchResult
}

/** Network seam for fetching the CR, conditional on a stored Last-Modified. */
interface CrRemote {
    suspend fun fetch(since: String?): CrFetchResult
}

/**
 * Serves a rules document's text with precedence **cache → bundle**, and can
 * refresh the cache from the network. A refresh only adopts a newer file if it
 * still parses to a plausible rules document, so a future format change can
 * never downgrade the app below its tested, bundled snapshot.
 */
class CrRepository(
    private val store: CrStore,
    private val remote: CrRemote,
    private val isPlausible: (String) -> Boolean = ::looksLikeRulesText,
) {

    /** The best text available right now: a refreshed cache, else the bundle. */
    fun loadText(): String = store.readCached() ?: store.readBundled()

    /**
     * Check for a newer document and adopt it if it's plausible. Returns true iff
     * the cache changed (so callers can re-parse). Never throws — any failure
     * leaves the current text in place.
     */
    suspend fun refresh(): Boolean {
        val result = runCatching { remote.fetch(store.cachedLastModified()) }
            .getOrElse { CrFetchResult.Failed(it) }
        return when (result) {
            is CrFetchResult.Updated -> {
                if (isPlausible(result.text)) {
                    store.writeCached(result.text, result.lastModified)
                    true
                } else {
                    false
                }
            }
            CrFetchResult.NotModified, is CrFetchResult.Failed -> false
        }
    }
}

// Doc-agnostic sanity check: a real rules document has many numbered lines
// (CR ~1300, TRP ~140, PPG ~40). Garbage or an error page has essentially none.
private const val MIN_NUMBERED_RULES = 20

private fun looksLikeRulesText(text: String): Boolean =
    CrDocument.parse(text).entries.count { it.reference != null } >= MIN_NUMBERED_RULES

// --- Android / Ktor implementations of the seams -----------------------------

/** Cache in the app's private files dir; bundle read from assets. Per document. */
class AndroidCrStore(context: Context, private val asset: String) : CrStore {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, asset)
    private val metaFile = File(appContext.filesDir, "$asset.lastmodified")

    override fun readCached(): String? = cacheFile.takeIf { it.exists() }?.readText()

    override fun cachedLastModified(): String? = metaFile.takeIf { it.exists() }?.readText()

    override fun writeCached(text: String, lastModified: String?) {
        cacheFile.writeText(text)
        if (lastModified != null) metaFile.writeText(lastModified) else metaFile.delete()
    }

    override fun readBundled(): String =
        appContext.assets.open(asset).bufferedReader().use { it.readText() }
}

/** Conditional GET against the official rules host for a given document URL. */
class KtorCrRemote(
    private val url: String,
    private val client: HttpClient = defaultClient(),
) : CrRemote {

    override suspend fun fetch(since: String?): CrFetchResult {
        val response: HttpResponse = client.get(url) {
            if (since != null) header(HttpHeaders.IfModifiedSince, since)
        }
        return when (response.status) {
            HttpStatusCode.NotModified -> CrFetchResult.NotModified
            HttpStatusCode.OK -> CrFetchResult.Updated(
                text = response.bodyAsText(),
                lastModified = response.headers[HttpHeaders.LastModified],
            )
            else -> CrFetchResult.Failed(IllegalStateException("HTTP ${response.status}"))
        }
    }

    companion object {
        private fun defaultClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
            }
        }
    }
}
