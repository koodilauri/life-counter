package com.example.life_counter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    onReset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the history overlay / reset dialog are open is pure UI state —
    // no game rule depends on it — so it lives here, not in the ViewModel.
    var showHistory by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                onShowHistory = { showHistory = true },
                onResetRequest = { showResetDialog = true },
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
        if (showHistory) {
            HistoryOverlay(
                history = state.history,
                onClose = { showHistory = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showResetDialog) {
            ResetDialog(
                onConfirm = { startingLife ->
                    showResetDialog = false
                    onReset(startingLife)
                },
                onCancel = { showResetDialog = false },
            )
        }
    }
}

@Composable
private fun ResetDialog(
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel, // tap outside / back button = cancel
        title = { Text(text = "Reset game?") },
        text = { Text(text = "Life totals, history and the round timer will be cleared. Choose the starting life.") },
        confirmButton = {
            TextButton(onClick = { onConfirm(40) }) {
                Text(text = "RESET TO 40")
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(20) }) {
                Text(text = "RESET TO 20")
            }
        },
    )
}

@Composable
private fun HistoryOverlay(
    history: List<LifeChange>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            // Swallows taps so they can't reach the +/− zones underneath;
            // indication = null turns off the ripple for this catch-all area.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            )
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "HISTORY",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
            )
            TextButton(onClick = onClose) {
                Text(text = "CLOSE")
            }
        }
        if (history.isEmpty()) {
            Text(
                text = "No life changes yet.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            // One column per player, labelled by which half of the screen
            // they sit at — clearer than P1/P2, which appears nowhere else.
            Row(modifier = Modifier.fillMaxSize()) {
                HistoryColumn(
                    label = "YOU",
                    entries = history.filter { it.player == Player.ONE },
                    modifier = Modifier.weight(1f),
                )
                HistoryColumn(
                    label = "OPPONENT",
                    entries = history.filter { it.player == Player.TWO },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HistoryColumn(
    label: String,
    entries: List<LifeChange>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries.asReversed()) { change ->
                HistoryRow(change = change)
            }
        }
    }
}

@Composable
private fun HistoryRow(
    change: LifeChange,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTime(change.elapsedSeconds),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "${formatDelta(change.amount)} → ${change.resultingTotal}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
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
    BoxWithConstraints(modifier = modifier) {
        val panelWidth = maxWidth
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
        // The life total is centered in the panel.
        val lifeText = player.life.toString()
        val factor = when (lifeText.length) {
            1 -> 0.8f
            2 -> 0.55f
            else -> 0.45f
        }
        Text(
            text = lifeText,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = (panelWidth.value * factor).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )

        // The pending delta stays on the side, vertically centered.
        Text(
            text = formatDelta(player.pendingDelta),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = (panelWidth.value * 0.12f).sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
                .alpha(if (player.pendingDelta == 0) 0f else 1f),
        )
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
    onResetRequest: () -> Unit,
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
                fontSize = 60.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        TextButton(onClick = onResetRequest) {
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
                history = listOf(
                    LifeChange(Player.TWO, +1, 21, 312),
                    LifeChange(Player.ONE, -2, 18, 145),
                ),
                elapsedSeconds = 754,
                isTimerRunning = true,
            ),
            onLifeChange = { _, _ -> },
            onToggleTimer = {},
            onReset = {},
        )
    }
}
