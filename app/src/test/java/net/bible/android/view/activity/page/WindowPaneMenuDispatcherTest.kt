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

package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.Window
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.platform.AndroidTextDisplayPrefItems
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.service.common.CommonUtils
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The common [WindowPaneMenuDispatcher] on a real window graph: window operations act on the window model (through
 * [WindowControl] when no reading-view controller is installed), the rows only a platform can do reach the
 * [WindowPaneMenuPlatform], and the clipboard reference slot round-trips copy reference then go to reference.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowPaneMenuDispatcherTest {
    private class RecordingPlatform : WindowPaneMenuPlatform {
        val calls = mutableListOf<String>()
        var copied: Pair<String, String>? = null
        override fun openAllTextOptions(window: Window) { calls += "allTextOptions" }
        override fun exportHtml(window: Window) { calls += "exportHtml" }
        override fun exportStudyPad(window: Window) { calls += "exportStudyPad" }
        override fun exportStudyPadCsv(window: Window) { calls += "exportStudyPadCsv" }
        override fun llmActions(window: Window) { calls += "llm" }
        override fun addWholePageBookmark(window: Window, bookInitials: String, osisRef: String) { calls += "bookmark:$bookInitials" }
        override fun copyReference(label: String, url: String) { copied = label to url; calls += "copyReference" }
        override fun openTextOption(window: Window, type: TextDisplaySettings.Types, item: PrefItemHandle, bundle: SettingsBundle): Boolean {
            calls += "textOption:${type.name}"
            return false
        }
    }

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var dispatcher: WindowPaneMenuDispatcher
    private val platform = RecordingPlatform()

    @Before
    fun setUp() {
        clipboardKey = null
        windowControl = CommonUtils.windowControl
        windowRepository = net.bible.test.testWindowRepository()
        windowControl.windowRepository = windowRepository
        windowRepository.initializeAtEdge()
        windowRepository.workspaceSettings.autoPin = true
        dispatcher = WindowPaneMenuDispatcher(
            windowControl = windowControl,
            speakControl = GlobalContext.get().get<SpeakControl>(),
            windowRepository = { windowRepository },
            controller = { null },
            prefItems = AndroidTextDisplayPrefItems(),
            recentDisplaySettings = { CommonUtils.lastDisplaySettingsSorted },
            globalTextDisplaySettings = { CommonUtils.globalTextDisplaySettings },
            platform = platform,
        )
    }

    @After
    fun tearDown() {
        clipboardKey = null
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    @Test
    fun maximiseActsOnTheWindowModelAndClosesTheMenu() {
        val window = windowRepository.activeWindow
        assertFalse(windowRepository.isMaximized)
        assertFalse(dispatcher.handle(window.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE))
        assertTrue(windowRepository.isMaximized)
    }

    @Test
    fun pinToggleFlipsPinModeAndKeepsTheMenuOpen() {
        windowRepository.workspaceSettings.autoPin = false
        val window = windowRepository.activeWindow
        window.isPinMode = false
        assertTrue(dispatcher.handle(window.id.toString(), WindowPaneMenuStateBuilder.ID_PIN_MODE))
        assertTrue(window.isPinMode)
    }

    @Test
    fun anUnknownWindowIsIgnored() {
        assertFalse(dispatcher.handle("00000000-0000-0000-0000-000000000000", WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE))
        assertEquals(emptyList(), platform.calls)
    }

    @Test
    fun platformOnlyRowsReachThePlatform() {
        val id = windowRepository.activeWindow.id.toString()
        assertFalse(dispatcher.handle(id, WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS))
        assertFalse(dispatcher.handle(id, WindowPaneMenuStateBuilder.ID_EXPORT_HTML))
        assertFalse(dispatcher.handle(id, WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU))
        assertEquals(listOf("allTextOptions", "exportHtml", "llm"), platform.calls)
    }

    @Test
    fun copyReferenceFillsTheClipboardSlotAndHandsTheLinkToThePlatform() {
        val window = windowRepository.activeWindow
        val page = window.pageManager.currentPage
        assertNotNull(page.currentDocument, "test modules provide a default Bible")
        dispatcher.handle(window.id.toString(), WindowPaneMenuStateBuilder.ID_COPY_REFERENCE)
        val copied = assertNotNull(platform.copied, "the platform got the link")
        assertTrue(copied.second.startsWith("https://read.andbible.org/"), copied.second)
        assertTrue(copied.second.contains("document=${page.currentDocument!!.initials}"), copied.second)
        assertNotNull(clipboardKey, "the app-wide reference slot is set")
    }

    @Test
    fun goToReferenceNavigatesToTheCopiedReference() {
        val first = windowRepository.activeWindow
        assertNotNull(first.pageManager.currentPage.currentDocument)
        dispatcher.handle(first.id.toString(), WindowPaneMenuStateBuilder.ID_COPY_REFERENCE)
        val copiedKey = assertNotNull(clipboardKey).key
        val second = windowRepository.addNewWindow(first)
        val v11n = org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV")
        second.pageManager.currentPage.setKey(
            org.crosswire.jsword.passage.Verse(v11n, org.crosswire.jsword.versification.BibleBook.GEN, 1, 1), true,
        )
        val chapter = copiedKey.getOsisRef().substringBeforeLast('.')
        assertFalse(second.pageManager.currentPage.key?.getOsisRef()?.startsWith(chapter) == true, "premise: not there yet")
        assertFalse(dispatcher.handle(second.id.toString(), WindowPaneMenuStateBuilder.ID_GO_TO_REFERENCE))
        // A Bible page keeps the chapter (the verse is the anchor), so compare book and chapter.
        assertTrue(second.pageManager.currentPage.key?.getOsisRef()?.startsWith(chapter) == true, "went to $chapter")
    }

    @Test
    fun everyRowListedAsPlatformRoutedReachesThePlatformExactlyOnce() {
        val id = windowRepository.activeWindow.id.toString()
        for (row in WindowPaneMenuDispatcher.PLATFORM_ROWS) {
            platform.calls.clear()
            dispatcher.handle(id, row)
            assertEquals(1, platform.calls.size, "$row reaches the platform once: ${platform.calls}")
        }
    }
}
