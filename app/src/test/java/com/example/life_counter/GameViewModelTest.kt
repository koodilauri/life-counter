package com.example.life_counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {

    @Test
    fun `starts with both players at starting life and no history`() {
        val viewModel = GameViewModel()

        val state = viewModel.state.value
        assertEquals(GameState.STARTING_LIFE, state.player1Life)
        assertEquals(GameState.STARTING_LIFE, state.player2Life)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `adjustLife changes only the targeted player`() {
        val viewModel = GameViewModel()

        viewModel.adjustLife(Player.ONE, -1)

        val state = viewModel.state.value
        assertEquals(GameState.STARTING_LIFE - 1, state.player1Life)
        assertEquals(GameState.STARTING_LIFE, state.player2Life)
    }

    @Test
    fun `every adjustment is recorded in history`() {
        val viewModel = GameViewModel()

        viewModel.adjustLife(Player.ONE, -5)
        viewModel.adjustLife(Player.TWO, 1)

        val history = viewModel.state.value.history
        assertEquals(2, history.size)

        val first = history[0]
        assertEquals(Player.ONE, first.player)
        assertEquals(-5, first.amount)
        assertEquals(GameState.STARTING_LIFE - 5, first.resultingTotal)

        val second = history[1]
        assertEquals(Player.TWO, second.player)
        assertEquals(1, second.amount)
        assertEquals(GameState.STARTING_LIFE + 1, second.resultingTotal)
    }

    @Test
    fun `resetGame restores starting state and clears history`() {
        val viewModel = GameViewModel()
        viewModel.adjustLife(Player.ONE, -7)

        viewModel.resetGame()

        assertEquals(GameState(), viewModel.state.value)
    }
}
