package com.example.life_counter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

/**
 * Full-screen card search overlay. Its own CardSearchViewModel drives the
 * search-as-you-type Flow pipeline; this composable is a pure function of the
 * `uiState` it collects.
 */
@Composable
fun CardSearchOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardSearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Autofocus the field so the user can type immediately after tapping SEARCH.
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Which result (if any) is zoomed to full size. Pure UI state, so it lives
    // here rather than in the ViewModel.
    var selectedCard by remember { mutableStateOf<Card?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Consume taps on empty areas so they can't reach the +/− zones
            // underneath. Unlike the history overlay this does NOT dismiss —
            // there's a text field to interact with — so onClick is a no-op.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            // Match HistoryOverlay's insets so both overlays start at the same
            // vertical position (below the status bar), not higher up.
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "CARD SEARCH",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
            )
            TextButton(onClick = onClose) {
                Text(text = "CLOSE")
            }
        }

        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text(text = "Search Flesh and Blood cards…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(focusRequester),
        )

        // The whole point: the body is a direct render of the pipeline's state.
        when (val s = uiState) {
            is SearchUiState.Idle -> Hint("Type at least 2 letters to search.")
            is SearchUiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is SearchUiState.Empty -> Hint("No cards found.")
            is SearchUiState.Error -> Hint("Search failed: ${s.message}")
            is SearchUiState.Results -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(s.cards) { card ->
                    CardRow(
                        card,
                        onClick = {
                            selectedCard = card
                            focusManager.clearFocus()
                        }
                    )
                }
            }
        }
    }

        // Tapping a result zooms its image; drawn last so it sits on top.
        selectedCard?.let { card ->
            EnlargedCard(card = card, onDismiss = { selectedCard = null })
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        fontSize = 16.sp,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            modifier = Modifier
                .width(60.dp)
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = card.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (card.typeText.isNotBlank()) {
                Text(
                    text = card.typeText,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
            cardMeta(card)?.let { meta ->
                Text(
                    text = meta,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
            if (card.functionalText.isNotBlank()) {
                Text(
                    text = card.functionalText,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// Full-screen zoom of a single card's image, over a dim scrim. Tap to dismiss.
@Composable
private fun EnlargedCard(card: Card, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (card.imageUrl != null) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            // No printing image for this card — show the name so the tap isn't a dead end.
            Text(text = card.name, color = Color.White, fontSize = 24.sp)
        }
    }
}

// Only include pitch/cost that the card actually has (many cards lack one).
private fun cardMeta(card: Card): String? {
    val parts = buildList {
        if (card.pitch.isNotBlank()) add("Pitch ${card.pitch}")
        if (card.cost.isNotBlank()) add("Cost ${card.cost}")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// FAB card face is ~450×628px.
private const val CARD_ASPECT_RATIO = 0.717f
