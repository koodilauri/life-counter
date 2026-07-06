package com.example.life_counter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun adjustLife(player: Player, amount: Int) {
        _state.update { current ->
            val newTotal = current.lifeOf(player) + amount
            val change = LifeChange(
                player = player,
                amount = amount,
                resultingTotal = newTotal,
                timestamp = System.currentTimeMillis(),
            )
            when (player) {
                Player.ONE -> current.copy(
                    player1Life = newTotal,
                    history = current.history + change,
                )
                Player.TWO -> current.copy(
                    player2Life = newTotal,
                    history = current.history + change,
                )
            }
        }
    }

    fun resetGame() {
        _state.value = GameState()
    }
}
