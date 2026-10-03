package net.bible.sharedui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import net.bible.sharedcore.search.StyledText

/**
 * Renders a host-built [StyledText] into a Compose [AnnotatedString], mapping each run's
 * `bold`/`highlight` flags to span styles. Highlighted runs are forced bold and get the theme
 * primary colour plus a primaryContainer background pill, so the match reads as a filled,
 * bold pill and stays legible in monochrome/e-ink themes (which map primaryContainer to grey).
 * Shared by the search-result screens (later Batch 5 tasks depend on this exact name).
 */
@Composable
fun styledTextToAnnotatedString(text: StyledText): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    return buildAnnotatedString {
        text.runs.forEach { run ->
            val style = SpanStyle(
                // A highlighted term is always emphasised bold, even if the source run wasn't bold.
                fontWeight = if (run.bold || run.highlight) FontWeight.Bold else null,
                color = if (run.highlight) highlightColor else Color.Unspecified,
                background = if (run.highlight) highlightBackground else Color.Unspecified,
            )
            withStyle(style) { append(run.text) }
        }
    }
}
