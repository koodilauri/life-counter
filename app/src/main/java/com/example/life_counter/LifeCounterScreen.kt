package com.example.life_counter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LifeCounterScreen(
    state: GameState,
    onLifeChange: (Player, Int) -> Unit,
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
            life = state.player2Life,
            onLifeChange = { delta -> onLifeChange(Player.TWO, delta) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f),
        )
        MiddleBar(
            onShowHistory = { /* wired up in step 7 */ },
            onReset = onReset,
            modifier = Modifier.fillMaxWidth(),
        )
        PlayerPanel(
            life = state.player1Life,
            onLifeChange = { delta -> onLifeChange(Player.ONE, delta) },
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
    life: Int,
    onLifeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            AdjustZone(
                label = "+",
                onClick = { onLifeChange(+1) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            AdjustZone(
                label = "−",
                onClick = { onLifeChange(-1) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
        Text(
            text = life.toString(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 120.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun AdjustZone(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            fontSize = 44.sp,
        )
    }
}

@Composable
private fun MiddleBar(
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
        Text(
            text = "00:00", // placeholder — driven by the round timer in step 6
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
        )
        TextButton(onClick = onReset) {
            Text(text = "RESET")
        }
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LifeCounterScreenPreview() {
    LifeCounterTheme {
        LifeCounterScreen(
            state = GameState(player1Life = 18, player2Life = 21),
            onLifeChange = { _, _ -> },
            onReset = {},
        )
    }
}
