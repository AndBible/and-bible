package net.bible.sharedui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * Prompt shown when a search index must be built (or rebuilt) for the current document.
 * Offers cancel + create/rebuild; [isRebuild] selects the prompt wording.
 */
@Composable
fun SearchIndexScreen(
    title: String,
    isRebuild: Boolean,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(if (isRebuild) strings.indexRebuildRequired else strings.indexCreationRequired)
            Spacer(Modifier.height(16.dp))
            Row {
                TextButton(onClick = onCancel) { Text(strings.cancel) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCreate) { Text(strings.create) }
            }
        }
    }
}
