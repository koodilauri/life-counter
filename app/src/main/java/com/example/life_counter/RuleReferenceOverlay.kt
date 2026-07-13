package com.example.life_counter

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen Comprehensive Rules reader. Scroll to read, jump via the table of
 * contents, and search smartly — a rule reference ("8.3.5") or a keyword ("go
 * again") jumps straight there, anything else full-text filters with matches
 * highlighted. Cross-references like [1.3.2a] in the text are tappable.
 *
 * [initialReference], when set, opens scrolled to that rule — used when the card
 * search's "Go to rule" hands off to this view.
 */
@Composable
fun RuleReferenceOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialReference: String? = null,
) {
    // Portrait-only, like the card search: long rules text reads badly sideways.
    ForcePortrait()

    val context = LocalContext.current
    val content by produceState<CrContent?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            CrContent(CrProvider.document(context), CrProvider.glossary(context))
        }
        val updated = withContext(Dispatchers.IO) { CrProvider.refreshOnce(context) }
        if (updated) {
            value = withContext(Dispatchers.IO) {
                CrContent(CrProvider.document(context), CrProvider.glossary(context))
            }
        }
    }

    var query by remember { mutableStateOf(initialReference ?: "") }
    var showContents by remember { mutableStateOf(false) }
    // Which cross-reference is being peeked in a popup, if any. Peeking keeps the
    // reading position; only "Go to rule" actually navigates.
    var peekRef by remember { mutableStateOf<String?>(null) }
    // A one-shot scroll target (used by the table of contents, whose chapters
    // share reference numbers so can't be reached by a reference query).
    var pendingScroll by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(pendingScroll) {
        pendingScroll?.let {
            listState.scrollToItem(it)
            pendingScroll = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "COMPREHENSIVE RULES",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                )
                Row {
                    TextButton(onClick = { showContents = true }) { Text(text = "CONTENTS") }
                    TextButton(onClick = onClose) { Text(text = "CLOSE") }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(text = "Search rules, or jump to e.g. 8.3.5") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            val loaded = content
            if (loaded == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                return@Column
            }

            val result = remember(query, loaded) { loaded.document.search(query, loaded.glossary) }

            LaunchedEffect(result) {
                if (result is CrSearchResult.Jump) listState.scrollToItem(result.index)
            }

            when (result) {
                is CrSearchResult.NoMatch -> Hint("No rules match “${query.trim()}”.")

                is CrSearchResult.Matches -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(result.hits) { hit ->
                        MatchRow(
                            entry = hit.entry,
                            query = query.trim(),
                            onClick = { loaded.document.nearestReference(hit.index)?.let { query = it } },
                        )
                    }
                }

                // Browse and Jump both show the whole document; Jump also highlights.
                else -> {
                    val highlight = (result as? CrSearchResult.Jump)?.reference
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(loaded.document.entries) { _, entry ->
                            EntryRow(
                                entry = entry,
                                highlighted = entry.reference == highlight,
                                onPeek = { peekRef = it },
                            )
                        }
                    }
                }
            }
        }

        // Contents panel, over the reader. Selecting an item clears any search
        // (so the full document is shown) and scrolls to it.
        content?.let { loaded ->
            if (showContents) {
                ContentsPanel(
                    contents = loaded.document.contents,
                    onSelect = { index ->
                        query = ""
                        pendingScroll = index
                        showContents = false
                    },
                    onClose = { showContents = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Peek popup for a tapped cross-reference. Refs inside it re-peek;
            // "Go to rule" navigates the document and closes the popup.
            peekRef?.let { reference ->
                RulePeekDialog(
                    document = loaded.document,
                    reference = reference,
                    onPeek = { peekRef = it },
                    onGoTo = {
                        query = reference
                        peekRef = null
                    },
                    onDismiss = { peekRef = null },
                )
            }
        }
    }
}

/** Forces the hosting Activity to portrait while composed, restoring on dispose. */
@Composable
fun ForcePortrait() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
private fun ContentsPanel(
    contents: List<CrHit>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "CONTENTS",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
            )
            TextButton(onClick = onClose) { Text(text = "CLOSE") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(contents) { item ->
                val isChapter = item.entry.level == CrLevel.CHAPTER
                Text(
                    text = if (isChapter) {
                        item.entry.text
                    } else {
                        "${item.entry.reference}  ${item.entry.text}"
                    },
                    color = if (isChapter) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontSize = if (isChapter) 18.sp else 15.sp,
                    fontWeight = if (isChapter) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item.index) }
                        .padding(
                            start = if (isChapter) 0.dp else 16.dp,
                            top = if (isChapter) 16.dp else 6.dp,
                            bottom = 6.dp,
                        ),
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

// One line of the document, styled by level. A jumped-to rule gets a highlight,
// and cross-references in its text peek in a popup.
@Composable
private fun EntryRow(entry: CrEntry, highlighted: Boolean, onPeek: (String) -> Unit) {
    val onBg = MaterialTheme.colorScheme.onBackground
    when (entry.level) {
        CrLevel.CHAPTER -> Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = onBg.copy(alpha = 0.15f),
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = entry.text,
                color = onBg,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        CrLevel.SECTION -> Text(
            text = "${entry.reference}  ${entry.text}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 4.dp),
        )

        CrLevel.RULE -> {
            // Lettered sub-rules (1.0.2a) are nested under their parent rule.
            val isSubRule = entry.reference?.lastOrNull()?.isLetter() == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (isSubRule) 20.dp else 0.dp, top = 5.dp)
                    .highlightBackground(highlighted),
            ) {
                Text(
                    text = entry.reference.orEmpty(),
                    color = onBg.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(CR_REFERENCE_GUTTER),
                )
                CrText(text = entry.text, color = onBg, onReference = onPeek)
            }
        }

        CrLevel.TEXT -> CrText(
            text = entry.text,
            color = onBg.copy(alpha = if (entry.isExample) 0.55f else 0.75f),
            onReference = onPeek,
            italic = entry.isExample,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CR_REFERENCE_GUTTER, top = 3.dp),
        )
    }
}

private fun Modifier.highlightBackground(on: Boolean): Modifier =
    if (on) {
        clip(RoundedCornerShape(6.dp))
            .background(Color(0x33FFFFFF))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    } else {
        this
    }

// A search hit: its reference (if any) plus its text with the query highlighted.
@Composable
private fun MatchRow(entry: CrEntry, query: String, onClick: () -> Unit) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = entry.reference.orEmpty(),
            color = onBg.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(CR_REFERENCE_GUTTER),
        )
        Text(
            text = highlight(entry.text, query, MaterialTheme.colorScheme.primary),
            color = onBg,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
    }
}

// Bold every occurrence of [query] in [text].
private fun highlight(text: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var index = 0
        while (true) {
            val found = text.indexOf(query, index, ignoreCase = true)
            if (found < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, found))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(found, found + query.length))
            }
            index = found + query.length
        }
    }
}
