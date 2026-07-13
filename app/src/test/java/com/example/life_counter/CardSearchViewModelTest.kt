package com.example.life_counter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A stand-in for the network. Records the queries it was asked to run (started)
 * separately from the ones that ran to completion — so a test can prove that a
 * stale, still-in-flight search was cancelled (started but never completed).
 */
private class FakeCardRepository(
    private val delayMs: Long = 0,
    private val error: Throwable? = null,
) : CardRepository {
    val started = mutableListOf<String>()
    val completed = mutableListOf<String>()

    override suspend fun search(query: String): List<Card> {
        started += query
        if (delayMs > 0) delay(delayMs)
        error?.let { throw it }
        completed += query
        return listOf(sampleCard(query))
    }

    companion object {
        fun sampleCard(name: String) = Card(
            name = name,
            typeText = "Action",
            variants = listOf(
                CardVariant(
                    color = CardColor.RED, pitch = "1", cost = "0", functionalText = "", imageUrl = null,
                    legality = Legality(legalFormats = listOf("Blitz", "CC"), bannedFormats = emptyList()),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CardSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing a query searches after the debounce and shows results`() = runTest(dispatcher) {
        val repo = FakeCardRepository()
        val viewModel = CardSearchViewModel(repo)
        // stateIn(WhileSubscribed) only runs the pipeline while something
        // collects it; backgroundScope is auto-cancelled when the test ends.
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChange("enlightened")
        advanceTimeBy(350)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        assertEquals(1, (state as SearchUiState.Results).cards.size)
        assertEquals(listOf("enlightened"), repo.started)
    }

    @Test
    fun `rapid typing debounces down to a single search`() = runTest(dispatcher) {
        val repo = FakeCardRepository()
        val viewModel = CardSearchViewModel(repo)
        backgroundScope.launch { viewModel.uiState.collect {} }

        // Three keystrokes inside the debounce window.
        viewModel.onQueryChange("bl")
        viewModel.onQueryChange("bla")
        viewModel.onQueryChange("blaz")
        advanceTimeBy(350)
        runCurrent()

        // Only the final query ever reached the repository.
        assertEquals(listOf("blaz"), repo.started)
    }

    @Test
    fun `a newer query cancels the stale in-flight search`() = runTest(dispatcher) {
        val repo = FakeCardRepository(delayMs = 500)
        val viewModel = CardSearchViewModel(repo)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChange("aa")
        advanceTimeBy(300) // debounce fires; search("aa") starts its 500ms work
        runCurrent()
        assertTrue(viewModel.uiState.value is SearchUiState.Loading)

        advanceTimeBy(100) // 100ms into aa's search — still in flight
        viewModel.onQueryChange("bb")
        advanceTimeBy(300) // debounce fires again; flatMapLatest cancels aa, starts bb
        runCurrent()

        advanceTimeBy(500) // let bb finish
        runCurrent()

        // Both searches were kicked off, but aa was cancelled before completing.
        assertEquals(listOf("aa", "bb"), repo.started)
        assertEquals(listOf("bb"), repo.completed)

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Results)
        assertEquals("bb", (state as SearchUiState.Results).cards.single().name)
    }

    @Test
    fun `a failing search surfaces as an error state`() = runTest(dispatcher) {
        val repo = FakeCardRepository(error = RuntimeException("boom"))
        val viewModel = CardSearchViewModel(repo)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChange("enlightened")
        advanceTimeBy(350)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Error)
        assertEquals("boom", (state as SearchUiState.Error).message)
    }

    @Test
    fun `queries shorter than the minimum stay idle and never hit the repo`() = runTest(dispatcher) {
        val repo = FakeCardRepository()
        val viewModel = CardSearchViewModel(repo)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChange("e")
        advanceTimeBy(350)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Idle)
        assertTrue(repo.started.isEmpty())
    }
}
