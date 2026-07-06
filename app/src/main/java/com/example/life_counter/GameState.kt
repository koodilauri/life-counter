package com.example.life_counter

enum class Player { ONE, TWO }

data class LifeChange(
    val player: Player,
    val amount: Int,
    val resultingTotal: Int,
    val timestamp: Long,
)

data class GameState(
    val player1Life: Int = STARTING_LIFE,
    val player2Life: Int = STARTING_LIFE,
    val history: List<LifeChange> = emptyList(),
) {
    fun lifeOf(player: Player): Int = when (player) {
        Player.ONE -> player1Life
        Player.TWO -> player2Life
    }

    companion object {
        const val STARTING_LIFE = 20
    }
}
