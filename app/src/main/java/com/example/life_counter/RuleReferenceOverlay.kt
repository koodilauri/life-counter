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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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

    // Which document is on screen (CR by default, switchable in the drawer).
    var selectedDoc by remember { mutableStateOf(RulesDoc.CR) }
    var query by remember { mutableStateOf(initialReference ?: "") }
    // Which cross-reference is being peeked in a popup, if any. Peeking keeps the
    // reading position; only "Go to rule" actually navigates.
    var peekRef by remember { mutableStateOf<String?>(null) }
    // A one-shot scroll target (used by the table of contents, whose chapters
    // share reference numbers so can't be reached by a reference query).
    var pendingScroll by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // The selected document, parsed from cache/bundle (reloads on switch). A
    // one-time background refresh re-reads if anything newer was adopted.
    val document by produceState<CrDocument?>(initialValue = null, selectedDoc) {
        value = withContext(Dispatchers.IO) { CrProvider.document(context, selectedDoc) }
        val updated = withContext(Dispatchers.IO) { CrProvider.refreshOnce(context) }
        if (updated) {
            value = withContext(Dispatchers.IO) { CrProvider.document(context, selectedDoc) }
        }
    }
    // Keyword glossary is Comprehensive-Rules-only; it drives keyword jumps there.
    val glossary by produceState<RulesGlossary?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { CrProvider.glossary(context) }
    }

    LaunchedEffect(pendingScroll) {
        pendingScroll?.let {
            listState.scrollToItem(it)
            pendingScroll = null
        }
    }

    // Switching documents resets the view to the top with no active search, but
    // keeps the drawer open so its contents update in place for browsing.
    val onSelectDoc: (RulesDoc) -> Unit = { doc ->
        selectedDoc = doc
        query = ""
        peekRef = null
        pendingScroll = 0
    }

    // The table of contents lives in a slide-in drawer (opened by the hamburger)
    // so it doesn't crowd the header.
    ModalNavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    selectedDoc = selectedDoc,
                    onSelectDoc = onSelectDoc,
                    contents = document?.contents ?: emptyList(),
                    onSelectSection = { index ->
                        query = ""
                        pendingScroll = index
                        scope.launch { drawerState.close() }
                    },
                    onClose = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Contents",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            text = selectedDoc.headerLabel,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                        )
                    }
                    TextButton(onClick = onClose) { Text(text = "CLOSE") }
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

                val loadedDoc = document
                if (loadedDoc == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    return@Column
                }

                // Keyword jumps only make sense in the CR; the other docs search
                // by reference and full text only.
                val activeGlossary = if (selectedDoc == RulesDoc.CR) glossary else null
                val result = remember(query, loadedDoc, activeGlossary) {
                    loadedDoc.search(query, activeGlossary)
                }

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
                                onClick = { loadedDoc.nearestReference(hit.index)?.let { query = it } },
                            )
                        }
                    }

                    // Browse and Jump both show the whole document; Jump also highlights.
                    else -> {
                        val highlight = (result as? CrSearchResult.Jump)?.reference
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(loadedDoc.entries) { _, entry ->
                                EntryRow(
                                    entry = entry,
                                    highlighted = entry.reference == highlight,
                                    onPeek = { peekRef = it },
                                    isKnownReference = loadedDoc::hasReference,
                                )
                            }
                        }
                    }
                }
            }

            // Peek popup for a tapped cross-reference. Refs inside it re-peek;
            // "Go to rule" navigates the document and closes the popup.
            document?.let { doc ->
                peekRef?.let { reference ->
                    RulePeekDialog(
                        document = doc,
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

// Navigation drawer: pick a document at the top, then its table of contents.
// Chapters are bold, sections indented/accented; tapping a section jumps there.
@Composable
private fun DrawerContent(
    selectedDoc: RulesDoc,
    onSelectDoc: (RulesDoc) -> Unit,
    contents: List<CrHit>,
    onSelectSection: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "RULES", color = onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text(text = "CLOSE") }
        }

        // Document picker.
        RulesDoc.entries.forEach { doc ->
            val selected = doc == selectedDoc
            Text(
                text = doc.title,
                color = if (selected) MaterialTheme.colorScheme.primary else onSurface,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDoc(doc) }
                    .padding(vertical = 8.dp),
            )
        }

        HorizontalDivider(
            color = onSurface.copy(alpha = 0.15f),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            text = "CONTENTS",
            color = onSurface.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )

        if (contents.isEmpty()) {
            Text(
                text = "Loading…",
                color = onSurface.copy(alpha = 0.5f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contents) { item ->
                    val isChapter = item.entry.level == CrLevel.CHAPTER
                    Text(
                        text = if (isChapter) {
                            item.entry.text
                        } else {
                            "${item.entry.reference}  ${item.entry.text}"
                        },
                        color = if (isChapter) onSurface else MaterialTheme.colorScheme.primary,
                        fontSize = if (isChapter) 18.sp else 15.sp,
                        fontWeight = if (isChapter) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSection(item.index) }
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
private fun EntryRow(
    entry: CrEntry,
    highlighted: Boolean,
    onPeek: (String) -> Unit,
    isKnownReference: (String) -> Boolean,
) {
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
                CrText(
                    text = entry.text,
                    color = onBg,
                    onReference = onPeek,
                    isKnownReference = isKnownReference,
                )
            }
        }

        CrLevel.TEXT -> CrText(
            text = entry.text,
            color = onBg.copy(alpha = if (entry.isExample) 0.55f else 0.75f),
            onReference = onPeek,
            isKnownReference = isKnownReference,
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
