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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.host.reading

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentDictionaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.CurrentPage
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.sword.mydocument.myDocumentId
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.host.ReadingHostState
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.index.IndexStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The route Android's `searchIntent` starts for [document], or null when there is nothing to open (a general book
 * without an index). A pure function of the document's facts so it is testable without a Sword module.
 */
internal fun searchRoute(indexDone: Boolean, isEpub: Boolean, isGeneralBook: Boolean, initials: String?): String? = when {
    indexDone -> if (isEpub) NavRoutes.EPUB_SEARCH else NavRoutes.searchForm()
    isGeneralBook -> null
    else -> NavRoutes.searchIndex(searchDocument = initials)
}

/**
 * iOS [ReadingNavigator]: each member navigates the single NavHost to the route Android's `AndroidReadingNavigator`
 * starts (same `NavRoutes` builders and arguments), through [shell]. Launches are posted to [mainContext] because
 * bridge calls arrive on a background thread and navigation must run on the main thread.
 *
 * Result-bearing choosers: [assignLabels] suspends until the label manager delivers its answer to [labelResults]
 * (or is left without one, which is a cancel). The suspension belongs to the asking caller, so a result is always
 * returned to the window that asked, whichever window is active when the manager closes; requests are serialised
 * because they share the one channel. The document, key and passage choosers' answers are applied by the reading
 * destination's result collectors ([HostReadingResults]); this class only records, through [chooserRequests], which
 * window asked.
 *
 * @param hostState the reading host's sheets (kept for the sheets the navigator will open instead of routing)
 * @param openReadingSearch where [openSearch] goes first (in-place search); see the constructor
 * @param links where [openExternalUrl] and [openWebLink] go
 * @param labelResults the app's `manageLabels` result channel
 * @param workspaceSettings seeds and receives the label chooser's workspace-wide settings
 */
