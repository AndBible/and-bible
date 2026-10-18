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
package net.bible.sharedui.reading.host.slots

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryPlatform
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingModelVd
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedcore.speak.SpeakBookmarkRowVd
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.speak.SpeakTransportStateVd
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ReadingHostSlotsTest {
    private class MapSettings : AppSettings {
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private class NoPlatform : HistoryPlatform {
        override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
        override fun isOnReadingScreen() = true
        override fun leaveCurrentScreen() = Unit
    }

    private class FakeLinks(override val askBeforeOpening: Boolean) : ExternalLinks {
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    private class FakeAgentService : AgentSessionService {
        override val snapshot: StateFlow<AgentLogSnapshot> = MutableStateFlow(AgentLogSnapshot())
        override fun stop() = Unit
        override suspend fun configuredModels(): List<ReadingModelVd> = emptyList()
        override fun setDefaultModel(modelId: String) = Unit
        override fun autoHideEnabled() = false
        override fun logVisiblePref() = false
        override fun setLogVisiblePref(v: Boolean) = Unit
        override fun currentWorkspaceId() = "w"
        override suspend fun refresh() = Unit
    }

    private class FakeTransport(visible: Boolean) : SpeakTransportService {
        override val state = MutableStateFlow(SpeakTransportStateVd(visible = visible, statusText = "Reading Genesis"))
        override fun speakAny() = Unit
        override fun pause() = Unit
        override fun continueAfterPause() = Unit
        override fun stop() = Unit
        override fun rewind() = Unit
        override fun forward() = Unit
        override fun prevVerse() = Unit
        override fun nextVerse() = Unit
        override suspend fun speakBookmarks(): List<SpeakBookmarkRowVd> = emptyList()
        override fun speakFromBookmark(id: String) = Unit
    }

    private class FakeSpeakSettings : SpeakSettingsService {
        override val playback = MutableStateFlow(SpeakPlaybackVd(100, true, true, false, 0, null))
        override val advanced = MutableStateFlow(AdvancedSpeakVd(true, false, false, false))
        override fun setSpeed(percent: Int) = Unit
        override fun setSpeakChapterChanges(on: Boolean) = Unit
        override fun setSpeakTitles(on: Boolean) = Unit
        override fun setSpeakFootnotes(on: Boolean) = Unit
        override fun clearRepeatRange() = Unit
        override fun setSleepTimerMinutes(minutes: Int) = Unit
        override fun setRepeatRange(startOsisId: String, endOsisId: String) = Unit
        override fun setSynchronize(on: Boolean) = Unit
        override fun setReplaceDivineName(on: Boolean) = Unit
        override fun setAutoBookmark(on: Boolean) = Unit
        override fun setRestoreSettingsFromBookmarks(on: Boolean) = Unit
    }

    private val strings: Strings = iosStrings("en")

    /** [DocumentControl] has nine collaborators and is only read by the document tabs; skip its constructor. */
    private fun allocateDocumentControl(): DocumentControl {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, DocumentControl::class.java) as DocumentControl
    }

    private fun slots(
        links: ExternalLinks = FakeLinks(false),
        transportVisible: Boolean = false,
        fullScreen: Boolean = false,
    ): Pair<ReadingHostSlots, ReadingHostState> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val windowControl = WindowControl()
        val hostState = ReadingHostState(
            scope = scope,
            settings = MapSettings(),
            windowControl = windowControl,
            historyManager = HistoryManager(windowControl, NoPlatform()),
            documentControl = allocateDocumentControl(),
            hooks = net.bible.sharedui.reading.host.ReadingHostHooks(),
        )
        val slots = ReadingHostSlots(
            hostState = hostState,
            strings = { strings },
            externalLinks = links,
            agentLog = AgentLogController(FakeAgentService(), scope, onCompletedToast = {}, onOpenRawLog = {}),
            speakTransport = SpeakTransportController(
                FakeTransport(transportVisible), FakeSpeakSettings(), scope, onConfig = {},
            ),
            disableAnimations = { true },
            fullScreen = { fullScreen },
            searchState = ReadingSearchState(MapSettings(), scope),
            searchSettingsForEpub = { false },
            currentBookName = { "Genesis" },
            onSearchSettingsClosed = {},
        )
        return slots to hostState
    }

    @Test
    fun readingDialogShowsTheDeleteConfirmationAndCallsBack() = runComposeUiTest {
        val (slots, state) = slots()
        var deleted = 0
        state.showDeleteDocumentPageConfirm { deleted++ }
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme { slots.ReadingDialog() }
            }
        }
        onNodeWithText(strings.aiDocumentDeleteConfirmation).assertExists()
        onNodeWithText(strings.yes).performClick()
        assertEquals(1, deleted)
        assertTrue(state.readingDialog.value == null)
    }

    @Test
    fun readingDialogLinkAsksFirstInDiscreteMode() = runComposeUiTest {
        val links = FakeLinks(askBeforeOpening = true)
        val (slots, state) = slots(links)
        state.showHelp("Help", "<a href=\"https://example.org/x\">Example link</a>")
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme { slots.ReadingDialog() }
            }
        }
        onNodeWithText("Example link").performClick()
        assertTrue(links.opened.isEmpty(), "discrete mode must ask before opening")
        onNodeWithText(strings.externalLink).assertExists()
        // two OK buttons now (help + question); the question's is the last one composed
        onAllNodesWithText(strings.okay).onLast().performClick()
        assertEquals(listOf("https://example.org/x"), links.opened)
    }

    @Test
    fun speakBarShowsWhenTransportVisible() = runComposeUiTest {
        val (slots, _) = slots(transportVisible = true)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme { slots.SpeakBar(applyNavBarInset = false) }
            }
        }
        onNodeWithText("Reading Genesis").assertExists()
    }
}
