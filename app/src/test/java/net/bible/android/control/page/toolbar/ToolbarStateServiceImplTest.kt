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
package net.bible.android.control.page.toolbar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakControl
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakPlaybackState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [ToolbarStateServiceImpl] against the REAL [WindowControl]/[DocumentControl]/
 * [PageControl]/[SpeakControl] graph (Robolectric + [TestBibleApplication], same style as
 * [net.bible.android.control.page.window.WindowStateServiceTest]) rather than mocking the
 * collaborators: `DocumentControl` and `SpeakControl` are plain (non-`open`) Kotlin classes, so
 * Mockito's default (non-inline) mock maker cannot stub them. `DocumentControl`/`PageControl`/
 * `SpeakControl` are resolved from the real Koin container (started by
 * [net.bible.android.BibleApplication.onCreate]) so they share the SAME [WindowControl] singleton
 * ([CommonUtils.windowControl]) whose `windowRepository` this test replaces with a fresh
 * in-memory one, mirroring `WindowStateServiceTest.setUp`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolbarStateServiceImplTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var pageControl: PageControl
    private lateinit var service: ToolbarStateServiceImpl

    private val kjv: org.crosswire.jsword.book.Book get() = Books.installed().getBook("KJV")!!
    private val kjvV11n get() = Versifications.instance().getVersification("KJV")
    private val gen11 get() = Verse(kjvV11n, BibleBook.GEN, 1, 1)

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository

        val koin = GlobalContext.get()
        pageControl = koin.get()
        service = ToolbarStateServiceImpl(
            windowControl = windowControl,
            documentControl = koin.get<DocumentControl>(),
            pageControl = pageControl,
            speakControl = koin.get<SpeakControl>(),
            windowStateService = koin.get(),
        )
    }

    @After
    fun tearDown() {
        PassageChangeMediator.resetSubscribersForTest()
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    /** Seeds the active window's Bible page WITHOUT posting any event (CurrentPage-level overload). */
    private fun seedActivePageSilently(book: org.crosswire.jsword.book.Book, key: Verse) {
        windowControl.activeWindowPageManager.currentBible.setCurrentDocumentAndKey(book, key)
    }

    @Test
    fun toolbarStartsEmpty() {
        assertThat(service.toolbar.value, equalTo(ToolbarState.EMPTY))
    }

    @Test
    fun contentLoaded_rebuildsSnapshotFromActivePage() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)

        PassageChangeMediator.contentChangeFinished()

        val state = service.toolbar.value
        val pageManager = windowControl.activeWindowPageManager
        assertThat(state.documentTitle, equalTo(PassageTestData.ESV.name))
        assertThat(state.pageTitle, equalTo(pageControl.currentBibleVerse.getName()))
        assertThat(state.showBible, equalTo(true)) // at least one bible (ESV/KJV) is installed
        assertThat(state.showStrongs, equalTo(pageManager.hasStrongs))
        assertThat(state.searchable, equalTo(true))
        assertThat(state.speakable, equalTo(true))
        assertThat(state.speakStopped, equalTo(true)) // nothing is speaking in the test process
    }

    @Test
    fun cloudSyncRunning_setsOnlySyncRunning() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value
        assertThat(before.syncRunning, equalTo(false))

        CloudSync.notifySyncRunning(true)
        val duringSync = service.toolbar.value
        assertThat(duringSync.syncRunning, equalTo(true))
        // every other field is untouched
        assertThat(duringSync.copy(syncRunning = false), equalTo(before))

        CloudSync.notifySyncRunning(false)
        assertThat(service.toolbar.value, equalTo(before))
    }

    @Test
    fun currentWindowChangedEvent_rebuildsFromNewlyActiveWindow() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        assertThat(service.toolbar.value.documentTitle, equalTo(PassageTestData.ESV.name))

        // 2-arg addNewWindow seeds the new (still inactive) window's page directly.
        val w2 = windowControl.addNewWindow(kjv, gen11)

        // switching active window emits WindowChange.ActiveWindowChanged (WindowRepository.notifyActiveWindowChanged)
        windowControl.activeWindow = w2

        assertThat(service.toolbar.value.documentTitle, equalTo(kjv.name))
    }

    @Test
    fun activeWindowChange_inASwappedInRepository_stillRefreshes() {
        val swapped = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = swapped
        swapped.initialize()
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val second = swapped.addNewWindow()
        second.pageManager.setCurrentDocumentAndKey(kjv, gen11)

        windowControl.activeWindow = second

        assertThat(service.toolbar.value.documentTitle, equalTo(kjv.name))
    }

    @Test
    fun verseChanged_rebuildsSnapshot() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value

        seedActivePageSilently(kjv, gen11) // silent -> no event yet, state must still be stale
        assertThat(service.toolbar.value, equalTo(before))

        PassageChangeMediator.onCurrentVerseChanged(windowControl.activeWindow)

        val after = service.toolbar.value
        assertThat(after.documentTitle, equalTo(kjv.name))
        assertThat(after.documentTitle, not(equalTo(before.documentTitle)))
    }

    @Test
    fun strongsMode_reflectsActiveWindowTextDisplaySetting() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)

        windowControl.activeWindowPageManager.textDisplaySettings.strongsMode = 1
        service.refresh()
        assertThat(service.toolbar.value.strongsMode, equalTo(1))

        // A later event-driven rebuild (not just refresh()) must re-read the live setting too.
        windowControl.activeWindowPageManager.textDisplaySettings.strongsMode = 2
        PassageChangeMediator.contentChangeFinished()
        assertThat(service.toolbar.value.strongsMode, equalTo(2))
    }

    @Test
    fun speakStateChange_rebuildsSnapshot() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value

        seedActivePageSilently(kjv, gen11) // silent -> no event yet
        assertThat(service.toolbar.value, equalTo(before))

        SpeakChanges.notifyState(SpeakPlaybackState.SILENT)

        val after = service.toolbar.value
        assertThat(after.documentTitle, equalTo(kjv.name))
        assertThat(after.documentTitle, not(equalTo(before.documentTitle)))
    }

    @Test
    fun snapshotCarriesTheWorkspaceColour() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        windowRepository.workspaceSettings.workspaceColor = 0xFF1B5E20.toInt()
        service.refresh()
        assertThat(service.toolbar.value.workspaceColorArgb, equalTo(0xFF1B5E20.toInt()))
    }

    @Test
    fun snapshotCarriesNullWhenTheWorkspaceHasNoColour() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        windowRepository.workspaceSettings.workspaceColor = null
        service.refresh()
        assertThat(service.toolbar.value.workspaceColorArgb, equalTo(null))
    }

    @Test
    fun workspaceColorChangedEvent_rebuildsSnapshotWithTheNewColour() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value.workspaceColorArgb

        val green = 0xFF1B5E20.toInt()
        windowRepository.workspaceSettings.workspaceColor = green
        // No passage/verse/window/speak/sync event happens here — this is exactly the situation the
        // maintainer hit: the colour is written by a settings screen and nothing else moves.
        WorkspaceChanges.notifyColorEdited()

        assertThat(before, not(equalTo(green)))
        assertThat(service.toolbar.value.workspaceColorArgb, equalTo(green))
    }

    @Test
    fun workspaceSwitch_doesNotRefreshTheToolbar() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value
        windowRepository.workspaceSettings.workspaceColor = 0xFF1B5E20.toInt()

        WorkspaceChanges.notifySwitched()

        assertThat("Switched alone must not rebuild the toolbar (it refreshes on the active-window change)",
            service.toolbar.value, equalTo(before))
    }

    @Test
    fun theWorkspaceColorGuardFailsOnAWriterWithoutTheNotify() {
        val writerWithoutNotify = listOf(
            "fun f(repo: WindowRepository) {",
            "    repo.workspaceSettings.workspaceColor = 1",
            "}",
        )
        val writerWithNotify = listOf(
            "    repo.workspaceSettings.workspaceColor = 1",
            "    WorkspaceChanges.notifyColorEdited()",
        )
        assertThat(unpairedWrites(listOf("Bad.kt" to writerWithoutNotify)), equalTo(listOf("Bad.kt:2")))
        assertThat(unpairedWrites(listOf("Good.kt" to writerWithNotify)), equalTo(emptyList<String>()))
    }

    @Test
    fun everyWorkspaceColorWriterNotifies() {
        // A/B batch 4a F1 fix round 1: the ORIGINAL version of this guard asserted `posts > 0` and
        // then `posts >= 1` — the same condition twice — so it could never fail; against
        // TextDisplaySettingsServiceImpl.kt (writes=8, posts=2 at the time) it still passed. This
        // version pairs every actual `workspaceSettings.workspaceColor =` WRITE (not a read like
        // `foo = ws.workspaceSettings?.workspaceColor` or a bare local like `it.workspaceColor =`)
        // with a `WorkspaceChanges.notifyColorEdited()` within the next few lines.
        //
        // A/B batch 4a whole-batch review I3: the ORIGINAL version of THIS scanned a literal
        // five-file list the reviewer traced live writers into by hand — so a thirteenth writer
        // added in a NEW file (exactly what the next work cycle on this area is likely to do) would
        // pass silently, defeating the guard's own kdoc claim (see WorkspaceChange.ColorEdited's kdoc) that it
        // catches this. Now walks the whole `src/main/java` tree instead: still cheap (a few
        // thousand files, plain line-regex, no parsing) and self-updating as files are added/moved/
        // renamed. Sorted so a failure message is stable/reproducible across runs (walkTopDown's
        // order is filesystem-dependent, not guaranteed).
        val root = java.io.File("src/main/java")
        val sourceFiles = root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .sortedBy { it.path }
            .toList()
        val unpaired = unpairedWrites(sourceFiles.map { it.path to it.readLines() })
        assertThat(
            "workspaceColor write(s) with no WorkspaceChanges.notifyColorEdited() within the next 4 lines: $unpaired",
            unpaired,
            equalTo(emptyList<String>()),
        )
    }

    private fun unpairedWrites(files: List<Pair<String, List<String>>>): List<String> {
        // Matches `<receiver>.workspaceSettings.workspaceColor =` / `<receiver>.workspaceSettings?.workspaceColor =`
        // (an actual write to the persisted field), never a read (`= foo.workspaceSettings?.workspaceColor`,
        // where nothing follows on the "=" side) nor a bare local (`it.workspaceColor =` / `c.workspaceColor =`,
        // which lack the `workspaceSettings` receiver segment entirely). The trailing `(?!=)` keeps a stray
        // `==` comparison from counting as a write.
        val writeRegex = Regex("""workspaceSettings\??\.workspaceColor\s*=(?!=)""")
        val postMarker = "WorkspaceChanges.notifyColorEdited()"
        val windowSize = 5 // the write's own line + the next 4, per the reviewer's "within the next 4 lines"
        // Strip a trailing `//` line comment before matching either the write or the post: a
        // *commented-out* post must NOT satisfy the guard (verified live below — see the fix
        // report's "guard has teeth" section), and this is a simple, sufficient heuristic since
        // nothing in these particular lines puts "//" inside a string literal.
        fun codeOnly(line: String) = line.substringBefore("//")
        // A/B batch 4a whole-batch review I3: walking the WHOLE tree (rather than the hand-picked
        // five files) surfaced a genuine false positive the old scope never could: this guard's own
        // kdoc, in WorkspaceChanges.kt, DOCUMENTS the exact write pattern it's guarding
        // (`` * pairs every `workspaceSettings.workspaceColor =` write... `` inside a `/** ... */`
        // block), which the write regex matches just as happily as real code. `codeOnly` only strips
        // `//` line comments, so a `/** ... */` block survives it untouched. stripBlockComments
        // removes `/* ... */` spans (single- and multi-line, e.g. this exact Kdoc) BEFORE codeOnly
        // runs per line, so documentation prose is never mistaken for a write/post site.
        fun stripBlockComments(rawLines: List<String>): List<String> {
            val out = ArrayList<String>(rawLines.size)
            var inBlock = false
            for (raw in rawLines) {
                val sb = StringBuilder()
                var i = 0
                while (i < raw.length) {
                    if (inBlock) {
                        val end = raw.indexOf("*/", i)
                        if (end == -1) { i = raw.length } else { i = end + 2; inBlock = false }
                    } else {
                        val start = raw.indexOf("/*", i)
                        if (start == -1) {
                            sb.append(raw, i, raw.length)
                            i = raw.length
                        } else {
                            sb.append(raw, i, start)
                            val end = raw.indexOf("*/", start + 2)
                            if (end == -1) { inBlock = true; i = raw.length } else { i = end + 2 }
                        }
                    }
                }
                out += sb.toString()
            }
            return out
        }
        val unpaired = mutableListOf<String>()
        for ((path, rawLines) in files) {
            val lines = stripBlockComments(rawLines).map(::codeOnly)
            lines.forEachIndexed { idx, line ->
                if (writeRegex.containsMatchIn(line)) {
                    val window = lines.subList(idx, minOf(lines.size, idx + windowSize))
                    if (window.none { it.contains(postMarker) }) unpaired += "$path:${idx + 1}"
                }
            }
        }
        return unpaired
    }
}
