package net.bible.sharedui.readingplan

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.readingplan.DayEntry
import net.bible.sharedcore.readingplan.ReadingPlanError
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings
import androidx.compose.foundation.lazy.rememberLazyListState
import net.bible.sharedui.components.volumeScrollTarget

@Composable
fun DailyReadingListScreen(
    title: String,
    days: List<DayEntry>,
    error: ReadingPlanError?,
    onSelect: (Int) -> Unit,
    onDismissError: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).volumeScrollTarget(listState)) {
            items(days, key = { it.day }) { day ->
                TwoLineListItem(title = day.primary, subtitle = day.secondary, onClick = { onSelect(day.day) })
            }
        }
    }
    if (error != null) {
        AbErrorDialog(message = strings.errorOccurred, confirmText = strings.okay, onDismiss = onDismissError)
    }
}
