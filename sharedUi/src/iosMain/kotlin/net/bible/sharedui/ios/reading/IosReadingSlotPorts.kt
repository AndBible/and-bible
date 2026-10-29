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

package net.bible.sharedui.ios.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedui.reading.host.slots.ReadingKeyChooserPage
import net.bible.sharedui.reading.host.slots.ReadingQuickActions
import net.bible.sharedui.reading.host.slots.ReadingSearchPlatform
import net.bible.sharedui.reading.host.slots.SearchIndexJobs
import net.bible.sharedui.reading.host.slots.SpeakSettingsPlatform
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.Key

/**
 * iOS [SpeakSettingsPlatform]: iOS has no per-feature text-to-speech settings screen an app can open, so the Speak
 * sheet's "system TTS settings" row opens the app's page in the Settings app (`UIApplicationOpenSettingsURLString`),
 * where the user reaches Accessibility > Spoken Content from. [openUrl] is the seam tests replace.
 */
internal class IosSpeakSettingsPlatform(
    private val openUrl: (String) -> Unit,
    /** The URL of the app's page in the Settings app (UIKit's `UIApplicationOpenSettingsURLString`). */
    private val settingsUrl: String,
) : SpeakSettingsPlatform {
    override fun openSystemTtsSettings() = openUrl(settingsUrl)
}

/**
 * iOS [ReadingQuickActions]: the same calls Android's `ReadingCommands` runs, over the window repository and the
 * active window's page manager. [scope] runs the (suspending) workspace load.
 */
internal class IosReadingQuickActions(
    private val windowControl: () -> WindowControl,
    private val fakeBookFactory: () -> FakeBookFactory,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit,
    /** Android's `onToolbarStateMayHaveChanged()`, called after a workspace switch. */
    private val onToolbarMayHaveChanged: () -> Unit = {},
    /** The [ChooserTarget] of the active window: a chosen document is applied by the same code every chooser answer uses. */
    private val chooserTarget: () -> ChooserTarget,
) : ReadingQuickActions {
    /** Android's `switchMutex`: two quick switches never interleave their load/reload steps. */
    private val switchMutex = Mutex()

    /**
     * Android's quick switch: the quick sheet never pauses the host, so the outgoing workspace is saved first, then
     * the target is loaded, remembered as the current one, and every window is reloaded with its text settings.
     * Switches are serialised and finish with the workspace-name toast, like Android's `switchWorkspace`. Android also
     * clears its `BibleViewFactory`; here `loadFromDb` clears the window list and calls `notifyWindowsChanged`, and
     * `IosReadingWindows` releases the session of every window that is gone or closed on that change.
     * Android's `buildView(forceUpdate)` / title / bottom-bar refreshes have no iOS counterpart; the toolbar state is refreshed
     * at the end ([onToolbarMayHaveChanged]).
     */
    override fun quickSwitchToWorkspace(id: String) {
        scope.launch { switchToWorkspace(id) }
    }

    /** The switch itself, suspending until it has finished (the toolbar's workspace cycling waits on it). */
    suspend fun switchToWorkspace(id: String, saveOutgoing: Boolean = true) {
        switchMutex.withLock {
            val control = windowControl()
            val repository = control.windowRepository
            // The full selector pauses the host first (the outgoing workspace is already saved), and saving AFTER a
            // rename there would write the old name back: Android's `switchWorkspace` never saves.
            if (saveOutgoing) repository.saveIntoDb()
            repository.loadFromDb(IdType(id))
            settings.setString("current_workspace_id", repository.id.toString())
            control.windowSync.reloadAllWindows()
            repository.updateAllWindowsTextDisplaySettings()
            toast(repository.name)
            onToolbarMayHaveChanged()
        }
    }

    /** `KeyChooserResults.applyChosenDocument` on the active window, through the one shared [ChooserTarget.applyDocument]. */
    override fun applyChosenDocument(bookStr: String?) = chooserTarget().applyDocument(bookStr)

    override fun applyChosenGenBookKey(book: Book?, key: Key) {
        windowControl().activeWindowPageManager.setCurrentDocumentAndKey(book, key)
    }
}

