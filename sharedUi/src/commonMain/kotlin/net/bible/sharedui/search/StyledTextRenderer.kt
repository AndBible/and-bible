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
 * `bold`/`highlight` flags to span styles (highlighted runs use the theme primary colour).
 * Shared by the search-result screens (later Batch 5 tasks depend on this exact name).
 */
@Composable
fun styledTextToAnnotatedString(text: StyledText): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        text.runs.forEach { run ->
            val style = SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                color = if (run.highlight) highlightColor else Color.Unspecified,
            )
            withStyle(style) { append(run.text) }
        }
    }
}
