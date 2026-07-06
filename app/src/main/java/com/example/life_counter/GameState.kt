package com.example.life_counter

enum class Player { ONE, TWO }

data class LifeChange(
    val player: Player,
    val amount: Int,
    val resultingTotal: Int,
    // Round-timer time (in seconds) when the change was committed, not wall
    // clock — "P1 lost 6 at 12:34 into the round".
    val elapsedSeconds: Int,
)

data class PlayerState(
    val life: Int = GameState.STARTING_LIFE,
    // Accumulated not-yet-logged change; committed to history as one entry
    // after a quiet period with no further adjustments.
    val pendingDelta: Int = 0,
)

data class GameState(
    val player1: PlayerState = PlayerState(),
    val player2: PlayerState = PlayerState(),
    val history: List<LifeChange> = emptyList(),
    val elapsedSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
) {
    fun player(player: Player): PlayerState = when (player) {
        Player.ONE -> player1
        Player.TWO -> player2
    }

    fun withPlayer(player: Player, state: PlayerState): GameState = when (player) {
        Player.ONE -> copy(player1 = state)
        Player.TWO -> copy(player2 = state)
    }

    companion object {
        const val STARTING_LIFE = 40
    }
}
