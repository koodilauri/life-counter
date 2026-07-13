package com.example.life_counter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen card search overlay. Its own CardSearchViewModel drives the
 * search-as-you-type Flow pipeline; this composable is a pure function of the
 * `uiState` it collects.
 */
@Composable
fun CardSearchOverlay(
    onClose: () -> Unit,
    onOpenRule: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardSearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Autofocus the field so the user can type immediately after tapping SEARCH.
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Card search is portrait-only: even opened in landscape, force the display
    // upright so the card images and rules text read normally.
    ForcePortrait()

    // The CR glossary (for keyword links) + document (for peeking a keyword's
    // rule). Loaded off the main thread, then refreshed once. See CrProvider.
    val context = LocalContext.current
    val content by produceState<CrContent?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            CrContent(CrProvider.document(context, RulesDoc.CR), CrProvider.glossary(context))
        }
        val updated = withContext(Dispatchers.IO) { CrProvider.refreshOnce(context) }
        if (updated) {
            value = withContext(Dispatchers.IO) {
                CrContent(CrProvider.document(context, RulesDoc.CR), CrProvider.glossary(context))
            }
        }
    }

    // Which result (if any) is zoomed to full size. Pure UI state, so it lives
    // here rather than in the ViewModel.
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    // Which keyword's rule is being peeked, by CR reference (e.g. "8.3.5").
    var peekRef by remember { mutableStateOf<String?>(null) }

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
            // Only offer the clear button when there's something to clear.
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.onQueryChange("")
                            // Keep the keyboard up so the user can retype.
                            focusRequester.requestFocus()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
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
            EnlargedCard(
                card = card,
                onDismiss = { selectedCard = null },
                glossary = content?.glossary,
                onPeekReference = { peekRef = it },
            )
        }

        // Peeking a keyword's rule — the same popup the CR reader uses. "Go to
        // rule" hands off to the full CR view at that reference.
        content?.document?.let { document ->
            peekRef?.let { reference ->
                RulePeekDialog(
                    document = document,
                    reference = reference,
                    onPeek = { peekRef = it },
                    onGoTo = {
                        peekRef = null
                        onOpenRule(reference)
                    },
                    onDismiss = { peekRef = null },
                )
            }
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
            model = card.primary.imageUrl,
            contentDescription = card.name,
            modifier = Modifier
                .width(60.dp)
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.padding(top = 2.dp)) {
            // Name + its available colors, so multi-pitch cards are obvious at a
            // glance without opening them.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (card.colors.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ColorPips(card.colors)
                }
            }
            if (card.typeText.isNotBlank()) {
                Text(
                    text = card.typeText,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
            if (card.primary.functionalText.isNotBlank()) {
                Text(
                    text = card.primary.functionalText,
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

// A row of small solid dots, one per color. Non-interactive — used in the list.
@Composable
private fun ColorPips(colors: List<CardColor>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color.pipColor()),
            )
        }
    }
}

