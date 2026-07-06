package com.example.life_counter

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAP_STEP = 1
private const val HOLD_STEP = 5
private const val HOLD_REPEAT_INTERVAL_MS = 500L

@Composable
fun LifeCounterScreen(
    state: GameState,
    onLifeChange: (Player, Int) -> Unit,
    onToggleTimer: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Opponent's half: composed upright, then rotated 180° so it reads
        // correctly for the player sitting across the table. Touch input is
        // rotated along with it.
        PlayerPanel(
            player = state.player2,
            onAdjust = { delta -> onLifeChange(Player.TWO, delta) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f),
        )
        MiddleBar(
            elapsedSeconds = state.elapsedSeconds,
            isTimerRunning = state.isTimerRunning,
            onToggleTimer = onToggleTimer,
            onShowHistory = { /* wired up in step 7 */ },
            onReset = onReset,
            modifier = Modifier.fillMaxWidth(),
        )
        PlayerPanel(
            player = state.player1,
            onAdjust = { delta -> onLifeChange(Player.ONE, delta) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

// In the panel's own frame of reference the far zone (away from the player)
// increments and the near zone decrements; the 180° rotation of the opponent
// panel keeps that physically true for both players.
@Composable
private fun PlayerPanel(
    player: PlayerState,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            AdjustZone(
                label = "+",
                direction = +1,
                onAdjust = onAdjust,
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            AdjustZone(
                label = "−",
                direction = -1,
                onAdjust = onAdjust,
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Not-yet-committed change, e.g. "−3"; rendered invisible (but
            // still taking up space, so the life total never jumps) when zero.
            Text(
                text = formatDelta(player.pendingDelta),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 40.sp,
                modifier = Modifier.alpha(if (player.pendingDelta == 0) 0f else 1f),
            )
            Text(
                text = player.life.toString(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// Tap: ±1. Press and hold: ±5 once the long-press threshold passes, then
// ±5 again every repeat interval for as long as the finger stays down.
@Composable
private fun AdjustZone(
    label: String,
    direction: Int,
    onAdjust: (Int) -> Unit,
    contentAlignment: Alignment,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.pointerInput(direction) {
            val holdDelayMs = viewConfiguration.longPressTimeoutMillis
            detectTapGestures(
                onTap = { onAdjust(direction * TAP_STEP) },
                // Present so that detectTapGestures does NOT also fire onTap
                // when a long hold is released; the actual work happens in the
                // repeat job below.
                onLongPress = {},
                onPress = {
                    val repeatJob = scope.launch {
                        delay(holdDelayMs)
                        while (isActive) {
                            onAdjust(direction * HOLD_STEP)
                            delay(HOLD_REPEAT_INTERVAL_MS)
                        }
                    }
                    tryAwaitRelease()
                    repeatJob.cancel()
                },
            )
        },
        contentAlignment = contentAlignment,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            fontSize = 44.sp,
            modifier = Modifier.padding(vertical = 20.dp),
        )
    }
}

private fun formatDelta(delta: Int): String =
    if (delta < 0) "−${-delta}" else "+$delta"

@Composable
private fun MiddleBar(
    elapsedSeconds: Int,
    isTimerRunning: Boolean,
    onToggleTimer: () -> Unit,
    onShowHistory: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onShowHistory) {
            Text(text = "HISTORY")
        }
        // Tap the clock to pause/resume; it dims while paused.
        TextButton(onClick = onToggleTimer) {
            Text(
                text = formatTime(elapsedSeconds),
                color = MaterialTheme.colorScheme.onBackground
                    .copy(alpha = if (isTimerRunning) 1f else 0.4f),
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        TextButton(onClick = onReset) {
            Text(text = "RESET")
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LifeCounterScreenPreview() {
    LifeCounterTheme {
        LifeCounterScreen(
            state = GameState(
                player1 = PlayerState(life = 18, pendingDelta = -2),
                player2 = PlayerState(life = 21),
                elapsedSeconds = 754,
                isTimerRunning = true,
            ),
            onLifeChange = { _, _ -> },
            onToggleTimer = {},
            onReset = {},
        )
    }
}
