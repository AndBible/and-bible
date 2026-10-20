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

package net.bible.sharedui.ios.reading

import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedui.nav.NavResultChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chooser answers reach the window that asked ([IosReadingResults]); the target stands in for that window's page manager. */
class IosReadingResultsTest {
    private class Target(val name: String, val calls: MutableList<String>) : ChooserTarget {
        override fun applyDocument(bookInitials: String?) { calls += "$name:document:$bookInitials" }
        override fun applyVerse(osisId: String) { calls += "$name:verse:$osisId" }
        override fun applyKey(result: KeyChooserResult) { calls += "$name:key:${result.key}/${result.book}" }
        override fun openMyDocumentPage(documentInitials: String, pageKey: String?) { calls += "$name:page:$documentInitials/$pageKey" }
        override fun applyBookmark(result: BookmarkResult) { calls += "$name:bookmark:${result.verse ?: result.key}" }
        override fun applyProgress(result: ReadingProgressResult) { calls += "$name:progress:$result" }
        override fun cancelled() { calls += "$name:cancelled" }
    }

    private val calls = mutableListOf<String>()
    private val logs = mutableListOf<String>()
    private var active: Any? = "w1"
    private val targets = mapOf<Any?, ChooserTarget>("w1" to Target("w1", calls), "w2" to Target("w2", calls))

    private val keyChooser = NavResultChannel<KeyChooserResult> { }
    private val passage = NavResultChannel<PassageResult> { }
    private val document = NavResultChannel<DocumentResult> { }
    private val pages = NavResultChannel<MyDocumentPagesResult> { }
    private val myDocuments = NavResultChannel<MyDocumentsResult> { }
    private val bookmark = NavResultChannel<BookmarkResult> { }
    private val progress = NavResultChannel<ReadingProgressResult> { }
    private val workspace = NavResultChannel<net.bible.sharedcore.nav.WorkspaceResult> { }

    private val results = IosReadingResults(
        keyChooser, passage, document, pages, myDocuments, bookmark, progress, workspace,
        targetFor = { targets[it] },
        activeAskerProvider = { active },
        log = { logs += it },
    )

    @Test fun resultGoesToTheAskingWindowAlsoWhenAnotherBecameActive() {
        results.asked(ChooserKind.Passage, "w2")
        active = "w1" // the user switched windows while the chooser was open
        results.onPassage(PassageResult("Gen.1.1"))
        assertEquals(listOf("w2:verse:Gen.1.1"), calls)
    }

    @Test fun aRequestWithoutAnAskerBelongsToTheActiveWindowAtRequestTime() {
        results.asked(ChooserKind.Document, null)
        active = "w2"
        results.onDocument(DocumentResult("KJV"))
        assertEquals(listOf("w1:document:KJV"), calls)
    }

    @Test fun eachKindAppliesItsOwnAnswer() {
        results.asked(ChooserKind.KeyChooser, "w1")
        results.onKeyChooser(KeyChooserResult(key = "Gen.2", book = "Dict"))
        results.asked(ChooserKind.MyDocumentPages, "w2")
        results.onMyDocumentPages(MyDocumentPagesResult.Selected("Notes", "p1"))
        assertEquals(listOf("w1:key:Gen.2/Dict", "w2:page:Notes/p1"), calls)
    }

