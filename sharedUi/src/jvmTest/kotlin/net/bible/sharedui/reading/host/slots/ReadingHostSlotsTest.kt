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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import java.lang.reflect.Proxy
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
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingsSnapshot
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedcore.speak.SpeakBookmarkRowVd
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.speak.SpeakTransportStateVd
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.ImagePicker
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

    /** What the [ImagePicker] port answers, how often it was asked, and what the service's import received from it. */
    private var pickerAnswer: String? = null
    private var pickerCalls = 0
    private var importReceived: String? = null
    private val imagePicker = object : ImagePicker {
        override suspend fun pickImage(): String? {
            pickerCalls++
            return pickerAnswer
        }
    }

    /** The font size the fake text-settings service reports; a test changes it to model an edit made elsewhere. */
    private var fontSize = 18

    private fun snapshot(scope: SettingsScope): TextSettingsSnapshot {
        val choice = TextSettingRowValue.Choice("0", listOf(SettingsItem.Choice("0", "Off")))
        val rows = TextSettingType.entries.associateWith { t ->
            val value: TextSettingRowValue = when (t) {
                TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
                TextSettingType.SCROLL_HELPER_LINE_STYLE -> choice
                TextSettingType.FONTFAMILY ->
                    TextSettingRowValue.Choice("sans-serif", listOf(SettingsItem.Choice("sans-serif", "Sans serif")))
                TextSettingType.FONTSIZE -> TextSettingRowValue.Numeric(fontSize, 1, 60, "$fontSize pt")
                TextSettingType.TOPMARGIN, TextSettingType.LINE_SPACING ->
                    TextSettingRowValue.Numeric(16, 1, 60, "16 pt")
                TextSettingType.MARGINSIZE -> TextSettingRowValue.Margins(3, 3, 170, 30, 30, 500, "3/3/170 mm")
                TextSettingType.COLORS -> TextSettingRowValue.ColorsNav("Colours")
                TextSettingType.BOOKMARKS_HIDELABELS -> TextSettingRowValue.HideLabels("2 labels hidden")
                else -> TextSettingRowValue.Bool(true)
            }
            TextSettingRow(t, value, InheritedFrom.NONE, enabled = true, visible = true, iconKey = null)
        }
        return TextSettingsSnapshot(
            scope, "Title", "My WS", rows,
            showParentCategory = false, showWorkspaceLink = false, showGlobalLink = false,
        )
    }

    private val colours = ColorsSnapshot(
        title = "Colours", dayTextColor = 0, dayBackground = 0, dayNoise = 0,
        nightTextColor = 0, nightBackground = 0, nightNoise = 0,
        workspaceColor = 0, workspaceColorVisible = false,
        dayBackgroundImageInitials = null, dayBackgroundImageName = "None", dayBackgroundImageOpacity = 100,
        nightBackgroundImageInitials = null, nightBackgroundImageName = "None", nightBackgroundImageOpacity = 100,
        inheritedFrom = InheritedFrom.NONE,
    )

    private val unusedService: TextDisplaySettingsService = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(TextDisplaySettingsService::class.java),
    ) { _, method, _ -> error("unexpected call: ${method.name}") } as TextDisplaySettingsService

    private val textSettingsService: TextDisplaySettingsService = object : TextDisplaySettingsService by unusedService {
        override fun loadText(scope: SettingsScope) = snapshot(scope)
        override fun loadColors(scope: SettingsScope) = colours
        override fun loadBackgroundOptions(): List<BackgroundImageOption> = emptyList()
        override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? {
            importReceived = picker()
            return null
        }
    }

    private fun fontSizeOf(slots: ReadingHostSlots, scope: SettingsScope) =
        (slots.textSettingsControllerFor(scope).state.value.rows.getValue(TextSettingType.FONTSIZE).value
            as TextSettingRowValue.Numeric).value

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
        textService: TextDisplaySettingsService = textSettingsService,
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
            textDisplaySettingsService = { textService },
            scope = scope,
            imagePicker = imagePicker,
            thumbnailFor = { null },
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

    // ---- text-settings editor ----------------------------------------------------------------

    @Test
    fun showTextSettingEditorOpensTheStackAtThatPage() {
        val (slots, _) = slots()
        slots.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Row("FONTSIZE")) { }
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), slots.textSettingsEditor.current)
        assertEquals(1, slots.textSettingsEditor.depth)
        // Re-opening starts a fresh stack rather than stacking on the old one.
        slots.textSettingsEditor.push(SettingsEditorPage.ColorPick(ColorField.DAY_TEXT))
        slots.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Row("FONTSIZE")) { }
        assertEquals(1, slots.textSettingsEditor.depth)
    }

    @Test
    fun showTextSettingEditorRefreshesTheCachedControllerBeforeReopening() {
        val (slots, _) = slots()
        val scope = SettingsScope.Workspace("ws")
        slots.showTextSettingEditor(scope, SettingsEditorPage.Row(TextSettingType.FONTSIZE.name)) { }
        assertEquals(18, fontSizeOf(slots, scope))
        slots.textSettingsEditor.close()

        fontSize = 30 // an edit made through some other controller instance
        slots.showTextSettingEditor(scope, SettingsEditorPage.Row(TextSettingType.FONTSIZE.name)) { }

        assertEquals(30, fontSizeOf(slots, scope))
    }

    @Test
    fun textSettingsControllerIsCachedPerScopeAndColourControllerIsNot() {
        val (slots, _) = slots()
        val a = SettingsScope.Workspace("a")
        assertTrue(slots.textSettingsControllerFor(a) === slots.textSettingsControllerFor(a))
        assertTrue(slots.textSettingsControllerFor(a) !== slots.textSettingsControllerFor(SettingsScope.Workspace("b")))
        assertTrue(slots.colorControllerFor(a) !== slots.colorControllerFor(a))
    }

    @Test
    fun colourControllerImportUsesTheImagePickerPort() {
        val (slots, _) = slots()
        pickerAnswer = "img-token"
        val controller = slots.colorControllerFor(SettingsScope.Workspace("ws"))
        controller.onImportBackgroundImage()
        assertEquals(1, pickerCalls, "the import must ask the ImagePicker port exactly once")
        assertEquals("img-token", importReceived, "the import must receive the port's answer")
    }

    @Test
    fun rowEditorBackClosesTheRowFirst() = runComposeUiTest {
        val (slots, _) = slots()
        val scope = SettingsScope.Workspace("ws")
        slots.showTextSettingEditor(scope, SettingsEditorPage.Colors) { }
        slots.textSettingsEditor.push(SettingsEditorPage.ColorPick(ColorField.DAY_TEXT))
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme { slots.TextSettingsEditor() }
            }
        }
        assertEquals(2, slots.textSettingsEditor.depth)

        // Back / dismiss on the stacked row editor steps back one page; the colours page stays open.
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()

        assertEquals(1, slots.textSettingsEditor.depth)
        assertEquals(SettingsEditorPage.Colors, slots.textSettingsEditor.current)
        // The sheet is still rendered (now on the colours page), not dismissed with the row.
        onNodeWithContentDescription(strings.settingsEditorClose).assertExists()
        onAllNodesWithContentDescription(strings.settingsEditorBack).assertCountEquals(0)
    }

    @Test
    fun numericRowEditorBackClosesTheRowFirst() = runComposeUiTest {
        val (slots, _) = slots()
        val scope = SettingsScope.Workspace("ws")
        slots.showTextSettingEditor(scope, SettingsEditorPage.Row(TextSettingType.FONTSIZE.name)) { }
        slots.textSettingsEditor.push(SettingsEditorPage.Row(TextSettingType.TOPMARGIN.name))
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme { slots.TextSettingsEditor() }
            }
        }
        onNodeWithText(strings.prefsTopMarginTitle).assertExists()

        onNodeWithContentDescription(strings.settingsEditorBack).performClick()

        assertEquals(1, slots.textSettingsEditor.depth)
        assertEquals(SettingsEditorPage.Row(TextSettingType.FONTSIZE.name), slots.textSettingsEditor.current)
        // Still rendered, now showing the font size row editor.
        onNodeWithText(strings.fontSizeTitle).assertExists()
        onAllNodesWithText(strings.prefsTopMarginTitle).assertCountEquals(0)
    }

    /**
     * Proves only that the open editor reappears on the same page when its composition is disposed and
     * composed afresh, because the page stack lives in [ReadingHostSlots], not in composition. It does
     * NOT exercise saved-instance-state save/restore: `StateRestorationTester` is not implemented on
     * the skiko (JVM/iOS) test target, and the editor keeps no `rememberSaveable` state anyway.
     */
    @Test
    fun editorReappearsOnTheSamePageWhenTheCompositionIsRecreated() = runComposeUiTest {
        val (slots, _) = slots()
        var composed by mutableStateOf(true)
        slots.showTextSettingEditor(
            SettingsScope.Workspace("ws"), SettingsEditorPage.Row(TextSettingType.FONTSIZE.name),
        ) { }
        setContent {
            if (composed) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    MaterialTheme { slots.TextSettingsEditor() }
                }
            }
        }
        onNodeWithText(strings.okay).assertExists()

        composed = false
        waitForIdle()
        onAllNodesWithText(strings.okay).assertCountEquals(0)
        composed = true
        waitForIdle()

        assertEquals(SettingsEditorPage.Row(TextSettingType.FONTSIZE.name), slots.textSettingsEditor.current)
        onNodeWithText(strings.okay).assertExists()
    }
}
