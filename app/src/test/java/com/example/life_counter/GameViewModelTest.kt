package com.example.life_counter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope launches on Dispatchers.Main; in a JVM test there is
        // no Android main thread, so substitute the virtual-time dispatcher.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with both players at starting life and no history`() {
        val viewModel = GameViewModel()

        val state = viewModel.state.value
        assertEquals(GameState.STARTING_LIFE, state.player1.life)
        assertEquals(GameState.STARTING_LIFE, state.player2.life)
        assertEquals(0, state.player1.pendingDelta)
        assertEquals(0, state.player2.pendingDelta)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `life changes immediately but history waits for the quiet period`() = runTest(dispatcher) {
        val viewModel = GameViewModel()

        viewModel.adjustLife(Player.ONE, -1)
        viewModel.adjustLife(Player.ONE, -1)
        viewModel.adjustLife(Player.ONE, -1)

        // Life total is live, the pending delta accumulates, nothing logged yet.
        var state = viewModel.state.value
        assertEquals(GameState.STARTING_LIFE - 3, state.player1.life)
        assertEquals(-3, state.player1.pendingDelta)
        assertTrue(state.history.isEmpty())

        advanceTimeBy(1_100) // let the 1s commit timer fire (virtual time)

        state = viewModel.state.value
        assertEquals(0, state.player1.pendingDelta)
        assertEquals(1, state.history.size)
        val entry = state.history.single()
        assertEquals(Player.ONE, entry.player)
        assertEquals(-3, entry.amount)
        assertEquals(GameState.STARTING_LIFE - 3, entry.resultingTotal)

        viewModel.resetGame() // stop the auto-started round timer
    }

    @Test
    fun `a new change within the window restarts the commit timer`() = runTest(dispatcher) {
        val viewModel = GameViewModel()

        viewModel.adjustLife(Player.ONE, -5)
        advanceTimeBy(600) // 0.6s of quiet — not enough to commit
        viewModel.adjustLife(Player.ONE, -1)
        advanceTimeBy(600) // 1.2s since first change, 0.6s since last

        assertTrue(viewModel.state.value.history.isEmpty())

        advanceTimeBy(500) // now 1.1s since the last change

        val history = viewModel.state.value.history
        assertEquals(1, history.size)
        assertEquals(-6, history.single().amount)
        assertEquals(GameState.STARTING_LIFE - 6, history.single().resultingTotal)

        viewModel.resetGame() // stop the auto-started round timer
    }

    @Test
    fun `players buffer and commit independently`() = runTest(dispatcher) {
        val viewModel = GameViewModel()

        viewModel.adjustLife(Player.ONE, -2)
        advanceTimeBy(800)
        viewModel.adjustLife(Player.TWO, 1) // must not restart player ONE's timer
        advanceTimeBy(300) // player ONE quiet for 1.1s, player TWO for 0.3s

        var history = viewModel.state.value.history
        assertEquals(1, history.size)
        assertEquals(Player.ONE, history.single().player)
        assertEquals(-2, history.single().amount)

        advanceTimeBy(900) // player TWO's commit fires at 1.8s

        history = viewModel.state.value.history
        assertEquals(2, history.size)
        assertEquals(Player.TWO, history[1].player)
        assertEquals(1, history[1].amount)

        viewModel.resetGame() // stop the auto-started round timer
    }

    @Test
    fun `a life change auto-starts the round timer`() = runTest(dispatcher) {
        val viewModel = GameViewModel()

        viewModel.adjustLife(Player.ONE, -1)

        assertEquals(true, viewModel.state.value.isTimerRunning)
        advanceTimeBy(3_600)
        assertEquals(3, viewModel.state.value.elapsedSeconds)

        viewModel.resetGame() // stop the auto-started round timer
    }

    @Test
    fun `a manual pause is not overridden by auto-start and stamps entries with the frozen time`() =
        runTest(dispatcher) {
            val viewModel = GameViewModel()

            viewModel.toggleTimer()
            advanceTimeBy(60_500) // one minute into the round
            viewModel.toggleTimer() // manual pause

            viewModel.adjustLife(Player.ONE, -4) // must NOT restart the clock
            advanceUntilIdle() // safe: timer is paused, only the commit job is pending

            val state = viewModel.state.value
            assertEquals(false, state.isTimerRunning)
            assertEquals(60, state.elapsedSeconds)
            val entry = state.history.single()
            assertEquals(-4, entry.amount)
            assertEquals(60, entry.elapsedSeconds)
        }

    // NOTE: every test that starts the timer must pause it before finishing.
    // The ticking coroutine reschedules itself forever, and runTest only
    // returns once the virtual-time task queue drains — a still-running
    // timer therefore makes the test loop endlessly.

    @Test
    fun `timer ticks once per second while running`() = runTest(dispatcher) {
        val viewModel = GameViewModel()
        assertEquals(false, viewModel.state.value.isTimerRunning)

        viewModel.toggleTimer()
        advanceTimeBy(3_500)

        assertEquals(true, viewModel.state.value.isTimerRunning)
        assertEquals(3, viewModel.state.value.elapsedSeconds)

        viewModel.toggleTimer() // stop ticking so runTest can finish
    }

    @Test
    fun `pausing freezes the clock and resuming continues from there`() = runTest(dispatcher) {
        val viewModel = GameViewModel()

        viewModel.toggleTimer()
        advanceTimeBy(2_500) // 2 ticks
        viewModel.toggleTimer() // pause
        advanceTimeBy(3_000) // quiet time on the paused clock

        assertEquals(false, viewModel.state.value.isTimerRunning)
        assertEquals(2, viewModel.state.value.elapsedSeconds)

        viewModel.toggleTimer() // resume
        advanceTimeBy(1_500) // 1 more tick

        assertEquals(3, viewModel.state.value.elapsedSeconds)

        viewModel.toggleTimer() // stop ticking so runTest can finish
    }

    @Test
    fun `resetGame stops and zeroes the timer`() = runTest(dispatcher) {
        val viewModel = GameViewModel()
        viewModel.toggleTimer()
        advanceTimeBy(10_000)

        viewModel.resetGame()
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.elapsedSeconds)
        assertEquals(false, viewModel.state.value.isTimerRunning)
    }

    @Test
    fun `resetGame restores starting state and abandons pending changes`() = runTest(dispatcher) {
        val viewModel = GameViewModel()
        viewModel.adjustLife(Player.ONE, -7)

        viewModel.resetGame()
        advanceUntilIdle() // a cancelled commit timer must not fire afterwards

        assertEquals(GameState(), viewModel.state.value)
    }

    @Test
    fun `resetGame can set a chosen starting life for both players`() = runTest(dispatcher) {
        val viewModel = GameViewModel()
        viewModel.adjustLife(Player.ONE, -3)

        viewModel.resetGame(startingLife = 20)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(20, state.player1.life)
        assertEquals(20, state.player2.life)
        assertTrue(state.history.isEmpty())
    }
}
