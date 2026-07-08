package com.example.life_counter

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
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

    // LocalConfiguration is a CompositionLocal — think of it like React context,
    // but supplied by the platform: it recomposes whenever the device config
    // (orientation, screen size, ...) changes, e.g. on a rotation.
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Portrait stacks the two panels top/bottom (a Column, split by
        // height). Landscape's height is short, so that same split would
        // squeeze each panel into a thin band — instead landscape uses a Row,
        // splitting the (now generous) width between the panels, each turned
        // on its side with `rotatedFit` so it fits its slot without overflow.
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                PlayerPanel(
                    player = state.player2,
                    onAdjust = { delta -> onLifeChange(Player.TWO, delta) },
                    isLandscape = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .rotatedFit(270f),
                )
                MiddleBar(
                    elapsedSeconds = state.elapsedSeconds,
                    isTimerRunning = state.isTimerRunning,
                    onToggleTimer = onToggleTimer,
                    onShowHistory = { showHistory = true },
                    onResetRequest = { showResetDialog = true },
                    isLandscape = true,
                    modifier = Modifier.fillMaxHeight(),
                )
                PlayerPanel(
                    player = state.player1,
                    onAdjust = { delta -> onLifeChange(Player.ONE, delta) },
                    isLandscape = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .rotatedFit(90f),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Opponent's half: composed upright, then rotated 180° so it
                // reads correctly for the player sitting across the table.
                // Touch input is rotated along with it.
                PlayerPanel(
                    player = state.player2,
                    onAdjust = { delta -> onLifeChange(Player.TWO, delta) },
                    isLandscape = false,
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
                    isLandscape = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlayerPanel(
                    player = state.player1,
                    onAdjust = { delta -> onLifeChange(Player.ONE, delta) },
                    isLandscape = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
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

// A plain `.rotate()` only repaints pixels — it doesn't change what the
// child measures itself against. A 90°/270° rotation inside a box that
// wasn't sized for that orientation (e.g. a wide-short Row/Column slot)
// will overflow it. This measures the child against *swapped* constraints
// — as if width and height were transposed, its true shape once turned on
// its side — then reports its own size back as that measured result
// swapped back (not the incoming constraints' upper bound — for a
// wrap-content parent like MiddleBar's landscape Column, that bound can be
// almost the full screen width, which would make every rotated child claim
// that much space). For 0°/180° the shape doesn't change, so it's a plain
// rotate.
private fun Modifier.rotatedFit(degrees: Float): Modifier =
    if (degrees % 180f == 0f) {
        rotate(degrees)
    } else {
        layout { measurable, constraints ->
            val swapped = Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
            val placeable = measurable.measure(swapped)
            val width = placeable.height
            val height = placeable.width
            layout(width, height) {
                placeable.placeWithLayer(
                    x = (width - placeable.width) / 2,
                    y = (height - placeable.height) / 2,
                ) {
                    rotationZ = degrees
                }
            }
        }
    }

// In the panel's own frame of reference the far zone (away from the player)
// increments and the near zone decrements; the 180° rotation of the opponent
// panel keeps that physically true for both players.
@Composable
private fun PlayerPanel(
    player: PlayerState,
    onAdjust: (Int) -> Unit,
    isLandscape: Boolean,
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
            1, 2 -> 0.55f
            else -> 0.45f
        }
        // The number and its ambiguity underline are rotated together as one
        // unit — rotating just the Text would leave the underline (positioned
        // by an offset relative to this Column) pointing the old, un-rotated
        // "down", which after a 90° turn lands beside the number instead of
        // under it.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(if (isLandscape) -90f else 0f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = lifeText,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = (panelWidth.value * factor).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            // A subtle line below the number for 6 and 9 to clarify orientation.
            val isAmbiguous = lifeText == "6" || lifeText == "9"
            if (isAmbiguous) {
                Box(
                    modifier = Modifier
                        .offset(y = (-panelWidth.value * 0.11f).dp)
                        .width(panelWidth * 0.28f)
                        .height(panelWidth * 0.005f)
                        .background(color = MaterialTheme.colorScheme.onBackground),
                )
            }
        }

        // The pending delta stays on the side, positioned above the life total.
        Text(
            text = formatDelta(player.pendingDelta),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = (panelWidth.value * 0.12f).sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 60.dp)
                .rotate(if (isLandscape) -90f else 0f)
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
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // The bottom life counter's number ends up net upright on screen: its
    // panel is rotated +90° but its own text counter-rotates -90°, so the
    // two cancel out. MiddleBar has no panel-level rotation to cancel, so
    // matching that same upright appearance means simply not rotating these
    // texts at all — a -90° or +90° rotate would still read sideways.
    val textRotation = 0f
    val clockText: @Composable () -> Unit = {
        Text(
            text = formatTime(elapsedSeconds),
            color = MaterialTheme.colorScheme.onBackground
                .copy(alpha = if (isTimerRunning) 1f else 0.4f),
            fontSize = 60.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.rotatedFit(textRotation),
        )
    }
    // In landscape this becomes a vertical strip between the two panels, so
    // it switches from a horizontal Row to a vertical Column and each text
    // rotates 90° (via rotatedFit, so it still fits its own slot).
    if (isLandscape) {
        Column(
            modifier = modifier.padding(vertical = 24.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onShowHistory) {
                Text(text = "HISTORY", modifier = Modifier.rotatedFit(textRotation))
            }
            // Tap the clock to pause/resume; it dims while paused.
            TextButton(onClick = onToggleTimer) { clockText() }
            TextButton(onClick = onResetRequest) {
                Text(text = "RESET", modifier = Modifier.rotatedFit(textRotation))
            }
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onShowHistory) {
                Text(text = "HISTORY")
            }
            // Tap the clock to pause/resume; it dims while paused.
            TextButton(onClick = onToggleTimer) { clockText() }
            TextButton(onClick = onResetRequest) {
                Text(text = "RESET")
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

private fun previewGameState() = GameState(
    player1 = PlayerState(life = 9, pendingDelta = -2),
    player2 = PlayerState(life = 21),
    history = listOf(
        LifeChange(Player.TWO, +1, 21, 312),
        LifeChange(Player.ONE, -2, 18, 145),
    ),
    elapsedSeconds = 754,
    isTimerRunning = true,
)

@Preview(widthDp = 411, heightDp = 891, name = "Portrait")
@Composable
private fun LifeCounterScreenPreview() {
    LifeCounterTheme {
        LifeCounterScreen(
            state = previewGameState(),
            onLifeChange = { _, _ -> },
            onToggleTimer = {},
            onReset = {},
        )
    }
}

// Preview tooling derives Configuration.orientation from whether widthDp
// exceeds heightDp, so swapping the two dimensions is enough to make
// LocalConfiguration.current.orientation report ORIENTATION_LANDSCAPE here —
// no device or emulator rotation needed to see this layout.
@Preview(widthDp = 891, heightDp = 411, name = "Landscape")
@Composable
private fun LifeCounterScreenLandscapePreview() {
    LifeCounterTheme {
        LifeCounterScreen(
            state = previewGameState(),
            onLifeChange = { _, _ -> },
            onToggleTimer = {},
            onReset = {},
        )
    }
}
