package com.example.life_counter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The two parsed CR views shared by the reader and the card-search popup. */
internal class CrContent(val document: CrDocument, val glossary: RulesGlossary)

/** Width of the monospace reference column, shared so rules line up everywhere. */
internal val CR_REFERENCE_GUTTER = 60.dp

/**
 * Peek at a referenced rule (and its sub-rules) in a popup, without leaving
 * wherever the user was. Used both for cross-references in the CR reader and for
 * keyword links in the card-search rules text. Cross-references inside re-peek
 * (via [onPeek]); [onGoTo] — when non-null — is the only thing that navigates,
 * and its meaning is up to the caller (scroll the reader, or open the reader).
 */
@Composable
internal fun RulePeekDialog(
    document: CrDocument,
    reference: String,
    onPeek: (String) -> Unit,
    onGoTo: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val block = remember(reference, document) { document.ruleBlock(reference) }
    val onBg = MaterialTheme.colorScheme.onBackground
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = reference, fontFamily = FontFamily.Monospace) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (block.isEmpty()) {
                    Text(text = "Rule $reference not found.", color = onBg.copy(alpha = 0.6f))
                } else {
                    block.forEach { entry ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(
                                text = entry.reference.orEmpty(),
                                color = onBg.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(CR_REFERENCE_GUTTER),
                            )
                            CrText(
                                text = entry.text,
                                color = onBg.copy(alpha = if (entry.isExample) 0.6f else 0.9f),
                                onReference = onPeek,
                                isKnownReference = document::hasReference,
                                italic = entry.isExample,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (block.isNotEmpty() && onGoTo != null) {
                TextButton(onClick = onGoTo) { Text(text = "GO TO RULE") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "CLOSE") }
        },
    )
}

/**
 * Rules text with each [x.y.z] cross-reference rendered as a tappable link —
 * but only when [isKnownReference] resolves it. References the published CR
 * cites but never defines (5.5, 4.6) stay as plain text, so there are no dead
 * links to tap.
 */
@Composable
internal fun CrText(
    text: String,
    color: Color,
    onReference: (String) -> Unit,
    isKnownReference: (String) -> Boolean,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildCrText(text, linkColor, onReference, isKnownReference)
    Text(
        text = annotated,
        color = color,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        modifier = modifier,
    )
}

// A cross-reference in CR text: a bare chapter/part number ("[2]") or a dotted
// rule ("[1.3.2a]"). Only refs that actually resolve are turned into links
// (see isKnownReference), so stray bracketed numbers stay plain text.
private val CROSS_REFERENCE = Regex("""\[(\d+(?:\.\d+)*[a-z]?)]""")

private fun buildCrText(
    text: String,
    linkColor: Color,
    onReference: (String) -> Unit,
    isKnownReference: (String) -> Boolean,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in CROSS_REFERENCE.findAll(text)) {
        append(text.substring(index, match.range.first))
        val reference = match.groupValues[1]
        if (isKnownReference(reference)) {
            val link = LinkAnnotation.Clickable(
                tag = reference,
                styles = TextLinkStyles(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ),
            ) { onReference(reference) }
            withLink(link) { append(match.value) }
        } else {
            // Dangling reference (missing from the published CR) — plain text.
            append(match.value)
        }
        index = match.range.last + 1
    }
    append(text.substring(index))
}
