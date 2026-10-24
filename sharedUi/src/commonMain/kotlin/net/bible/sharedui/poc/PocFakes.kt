package net.bible.sharedui.poc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons

/** Fixture data for the I0 PoC app (all text here is fake content, not UI chrome). */

fun pocLayout(count: Int, active: String) = WindowLayoutState(
    windows = (1..count).map { WindowSnapshot("w$it", WindowStateValue.VISIBLE, 1f, true, false, true, 0, false) },
    activeWindowId = active,
    maximizedWindowId = null,
    reverseSplitMode = false,
    restoreButtonsVisible = false,
)

fun pocToolbar(title: String) =
    ToolbarState.EMPTY.copy(pageTitle = title, documentTitle = "KJVA", showBible = true, searchable = true)

@Composable
fun pocToolbarIcons(): ReadingToolbarIcons {
    val menu = rememberVectorPainter(Icons.Filled.Menu)
    return ReadingToolbarIcons(
        home = rememberVectorPainter(Icons.Filled.Home),
        search = rememberVectorPainter(Icons.Filled.Search),
        speak = menu,
        strongs = menu,
        bible = rememberVectorPainter(Icons.Filled.Book),
        commentary = menu,
        workspace = rememberVectorPainter(Icons.Filled.Settings),
        overflow = rememberVectorPainter(Icons.Filled.MoreVert),
        sync = rememberVectorPainter(Icons.Filled.Refresh),
    )
}

fun pocToolbarCallbacks(
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onWorkspace: () -> Unit,
    onOverflow: () -> Unit,
) = ReadingToolbarCallbacks(
    onHome = onHome, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
    onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {}, onCommentaryLong = {},
    onStrongs = {}, onStrongsLong = {}, onSearch = onSearch, onSpeak = {}, onSpeakLong = {},
    onWorkspace = onWorkspace, onOverflow = onOverflow,
)

val pocHistory: List<HistoryEntry> = listOf(
    "Ephesians 2:8", "Ephesians 2:1", "Romans 8:28", "John 3:16", "Psalm 23:1",
    "Genesis 1:1", "Isaiah 53:5", "Matthew 5:3", "Hebrews 11:1", "Philippians 4:13",
).mapIndexed { i, t -> HistoryEntry(i, t, "2026-10-0${i % 9 + 1} 12:00") }

val pocBookmarks: List<BookmarkRow> = (1..10).map { i ->
    BookmarkRow(
        id = "b$i", title = "Ephesians 2:$i", dateText = "Mon, 2026-10-0${i % 9 + 1} 12:00",
        content = StyledText.plain("Fixture bookmark text number $i"), notes = null,
        labelColors = emptyList(), isSpeak = false,
    )
}

val pocSettings = SettingsScreenState(
    title = "Settings",
    items = listOf(
        SettingsItem.Category("cat", "General"),
        SettingsItem.SwitchRow("night", "Night mode", checked = false),
        SettingsItem.SwitchRow("notes", "Show notes", checked = true),
        SettingsItem.ListChoiceRow(
            "size", "Font size",
            entries = listOf(SettingsItem.Choice("s", "Small"), SettingsItem.Choice("m", "Medium")),
            selectedValue = "m",
        ),
    ),
)

/** PoC-only fixture labels for chrome that has no existing Strings member (not user-facing app text). */
const val POC_LABEL_SPLIT = "Split"
const val POC_LABEL_HISTORY = "History"
const val POC_LABEL_SETTINGS = "Settings"