@kotlin.native.HiddenFromObjC
class HostReadingNavigator(
    private val shell: HostNavShell,
    @Suppress("unused") private val hostState: ReadingHostState,
    private val links: ExternalLinks,
    private val labelResults: NavResultChannel<ManageLabelsResult>,
    private val downloadControl: () -> DownloadControl,
    private val fakeBooks: () -> FakeBookFactory,
    private val workspaceSettings: () -> WorkspaceEntities.WorkspaceSettings? = { null },
    private val mainContext: CoroutineContext = Dispatchers.Main,
    /** Told which window opened a chooser, so its answer is applied to that window ([HostReadingResults]). */
    private val chooserRequests: ChooserRequests = ChooserRequests.None,
    /**
     * Opens the reading view's own search (the toolbar's search field and results sheet), as Android's `composeSearch`
     * does through the hosted `ComposeReadingViewHost.openSearch`. Null (a navigator without a reading host) falls back
     * to the search routes of [searchRoute].
     */
    private val openReadingSearch: (() -> Unit)? = null,
) : ReadingNavigator {
    private val labelRequests = Mutex()

    private fun onMain(block: () -> Unit) {
        shell.hostScope.launch(mainContext) { block() }
    }

    override fun openDownloads(addons: Boolean) = onMain {
        if (!downloadControl().checkDownloadOkay()) return@onMain
        shell.navigate(NavRoutes.download(addons = addons))
    }

    override fun openDownloadSearch(initials: String?) = onMain { shell.navigate(NavRoutes.download(search = initials)) }

    override fun openReadingProgress(tab: Int) {
        // Its chapter / memorize answer opens in the window that is active now.
        val asker = chooserRequests.activeAsker()
        onMain {
            chooserRequests.asked(ChooserKind.ReadingProgress, asker)
            shell.navigate(NavRoutes.readingProgress(tab))
        }
    }

    override fun openReadingProgressSettings() = onMain { shell.navigate(NavRoutes.READING_PROGRESS_SETTINGS) }

    override fun openWorkspaceSelector() {
        // Its save / pick answer applies to the reading view as a whole (Android's Workspace collector).
        onMain {
            chooserRequests.asked(ChooserKind.Workspace, null)
            shell.navigate(NavRoutes.WORKSPACE_SELECTOR)
        }
    }

    override fun openChooseDocument(type: String?) {
        // The asker is the window that is active NOW, read before the screen is posted: the chooser outlives that.
        val asker = chooserRequests.activeAsker()
        onMain {
            chooserRequests.asked(ChooserKind.Document, asker)
            shell.navigate(NavRoutes.chooseDocument(type))
        }
    }

    override fun openSearch(document: Book?) = onMain {
        openReadingSearch?.let { inPlace ->
            inPlace()
            return@onMain
        }
        val route = searchRoute(
            indexDone = document?.indexStatus == IndexStatus.DONE,
            isEpub = document?.isEpub == true,
            isGeneralBook = document?.bookCategory == BookCategory.GENERAL_BOOK,
            initials = document?.initials,
        )
        if (route != null) shell.navigate(route)
    }

    override fun openPromptEditor(promptId: String) = onMain { shell.navigate(NavRoutes.promptEdit(promptId = promptId)) }

    // TODO(Stream F): Android passes forceAsk = true here; ExternalLinks has no force-ask yet, add it with the real IosExternalLinks.
    override fun openExternalUrl(url: String) = links.open(url)

    override fun openWebLink(url: String) = links.open(url)

    override fun startKeyChooser(page: CurrentPage) {
        when (page) {
            is CurrentBiblePage, is CurrentCommentaryPage -> onMain {
                chooserRequests.asked(ChooserKind.Passage, page.pageManager)
                shell.navigate(NavRoutes.gridChoosePassage(isScripture = true))
            }
            is CurrentDictionaryPage -> onMain {
                chooserRequests.asked(ChooserKind.KeyChooser, page.pageManager)
                shell.navigate(NavRoutes.CHOOSE_DICTIONARY_WORD)
            }
            is CurrentMapPage -> onMain {
                chooserRequests.asked(ChooserKind.KeyChooser, page.pageManager)
                shell.navigate(NavRoutes.CHOOSE_MAP_KEY)
            }
            is CurrentGeneralBookPage -> startGeneralBookChooser(page)
        }
    }

    /** Android's `startGeneralBookChooser`: the StudyPad journal opens the label manager, the rest a key chooser. */
    private fun startGeneralBookChooser(page: CurrentGeneralBookPage) {
        shell.hostScope.launch(mainContext) {
            val doc = page.currentDocument
            val factory = fakeBooks()
            when {
                doc == factory.journalDocument -> {
                    val settings = page.pageManager.window.windowRepository.workspaceSettings
                    val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.STUDYPAD)
                        .applyFrom(settings)
                        .toJSON()
                    val result = awaitManageLabels(data) ?: return@launch
                    settings.updateFrom(ManageLabelsContract.ManageLabelsData.fromJSON(result.data))
                }
                doc == factory.multiDocument -> {
                    chooserRequests.asked(ChooserKind.Document, page.pageManager)
                    shell.navigate(NavRoutes.chooseDocument())
                }
                doc?.isMyDocument == true -> doc.myDocumentId?.let { docId ->
                    chooserRequests.asked(ChooserKind.MyDocumentPages, page.pageManager)
                    shell.navigate(NavRoutes.myDocumentPages(docId.toString(), doc.initials, doc.name))
                }
                else -> {
                    chooserRequests.asked(ChooserKind.KeyChooser, page.pageManager)
                    shell.navigate(NavRoutes.CHOOSE_GENERAL_BOOK_KEY)
                }
            }
        }
    }

    /**
     * Opens the label manager with [data] and waits for its answer: the delivered [ManageLabelsResult], or null when
     * the manager was left without delivering one, or could not be opened. A result is published before the manager
     * pops, so a delivered answer always wins over the pop.
     *
     * The wait ends on: a pending result; or any route change after the navigation was issued that leaves the app
     * back at reading (this also covers a manager that opened and closed between two collector emissions, which is
     * why it counts route changes instead of waiting to observe the manager's own route). A `navigate` that throws
     * returns null. The request lock is released on every exit ([withLock]), also when the caller is cancelled. A
     * navigation that is silently dropped gives no signal at all and waits for the caller's cancellation.
     */
    private suspend fun awaitManageLabels(data: String): ManageLabelsResult? = labelRequests.withLock {
        labelResults.consume() // a stale answer must not satisfy this request
        val versionAtNavigate = shell.routeVersion.value
        try {
            withContext(mainContext) { shell.navigate(NavRoutes.manageLabels(data)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            nsLog("IosReadingNavigator: opening the label manager failed: ${e.stackTraceToString()}")
            return@withLock null
        }
        combine(labelResults.pending, shell.currentRoute, shell.routeVersion) { pending, route, version ->
            pending != null || (version > versionAtNavigate && route?.substringBefore('?') == NavRoutes.READING)
        }.first { it }
        labelResults.consume()
    }

    override suspend fun assignLabels(
        bookmark: BookmarkEntities.BaseBookmarkWithNotes,
        labelIds: List<IdType>,
    ): List<IdType>? {
        val data = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.ASSIGN,
            selectedLabels = labelIds.toMutableSet(),
            bookmarkPrimaryLabel = bookmark.primaryLabelId,
        ).applyFrom(workspaceSettings()).toJSON()
        val result = awaitManageLabels(data) ?: return null
        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data)
        bookmark.primaryLabelId = resultData.bookmarkPrimaryLabel
        workspaceSettings()?.updateFrom(resultData)
        return resultData.selectedLabels.toList()
    }

    /**
     * The drawer's StudyPads row (Android's `studyPadsButton`): the label manager in `STUDYPAD` mode seeded from the
     * workspace settings; the manager opens the chosen StudyPad in the active window itself, and an answer is applied to
     * the workspace settings with [updateFrom]. A cancel (the manager left without an answer) and missing workspace
     * settings change nothing. Returns whether an answer was applied.
     */
    suspend fun openStudyPads(): Boolean {
        val settings = workspaceSettings() ?: return false
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.STUDYPAD)
            .applyFrom(settings).toJSON()
        val result = awaitManageLabels(data) ?: return false
        settings.updateFrom(ManageLabelsContract.ManageLabelsData.fromJSON(result.data))
        return true
    }

    /**
     * The overflow menu's "Auto assign labels" (Android's `AutoAssignPreference.openDialog`): the label manager in
     * `WORKSPACE` mode seeded from the workspace settings. A reset answer clears the auto-assign labels and the primary
     * one; any other answer is applied with [updateFrom]. Returns whether the settings changed: false for a cancel (the
     * manager left without an answer) and when there are no workspace settings to edit.
     */
    suspend fun editAutoAssignLabels(): Boolean {
        val settings = workspaceSettings() ?: return false
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.WORKSPACE)
            .applyFrom(settings).toJSON()
        val result = awaitManageLabels(data) ?: return false
        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data)
        if (resultData.reset) {
            settings.autoAssignLabels = mutableSetOf()
            settings.autoAssignPrimaryLabel = null
        } else {
            settings.updateFrom(resultData)
        }
        return true
    }
}