    @Test fun anAnswerNobodyAskedForIsDroppedAndLogged() {
        results.onPassage(PassageResult("Gen.1.1"))
        assertEquals(emptyList(), calls)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("Passage"))
    }

    @Test fun anAnswerIsSpentOnce() {
        results.asked(ChooserKind.Document, "w1")
        results.onDocument(DocumentResult("KJV"))
        results.onDocument(DocumentResult("ESV"))
        assertEquals(listOf("w1:document:KJV"), calls)
        assertEquals(1, logs.size)
    }

    @Test fun anAnswerForAWindowThatIsGoneIsDroppedAndLogged() {
        results.asked(ChooserKind.Passage, "closed")
        results.onPassage(PassageResult("Gen.1.1"))
        assertEquals(emptyList(), calls)
        assertTrue(logs.single().contains("gone"))
    }

    @Test fun theMyDocumentsListAnswersThePagesEditorsSwitchFlow() {
        results.asked(ChooserKind.MyDocumentPages, "w2")
        results.onMyDocuments(MyDocumentsResult.Selected("Notes", "p2"))
        assertEquals(listOf("w2:page:Notes/p2"), calls)
        assertEquals(false, results.isAwaiting(ChooserKind.MyDocumentPages), "the pages request was spent too")
    }

    @Test fun cancelledAndSavedPagesAnswersApplyNothingOrCancel() {
        results.asked(ChooserKind.MyDocumentPages, "w1")
        results.onMyDocumentPages(MyDocumentPagesResult.Saved)
        results.asked(ChooserKind.MyDocumentPages, "w1")
        results.onMyDocumentPages(MyDocumentPagesResult.Cancelled)
        assertEquals(listOf("w1:cancelled"), calls)
    }

    // --- leaving a chooser without answering

    @Test fun aChooserLeftWithoutAnAnswerRunsTheAskersCancelArm() {
        results.asked(ChooserKind.KeyChooser, "w2")
        results.readingReached()
        assertEquals(listOf("w2:cancelled"), calls)
        assertEquals(false, results.isAwaiting(ChooserKind.KeyChooser))
        results.readingReached()
        assertEquals(1, calls.size, "closed once")
    }

    @Test fun anAnswerPublishedBeforeThePopIsNotAbandonment() {
        results.asked(ChooserKind.Passage, "w2")
        passage.publishForTest(PassageResult("Gen.1.1"))
        results.readingReached()
        assertEquals(emptyList(), calls)
        assertTrue(results.isAwaiting(ChooserKind.Passage), "still waiting for the collector to apply it")
    }

    @Test fun aMyDocumentsAnswerKeepsThePagesRequestOpenToo() {
        results.asked(ChooserKind.MyDocumentPages, "w1")
        myDocuments.publishForTest(MyDocumentsResult.Selected("Notes", "p"))
        results.readingReached()
        assertEquals(emptyList(), calls)
    }

    @Test fun oneCollectorPerChannel() {
        assertEquals(7, results.collectors().size)
    }

    private fun bookmarkOf(verse: String) = BookmarkResult(verse = verse, description = "d", labelNo = 0, listPosition = 0)

    @Test fun aBookmarkAnswerGoesToTheAskingWindowAlsoWhenAnotherBecameActive() {
        results.asked(ChooserKind.Bookmarks, "w2")
        active = "w1"
        results.onBookmark(bookmarkOf("Gen.1.1"))
        assertEquals(listOf("w2:bookmark:Gen.1.1"), calls)
    }

    @Test fun aProgressAnswerGoesToTheAskingWindowAndMemorizeToo() {
        results.asked(ChooserKind.ReadingProgress, "w2")
        active = "w1"
        results.onReadingProgress(ReadingProgressResult.Chapter("Gen", 3))
        results.asked(ChooserKind.ReadingProgress, null)
        results.onReadingProgress(ReadingProgressResult.Memorize(1, 2))
        assertEquals(
            listOf("w2:progress:${ReadingProgressResult.Chapter("Gen", 3)}", "w1:progress:${ReadingProgressResult.Memorize(1, 2)}"),
            calls,
        )
    }

    @Test fun bookmarkAndProgressAnswersNobodyAskedForAreDroppedAndLogged() {
        results.onBookmark(bookmarkOf("Gen.1.1"))
        results.onReadingProgress(ReadingProgressResult.Chapter("Gen", 1))
        assertEquals(emptyList(), calls)
        assertEquals(2, logs.size)
    }

    /** Android has no cancel arm for these two screens: leaving them closes the request and applies nothing. */
    @Test fun leavingTheBookmarkListOrProgressScreenWithoutAnAnswerApplysNothingAndClosesTheRequest() {
        results.asked(ChooserKind.Bookmarks, "w1")
        results.asked(ChooserKind.ReadingProgress, "w2")
        results.readingReached()
        assertEquals(emptyList(), calls)
        assertEquals(false, results.isAwaiting(ChooserKind.Bookmarks))
        assertEquals(false, results.isAwaiting(ChooserKind.ReadingProgress))
    }

    @Test fun aBookmarkAnswerPublishedBeforeThePopIsNotAbandonment() {
        results.asked(ChooserKind.Bookmarks, "w1")
        bookmark.publishForTest(bookmarkOf("Gen.1.1"))
        results.readingReached()
        assertTrue(results.isAwaiting(ChooserKind.Bookmarks))
    }

    // --- the full workspace selector (Android's Workspace collector)

    @Test fun aWorkspaceAnswerIsAppliedOnceThroughTheHostAndNeedsNoWindow() {
        val applied = mutableListOf<net.bible.sharedcore.nav.WorkspaceResult>()
        results.applyWorkspace = { applied += it }
        results.asked(ChooserKind.Workspace, null)
        results.onWorkspace(net.bible.sharedcore.nav.WorkspaceResult("ws2", changed = false))
        results.onWorkspace(net.bible.sharedcore.nav.WorkspaceResult("ws3", changed = false))
        assertEquals(listOf(net.bible.sharedcore.nav.WorkspaceResult("ws2", changed = false)), applied, "spent once")
        assertEquals(1, logs.size, "the second answer was dropped and logged")
    }

    @Test fun leavingTheWorkspaceSelectorAppliesNothingAndClosesTheRequest() {
        results.asked(ChooserKind.Workspace, null)
        results.readingReached()
        assertEquals(emptyList(), calls)
        assertEquals(false, results.isAwaiting(ChooserKind.Workspace))
    }
}