// Full-screen zoom of a single card, over a dim scrim. Tap the scrim to
// dismiss; tap a pitch pip (multi-pitch cards) to swap which variant is shown.
@Composable
private fun EnlargedCard(
    card: Card,
    onDismiss: () -> Unit,
    glossary: RulesGlossary?,
    onPeekReference: (String) -> Unit,
) {
    // Which pitch variant is on screen. Keyed on `card` so it resets to the
    // default whenever a different card is opened.
    var selected by remember(card) { mutableStateOf(card.primary) }
    // Image vs. rules text. The printed image can be outdated after an errata,
    // so the text (from the API) is offered as the authoritative alternative.
    var showText by remember(card) { mutableStateOf(false) }

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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            if (showText || selected.imageUrl == null) {
                RulesTextPanel(
                    card = card,
                    variant = selected,
                    glossary = glossary,
                    // Tapping a keyword peeks its CR rule, resolved by reference.
                    onKeyword = { keyword ->
                        glossary?.lookup(keyword)?.reference?.let(onPeekReference)
                    },
                )
            } else {
                AsyncImage(
                    model = selected.imageUrl,
                    contentDescription = card.name,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(CARD_ASPECT_RATIO)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            // Only offer the toggle when there's actually an image to switch
            // back to; imageless cards stay on the text panel.
            if (selected.imageUrl != null) {
                TextButton(
                    onClick = { showText = !showText },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(text = if (showText) "SHOW CARD" else "SHOW RULES TEXT")
                }
            }

            // Pitch selector: one tappable pip per variant, only when there's a
            // choice to make.
            if (card.variants.size > 1) {
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    card.variants.forEach { variant ->
                        PitchPip(
                            variant = variant,
                            isSelected = variant == selected,
                            onClick = { selected = variant },
                        )
                    }
                }
            }

            LegalityRow(
                summary = card.legalitySummary(),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

// A large, tappable pitch pip: colored circle with its pitch number, ringed
// white when selected.
@Composable
private fun PitchPip(variant: CardVariant, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(variant.color.pipColor())
            .then(
                if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = variant.pitch,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// The card's text as the API reports it — the authoritative version when the
// printed image is out of date (errata). Reads from the selected variant, so
// switching pitch pips updates the text/stats.
@Composable
private fun RulesTextPanel(
    card: Card,
    variant: CardVariant,
    glossary: RulesGlossary?,
    onKeyword: (String) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = card.name,
            color = onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        if (card.typeText.isNotBlank()) {
            Text(
                text = card.typeText,
                color = onSurface.copy(alpha = 0.6f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        statLine(variant)?.let { line ->
            Text(
                text = line,
                color = onSurface.copy(alpha = 0.6f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        KeywordText(
            text = variant.functionalText.ifBlank { "No rules text." },
            glossary = glossary,
            onKeyword = onKeyword,
            color = onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

// Renders card rules text, turning any Comprehensive Rules keyword into a
// tappable, underlined link. Falls back to plain text while the glossary is
// still loading or when the text contains no keywords.
@Composable
private fun KeywordText(
    text: String,
    glossary: RulesGlossary?,
    onKeyword: (String) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val spans = remember(text, glossary) {
        glossary?.let { findKeywordSpans(text, it.linkableKeywords) } ?: emptyList()
    }
    val body: AnnotatedString = if (spans.isEmpty()) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            var index = 0
            for (span in spans) {
                append(text.substring(index, span.start))
                val link = LinkAnnotation.Clickable(
                    tag = span.keyword,
                    styles = TextLinkStyles(
                        SpanStyle(color = LINK_COLOR, textDecoration = TextDecoration.Underline),
                    ),
                ) { onKeyword(span.keyword) }
                withLink(link) { append(text.substring(span.start, span.end)) }
                index = span.end
            }
            append(text.substring(index))
        }
    }
    Text(
        text = body,
        color = color,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        modifier = modifier,
    )
}

// Only the stats the variant actually has (many cards lack power/defense).
private fun statLine(variant: CardVariant): String? {
    val parts = buildList {
        if (variant.pitch.isNotBlank()) add("Pitch ${variant.pitch}")
        if (variant.cost.isNotBlank()) add("Cost ${variant.cost}")
        if (variant.power.isNotBlank()) add("${variant.power} power")
        if (variant.defense.isNotBlank()) add("${variant.defense} def")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun LegalityRow(summary: LegalitySummary, modifier: Modifier = Modifier) {
    if (summary.legalFormats.isEmpty() && summary.bannedFormats.isEmpty()) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (summary.legalFormats.isNotEmpty()) {
            Text(
                text = "Legal: ${summary.legalFormats.joinToString(" · ")}",
                color = LEGAL_GREEN,
                fontSize = 15.sp,
            )
        }
        if (summary.bannedFormats.isNotEmpty()) {
            Text(
                text = "Banned: ${summary.bannedFormats.joinToString(" · ")}",
                color = BANNED_RED,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun CardColor.pipColor(): Color = when (this) {
    CardColor.RED -> Color(0xFFD5473B)
    CardColor.YELLOW -> Color(0xFFE0B93B)
    CardColor.BLUE -> Color(0xFF3B72D5)
    CardColor.NONE -> Color(0xFF888888)
}

private val LEGAL_GREEN = Color(0xFF6FCF97)
private val BANNED_RED = Color(0xFFEB5757)
private val LINK_COLOR = Color(0xFF6FA8FF)

// FAB card face is ~450×628px.
private const val CARD_ASPECT_RATIO = 0.717f
