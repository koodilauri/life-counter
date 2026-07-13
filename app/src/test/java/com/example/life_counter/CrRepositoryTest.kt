package com.example.life_counter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrRepositoryTest {

    private class FakeStore(
        var cached: String? = null,
        var lastMod: String? = null,
        val bundled: String = "BUNDLED",
    ) : CrStore {
        var writes = 0
        override fun readCached() = cached
        override fun cachedLastModified() = lastMod
        override fun writeCached(text: String, lastModified: String?) {
            cached = text
            lastMod = lastModified
            writes++
        }
        override fun readBundled() = bundled
    }

    private class FakeRemote(private val result: CrFetchResult) : CrRemote {
        val sinceSeen = mutableListOf<String?>()
        override suspend fun fetch(since: String?): CrFetchResult {
            sinceSeen += since
            return result
        }
    }

    // A rules-shaped slice with numbered lines — "plausible". The garbage has
    // none. The test guard accepts any document with at least one numbered rule.
    private val plausibleCr = """
        8.3 Ability Keywords
        8.3.4 Dominate
        Dominate is a keyword.
        8.3.5 Go again
        Go again is a keyword.
    """.trimIndent()

    private val garbageCr = "this text has no rule headers at all"

    private fun repo(store: CrStore, remote: CrRemote) =
        CrRepository(store, remote, isPlausible = { text ->
            CrDocument.parse(text).entries.any { it.reference != null }
        })

    @Test
    fun `loadText prefers the cached copy over the bundle`() {
        val repo = repo(FakeStore(cached = "CACHED"), FakeRemote(CrFetchResult.NotModified))
        assertEquals("CACHED", repo.loadText())
    }

    @Test
    fun `loadText falls back to the bundle when there is no cache`() {
        val repo = repo(FakeStore(cached = null), FakeRemote(CrFetchResult.NotModified))
        assertEquals("BUNDLED", repo.loadText())
    }

    @Test
    fun `refresh adopts a plausible newer CR and writes it to the cache`() = runTest {
        val store = FakeStore()
        val repo = repo(store, FakeRemote(CrFetchResult.Updated(plausibleCr, "Wed, 10 Jun 2026 00:00:00 GMT")))

        val changed = repo.refresh()

        assertTrue(changed)
        assertEquals(plausibleCr, store.cached)
        assertEquals("Wed, 10 Jun 2026 00:00:00 GMT", store.lastMod)
        assertEquals(1, store.writes)
    }

    @Test
    fun `refresh rejects an implausible CR and keeps the old text`() = runTest {
        val store = FakeStore(cached = "OLD")
        val repo = repo(store, FakeRemote(CrFetchResult.Updated(garbageCr, "x")))

        val changed = repo.refresh()

        assertFalse(changed)
        assertEquals("OLD", store.cached)
        assertEquals(0, store.writes)
    }

    @Test
    fun `refresh does nothing when the server reports NotModified`() = runTest {
        val store = FakeStore(cached = "OLD")
        val repo = repo(store, FakeRemote(CrFetchResult.NotModified))

        assertFalse(repo.refresh())
        assertEquals(0, store.writes)
    }

    @Test
    fun `refresh swallows fetch failures`() = runTest {
        val store = FakeStore(cached = "OLD")
        val repo = repo(store, FakeRemote(CrFetchResult.Failed(RuntimeException("boom"))))

        assertFalse(repo.refresh())
        assertEquals("OLD", store.cached)
    }

    @Test
    fun `refresh sends the stored last-modified as the conditional header`() = runTest {
        val store = FakeStore(cached = "OLD", lastMod = "Tue, 01 Jan 2026 00:00:00 GMT")
        val remote = FakeRemote(CrFetchResult.NotModified)
        repo(store, remote).refresh()

        assertEquals(listOf("Tue, 01 Jan 2026 00:00:00 GMT"), remote.sinceSeen)
    }

    @Test
    fun `refresh sends no conditional header when there is no cached copy yet`() = runTest {
        val store = FakeStore(cached = null, lastMod = null)
        val remote = FakeRemote(CrFetchResult.NotModified)
        repo(store, remote).refresh()

        assertEquals(listOf<String?>(null), remote.sinceSeen)
        assertNull(store.cached)
    }
}
