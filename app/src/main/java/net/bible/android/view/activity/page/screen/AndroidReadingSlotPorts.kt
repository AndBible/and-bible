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
package net.bible.android.view.activity.page.screen

import android.content.Context
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedui.reading.host.slots.ReadingQuickActions
import net.bible.sharedui.reading.host.slots.ReadingSearchPlatform
import net.bible.sharedui.reading.host.slots.SearchIndexJobs
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import net.bible.android.platform.openSystemTtsSettings
import net.bible.sharedui.reading.host.slots.SpeakSettingsPlatform

/**
 * Android [SpeakSettingsPlatform]: the system TTS settings screen, or the general settings where
 * that screen is missing (the same code [net.bible.android.platform.AndroidSpeakPlatform] uses).
 * [context] is read per call because the host's activity can be recreated.
 */
class AndroidSpeakSettingsPlatform(private val context: () -> Context) : SpeakSettingsPlatform {
    override fun openSystemTtsSettings() = openSystemTtsSettings(context())
}

/**
 * Android [ReadingQuickActions]: each member is the body the matching full-screen chooser result
 * runs, through the hosting activity's `ReadingCommands`. [activity] is read per call because the
 * host's activity can be recreated.
 */
class AndroidReadingQuickActions(private val activity: () -> ReadingHostActivity) : ReadingQuickActions {
    /**
     * The quick sheet never pauses the activity, so nothing else flushes the outgoing workspace's
     * windows/page-managers/history before switching: use the save-then-switch entry point, never
     * plain `switchToWorkspace`.
     */
    override fun quickSwitchToWorkspace(id: String) {
        val host = activity()
        host.lifecycleScope.launch { host.readingCommands.quickSwitchToWorkspace(id) }
    }

    override fun applyChosenDocument(bookStr: String?) = activity().readingCommands.applyChosenDocument(bookStr)

    override fun applyChosenGenBookKey(book: Book?, key: Key) =
        activity().readingCommands.applyChosenGenBookKey(book, key)
}

/**
 * Android [ReadingSearchPlatform]: the active window's document through `DocumentControl`'s
 * current document, JSword lookups through [SwordDocumentFacade], and results shown through the
 * window and link controllers. Every collaborator is a provider because the host resolves them
 * lazily (Koin) and this object is built while the host is still being constructed.
 * [documentInfo] is `ComposeReadingViewHost`'s `searchDocumentInfo` (it needs `Book.isEpub` and the
 * EPUB backend, both `:app`-side JSword extensions).
 */
class AndroidReadingSearchPlatform(
    private val currentDocument: () -> Book?,
    private val currentBookName: () -> String,
    private val documentInfo: (Book?) -> SearchDocumentInfo?,
    private val windowControl: () -> WindowControl,
    private val linkControl: () -> LinkControl,
    private val fakeBookFactory: () -> FakeBookFactory,
) : ReadingSearchPlatform {
    override fun currentDocument(): Book? = currentDocument.invoke()

    override fun currentBookName(): String = currentBookName.invoke()

    override fun documentInfo(book: Book?): SearchDocumentInfo? = documentInfo.invoke(book)

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

/** Android [SearchIndexJobs]: JSword's global `JobManager`, exactly as the Activity path read it. */
class AndroidSearchIndexJobs : SearchIndexJobs {
    override fun subscribe(onEvent: (job: Any, isFinished: () -> Boolean) -> Unit): () -> Unit {
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) = onEvent(ev.job) { ev.job.isFinished }
            override fun workStateChanged(ev: WorkEvent) = onEvent(ev.job) { ev.job.isFinished }
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    override fun snapshot(): List<ProgressJob> {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            snapshot.add(
                ProgressJob(
                    id = System.identityHashCode(job).toString(),
                    label = job.jobName,
                    percent = job.work,
                    indeterminate = job.work == 0,
                )
            )
        }
        return snapshot
    }

    override fun isEveryJobFinished(): Boolean {
        val it = JobManager.iterator()
        while (it.hasNext()) if (!it.next().isFinished) return false
        return true
    }
}
