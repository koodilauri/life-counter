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
 * Serves the CR text with precedence **cache → bundle**, and can refresh the
 * cache from the network. A refresh only adopts a newer file if it still parses
 * to a plausible CR, so a future format change can never downgrade the app below
 * its tested, bundled snapshot.
 */
class CrRepository(
    private val store: CrStore,
    private val remote: CrRemote,
    private val minLinkableKeywords: Int = MIN_LINKABLE_KEYWORDS,
) {

    /** The best CR text available right now: a refreshed cache, else the bundle. */
    fun loadText(): String = store.readCached() ?: store.readBundled()

    /**
     * Check for a newer CR and adopt it if it's plausible. Returns true iff the
     * cache changed (so callers can re-parse). Never throws — any failure leaves
     * the current text in place.
     */
    suspend fun refresh(): Boolean {
        val result = runCatching { remote.fetch(store.cachedLastModified()) }
            .getOrElse { CrFetchResult.Failed(it) }
        return when (result) {
            is CrFetchResult.Updated -> {
                if (isPlausibleCr(result.text)) {
                    store.writeCached(result.text, result.lastModified)
                    true
                } else {
                    false
                }
            }
            CrFetchResult.NotModified, is CrFetchResult.Failed -> false
        }
    }

    private fun isPlausibleCr(text: String): Boolean =
        RulesGlossary.parse(text).linkableKeywords.size >= minLinkableKeywords

    companion object {
        // The real CR has ~160 linkable keywords; well below that means the parse
        // produced garbage (e.g. the file format changed) — reject it.
        const val MIN_LINKABLE_KEYWORDS = 50
    }
}

// --- Android / Ktor implementations of the seams -----------------------------

const val CR_ASSET_NAME = "fab-cr.txt"

/** Cache in the app's private files dir; bundle read from assets. */
class AndroidCrStore(context: Context) : CrStore {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CR_ASSET_NAME)
    private val metaFile = File(appContext.filesDir, "$CR_ASSET_NAME.lastmodified")

    override fun readCached(): String? = cacheFile.takeIf { it.exists() }?.readText()

    override fun cachedLastModified(): String? = metaFile.takeIf { it.exists() }?.readText()

    override fun writeCached(text: String, lastModified: String?) {
        cacheFile.writeText(text)
        if (lastModified != null) metaFile.writeText(lastModified) else metaFile.delete()
    }

    override fun readBundled(): String =
        appContext.assets.open(CR_ASSET_NAME).bufferedReader().use { it.readText() }
}

/** Conditional GET against the official rules host. */
class KtorCrRemote(private val client: HttpClient = defaultClient()) : CrRemote {

    override suspend fun fetch(since: String?): CrFetchResult {
        val response: HttpResponse = client.get(CR_URL) {
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
        private const val CR_URL = "https://rules.fabtcg.com/txt/latest/en-fab-cr.txt"

        private fun defaultClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
            }
        }
    }
}
