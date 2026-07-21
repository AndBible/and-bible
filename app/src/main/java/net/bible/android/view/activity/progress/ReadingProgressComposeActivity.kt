/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.progress

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressController
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.progress.AbReadHistoryDialog
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.progress.ReadingProgressScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

private const val PREF_LAST_TAB = "reading_progress_last_tab"

/** One-shot read-history dialog request, held as Compose state until dismissed. */
private data class HistoryReq(val title: String, val rows: List<ReadHistoryRow>)

/**
 * Compose host for the reading-progress screen — the new-path twin of classic
 * [ReadingProgressActivity]. Wires the shared [ReadingProgressController] to
 * [ReadingProgressServiceImpl] (`by inject()` resolves the CONCRETE type, since
 * [ReadingProgressServiceImpl.osisIdForChapter] plus the read-history/formatting helpers used here
 * go beyond the portable [net.bible.sharedcore.progress.ReadingProgressService] seam).
 *
 * **Result-className parity.** [navigateToChapter] builds the same `verse` extra classic
 * `ReadingProgressActivity.navigateToChapter` does, but targets it at the CLASSIC
 * `GridChoosePassageBook::class.java`, not this activity, so `MainBibleActivity`'s className
 * dispatch matches regardless of which host produced the result.
 *
 * (Plan 8b wires the Memorize tab into this host; for now `ReadingProgressScreen`'s
 * `memorizeTabContent` seam uses its stub default.)
 */
class ReadingProgressComposeActivity : ActivityBase() {
    private val service: ReadingProgressServiceImpl by inject()

    private val initialTab: ReadingTab by lazy {
        val tab = intent.getIntExtra(
            ReadingProgressActivity.EXTRA_TAB,
            CommonUtils.settings.getInt(PREF_LAST_TAB, 0),
        )
        if (tab == 1) ReadingTab.MEMORIZE else ReadingTab.READING
    }

    private var historyDialog by mutableStateOf<HistoryReq?>(null)

    private val controller: ReadingProgressController by lazy {
        ReadingProgressController(
            service = service,
            scope = lifecycleScope,
            initialTab = initialTab,
            onNavigateToChapter = ::navigateToChapter,
            onShowDayHistory = ::showDayHistory,
            onShowBookHistory = ::showBookHistory,
            onShowChapterHistory = ::showChapterHistory,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val model by controller.model.collectAsState()
                    val loading by controller.loading.collectAsState()

                    ReadingProgressScreen(
                        model = model,
                        loading = loading,
                        onUp = { finish() },
                        onSelectTab = { controller.selectTab(it); persistTab(it) },
                        onPrevCycle = controller::prevCycle,
                        onNextCycle = controller::nextCycle,
                        onNewCycle = controller::newCycle,
                        onBookClick = controller::openChapterDetail,
                        onBookLongClick = controller::bookLongPress,
                        onChapterClick = { chapter -> model.chapterDetail?.let { controller.chapterTap(it.bookId, chapter) } },
                        onChapterLongClick = { ch -> model.chapterDetail?.let { controller.chapterLongPress(it.bookId, ch) } },
                        onCalendarDayClick = controller::calendarDayTap,
                        onOpenSettings = ::openClassicSettings,
                        onShowHelp = ::showHelp,
                    )

                    historyDialog?.let { req ->
                        AbReadHistoryDialog(
                            title = req.title,
                            rows = req.rows,
                            onApplyDeletes = { ids ->
                                lifecycleScope.launch {
                                    service.deleteReadHistoryEntries(ids, controller.model.value.cycle)
                                    controller.refresh()
                                }
                            },
                            onDismiss = { historyDialog = null },
                        )
                    }
                }
            }
        }

        controller.load()
    }

    // --- result parity (mirrors classic ReadingProgressActivity.navigateToChapter) ---

    private fun navigateToChapter(bookId: String, chapter: Int) {
        val resultIntent = Intent(this, GridChoosePassageBook::class.java)
            .putExtra("verse", service.osisIdForChapter(bookId, chapter))
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // --- read history (mirrors classic ReadHistoryDialog.show's primary/secondary formatting) ---

    private fun showDayHistory(dayTimestamp: Long) = lifecycleScope.launch {
        val entries = service.readHistoryForDay(dayTimestamp, controller.model.value.cycle)
        historyDialog = HistoryReq(
            title = getString(R.string.reading_progress_history_for, service.dayTitle(dayTimestamp)),
            rows = entries.map {
                val date = service.formatEntryDate(it.readAt)
                val time = service.formatEntryTime(it.readAt)
                val version = it.bookInitials.ifEmpty { getString(R.string.reading_progress_history_version_unknown) }
                ReadHistoryRow(id = it.id, primary = "$date $time", secondary = version)
            },
        )
    }

    private fun showBookHistory(bookId: String) = lifecycleScope.launch {
        val entries = service.readHistoryForBook(bookId, controller.model.value.cycle)
        historyDialog = HistoryReq(
            title = getString(R.string.reading_progress_history_for, service.bookLongName(bookId)),
            rows = entries.map { it.toRow() },
        )
    }

    private fun showChapterHistory(bookId: String, chapter: Int) = lifecycleScope.launch {
        val entries = service.readHistoryForChapter(bookId, chapter, controller.model.value.cycle)
        historyDialog = HistoryReq(
            title = getString(R.string.reading_progress_history_for, "${service.bookShortName(bookId)} $chapter"),
            rows = entries.map { it.toRow() },
        )
    }

    private fun ReadHistoryEntry.toRow(): ReadHistoryRow {
        val chapterRef = "${service.bookShortName(bookId)} $chapter"
        val time = service.formatEntryTime(readAt)
        val date = service.formatEntryDate(readAt)
        val version = bookInitials.ifEmpty { getString(R.string.reading_progress_history_version_unknown) }
        return ReadHistoryRow(id = id, primary = "$chapterRef · $time", secondary = "$date · $version")
    }

    // --- overflow (mirrors classic ReadingProgressActivity.onOptionsItemSelected) ---

    private fun openClassicSettings() {
        startActivity(Intent(this, ReadingProgressSettingsActivity::class.java))
    }

    private fun showHelp() {
        CommonUtils.showHelpDialog(
            activity = this,
            titleResId = R.string.help,
            messageResId = R.string.help_reading_progress_text,
            helpPath = "reading_progress.html",
        )
    }

    private fun persistTab(tab: ReadingTab) {
        CommonUtils.settings.setInt(PREF_LAST_TAB, if (tab == ReadingTab.MEMORIZE) 1 else 0)
    }
}