/** iOS [ReadingKeyChooserPage]: the active window's map and general-book pages, read per call. */
internal class IosReadingKeyChooserPage(private val windowControl: () -> WindowControl) : ReadingKeyChooserPage {
    private val pages get() = windowControl().activeWindowPageManager

    override fun currentKey(kind: KeyChooserKind): Key? = when (kind) {
        KeyChooserKind.Map -> pages.currentMap.key
        KeyChooserKind.GeneralBook -> pages.currentGeneralBook.key
        KeyChooserKind.Grid -> null
    }

    override fun currentDocument(kind: KeyChooserKind): Book? = when (kind) {
        KeyChooserKind.Map -> pages.currentMap.currentDocument
        KeyChooserKind.GeneralBook -> pages.currentGeneralBook.currentDocument
        KeyChooserKind.Grid -> null
    }
}

/**
 * iOS [ReadingSearchPlatform]: the same lookups as Android's `AndroidReadingSearchPlatform`; every collaborator is a
 * provider because the host resolves them lazily from Koin. [documentInfo] mirrors `searchDocumentInfo`
 * (category, EPUB flag and whether the index exists; for an EPUB the FTS table of its own backend decides).
 */
internal class IosReadingSearchPlatform(
    private val currentDocument: () -> Book?,
    private val currentBookName: () -> String,
    private val windowControl: () -> WindowControl,
    private val linkControl: () -> LinkControl,
    private val fakeBookFactory: () -> FakeBookFactory,
) : ReadingSearchPlatform {
    override fun currentDocument(): Book? = currentDocument.invoke()

    override fun currentBookName(): String = currentBookName.invoke()

    override fun documentInfo(book: Book?): SearchDocumentInfo? {
        if (book == null) return null
        val category = when (book.bookCategory) {
            BookCategory.BIBLE -> SearchDocumentCategory.BIBLE
            BookCategory.COMMENTARY -> SearchDocumentCategory.COMMENTARY
            BookCategory.DICTIONARY -> SearchDocumentCategory.DICTIONARY
            else -> SearchDocumentCategory.GENERAL_BOOK
        }
        val indexDone = if (book.isEpub) book.epubBackend?.state?.isIndexed == true else book.indexStatus == IndexStatus.DONE
        return SearchDocumentInfo(docId = book.initials, category = category, isEpub = book.isEpub, indexDone = indexDone)
    }

    override fun documentByInitials(initials: String): Book? = SwordDocumentFacade.getDocumentByInitials(initials)

    override fun swordBookByInitials(initials: String): Book? =
        SwordDocumentFacade.getDocumentByInitials(initials) as? SwordBook

    override fun installedBibles(): List<Pair<String, String>> =
        SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().sortedBy { it.abbreviation }
            .map { it.initials to it.abbreviation }

    override fun tryOpenReference(query: String): Boolean = linkControl().tryToOpenRef(query)

    override fun showInActiveWindow(book: Book, key: Key) {
        windowControl().activeWindowPageManager.setCurrentDocumentAndKey(book, key)
    }

    override fun showInLinksWindow(list: BookAndKeyList) {
        linkControl().showLink(fakeBookFactory().multiDocument, list)
    }
}

/** iOS [SearchIndexJobs]: JSword's global `JobManager`, keyed by the job's own id (there is no identity hash). */
internal class IosSearchIndexJobs : SearchIndexJobs {
    override fun subscribe(onEvent: (job: Any, isFinished: () -> Boolean) -> Unit): () -> Unit {
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) = onEvent(ev.job) { ev.job.isFinished }
            override fun workStateChanged(ev: WorkEvent) = onEvent(ev.job) { ev.job.isFinished }
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    override fun snapshot(): List<ProgressJob> {
        val jobs = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            jobs.add(ProgressJob(id = job.jobID, label = job.jobName, percent = job.work, indeterminate = job.work == 0))
        }
        return jobs
    }

    override fun isEveryJobFinished(): Boolean {
        val it = JobManager.iterator()
        while (it.hasNext()) if (!it.next().isFinished) return false
        return true
    }
}
