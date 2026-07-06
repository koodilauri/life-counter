package com.example.life_counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val commitJobs = mutableMapOf<Player, Job>()
    private var timerJob: Job? = null

    // Set only by an explicit pause. While true, life changes must NOT
    // auto-start the timer; only a manual toggle (or reset) clears it.
    private var manuallyPaused = false

    fun toggleTimer() {
        if (_state.value.isTimerRunning) {
            manuallyPaused = true
            pauseTimer()
        } else {
            manuallyPaused = false
            startTimer()
        }
    }

    private fun startTimer() {
        _state.update { it.copy(isTimerRunning = true) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    // Pausing IS cancelling the ticking coroutine — elapsedSeconds stays in
    // the state, so a later startTimer() resumes from where it left off.
    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(isTimerRunning = false) }
    }

    fun adjustLife(player: Player, amount: Int) {
        _state.update { current ->
            val p = current.player(player)
            current.withPlayer(
                player,
                p.copy(
                    life = p.life + amount,
                    pendingDelta = p.pendingDelta + amount,
                ),
            )
        }
        // The round has clearly begun once life changes — start the clock,
        // unless the user deliberately paused it.
        if (!_state.value.isTimerRunning && !manuallyPaused) {
            startTimer()
        }
        scheduleCommit(player)
    }

    fun resetGame() {
        commitJobs.values.forEach { it.cancel() }
        commitJobs.clear()
        timerJob?.cancel()
        timerJob = null
        manuallyPaused = false
        _state.value = GameState()
    }

    // Debounce: every adjustment cancels the player's previous commit timer
    // and starts a new one, so the entry is logged only after a quiet period.
    private fun scheduleCommit(player: Player) {
        commitJobs[player]?.cancel()
        commitJobs[player] = viewModelScope.launch {
            delay(COMMIT_DELAY_MS)
            commitPending(player)
        }
    }

    private fun commitPending(player: Player) {
        _state.update { current ->
            val p = current.player(player)
            if (p.pendingDelta == 0) return@update current
            val change = LifeChange(
                player = player,
                amount = p.pendingDelta,
                resultingTotal = p.life,
                elapsedSeconds = current.elapsedSeconds,
            )
            current
                .withPlayer(player, p.copy(pendingDelta = 0))
                .copy(history = current.history + change)
        }
    }

    companion object {
        const val COMMIT_DELAY_MS = 1_000L
    }
}
