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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.ios.IosPending
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingAppInitPlatform
import net.bible.sharedui.reading.ReadingToolbarIcons
import org.koin.mp.KoinPlatform
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The iOS reading host deps build once the window repository exists, and follow the active window and night mode. */
class IosReadingHostTest {
    private object NoNavigator : IosNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    private class FakeNight(override val nightMode: Boolean) : NightModeState

    @Test
    fun depsBuildAfterWindowRepository() {
        val scope = MainScope()
        try {
            IosAppStartup.start(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            )
            runBlocking { IosAppStartup.openDatabases() }
            val platform = IosReadingAppInitPlatform(IosAppStartup.stringTable)
            runBlocking {
                ReadingAppInit.initialise(platform) // production order: initialise, then prepareData
                ReadingAppInit.prepareData(platform)
            }
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val shell = IosHostShell(scope, NoNavigator)
            val p = ColorPainter(Color.Black)
            val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)

            val host = IosReadingHost(scope, shell, koin)
            val deps = host.deps(icons)
            val activeId = koin.get<WindowControl>().activeWindow.id.toString()
            assertEquals(activeId, deps.controller.layout.value.activeWindowId)
            // hostState reads the active window through its WindowControl and HistoryManager.
            host.hostState.historyEntriesForActiveWindow()
            assertTrue(deps.state === host.hostState)
            assertEquals("Bible Study", host.navDeps {}.windowTitle)

            // Every toolbar callback is safe to invoke before a window exists on screen, and none is a logged pending
            // (see toolbarCallbacksAreWiredAndNoneIsPending for what each one does).
            IosPending.resetForTest()
            deps.toolbarCallbacks.apply {
                onHome(); onTitleTap(); onTitleLongPress(); onTitleFlingVertical(); onTitleFlingHorizontal(true)
                onBible(); onBibleLong(); onCommentary(); onCommentaryLong(); onStrongs(); onStrongsLong()
                onSearch(); onSpeak(); onSpeakLong(); onWorkspace(); onOverflow()
            }
            assertEquals(emptyList(), IosPending.reported.filter { it.startsWith("ReadingToolbar.") }, "toolbar pendings")

            // Night mode follows NightModeState, both ways.
            assertTrue(IosReadingHost(scope, shell, koin, FakeNight(true)).deps(icons).nightMode.value)
            assertFalse(IosReadingHost(scope, shell, koin, FakeNight(false)).deps(icons).nightMode.value)
        } finally {
            scope.cancel()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    /** Boots the app parts the host reads (as the first test does) and runs [block]; tears everything down after. */
    private fun withReadingHost(
        openUrl: (String) -> Unit = {},
        block: (host: IosReadingHost, deps: net.bible.sharedui.reading.host.ReadingHostDeps) -> Unit,
    ) {
        val scope = MainScope()
        try {
            IosAppStartup.start(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            )
            runBlocking { IosAppStartup.openDatabases() }
            val platform = IosReadingAppInitPlatform(IosAppStartup.stringTable)
            runBlocking {
                ReadingAppInit.initialise(platform)
                ReadingAppInit.prepareData(platform)
            }
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val p = ColorPainter(Color.Black)
            val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)
            val host = IosReadingHost(
                scope, IosHostShell(scope, NoNavigator), koin, FakeNight(false), openUrl = openUrl,
            )
            try {
                block(host, host.deps(icons))
            } finally {
                host.dispose()
            }
        } finally {
            scope.cancel()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    /**
     * EVERY `ReadingHostDeps` field is accounted for: supplied (not the inert default `ReadingHostDeps` ships) or on the named
     * inert allow-list below, whose entries are each asserted to still be inert so lifting one fails here on purpose. The set of
     * names must equal `ReadingHostDepsFields.ALL`, which a jvm test pins against the class itself, so a new slot cannot slip in.
     *
     * Default lambdas are shared singletons (non-capturing, hoisted by the compiler), so identity with a default-built instance
     * means "not supplied"; the first assertion pins that premise. State-typed fields (a default is a fresh object) are checked
     * against the very object the host wires them to.
     */
    @Test
    fun everyReadingHostDepsFieldIsSuppliedOrNamedInert() = withReadingHost { host, deps ->
        val p = ColorPainter(Color.Black)
        val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)
        val d1 = net.bible.sharedui.reading.host.ReadingHostDeps(deps.state, deps.controller, deps.toolbarCallbacks, { icons })
        val d2 = net.bible.sharedui.reading.host.ReadingHostDeps(deps.state, deps.controller, deps.toolbarCallbacks, { icons })
        assertTrue(d1.searchSheetSlot === d2.searchSheetSlot, "premise: default slot lambdas are shared instances")
        val search = host.slots.searchState

        val supplied: Map<String, Boolean> = mapOf(
            // required parameters
            "state" to (deps.state === host.hostState),
            "controller" to (deps.controller.layout.value.activeWindowId.isNotEmpty()),
            // (required parameters cannot be left at a default; toolbarCallbacksAreWiredAndNoneIsPending covers the callbacks)
            "toolbarCallbacks" to true,
            "toolbarIcons" to (deps.toolbarIcons !== d1.toolbarIcons),
            // toolbar, display state, overflow, drawer, panes
            "toolbar" to (deps.toolbar === KoinPlatform.getKoin().get<net.bible.sharedcore.reading.ToolbarStateService>().toolbar),
            "nightMode" to (deps.nightMode === host.nightState),
            "monochrome" to (deps.monochrome === host.monochromeState),
            "disableAnimations" to (deps.disableAnimations !== d1.disableAnimations),
            "overflowItems" to (deps.overflowItems === host.toolbarHost.overflowItems),
            "overflowExpanded" to (deps.overflowExpanded === host.toolbarHost.overflowExpanded),
            "onOverflowItemClick" to (deps.onOverflowItemClick !== d1.onOverflowItemClick),
            "onOverflowDismiss" to (deps.onOverflowDismiss !== d1.onOverflowDismiss),
            "drawerMenu" to (deps.drawerMenu === host.drawerMenuState),
            "drawerIcon" to (deps.drawerIcon !== d1.drawerIcon),
            "onDrawerItemClick" to (deps.onDrawerItemClick !== d1.onDrawerItemClick),
            "onDrawerClosed" to (deps.onDrawerClosed !== d1.onDrawerClosed),
            "paneBackground" to (deps.paneBackground !== d1.paneBackground),
            "windowLabel" to (deps.windowLabel !== d1.windowLabel),
            "windowIcon" to (deps.windowIcon !== d1.windowIcon),
            "windowTopLabel" to (deps.windowTopLabel !== d1.windowTopLabel),
            "handlePaneMenuItem" to (deps.handlePaneMenuItem !== d1.handlePaneMenuItem),
            "menuIcon" to (deps.menuIcon !== d1.menuIcon),
            // the speak bar's choose-bookmark dialog
            "speakDialog" to (deps.speakDialog === host.speakTransport.dialog),
            "onSpeakBookmarkChosen" to (deps.onSpeakBookmarkChosen !== d1.onSpeakBookmarkChosen),
            "onSpeakDialogDismiss" to (deps.onSpeakDialogDismiss !== d1.onSpeakDialogDismiss),
            // search: the field, the results sheet, the unavailable snackbar
            "searchBar" to (deps.searchBar === search.searchBar),
            "searchBarCallbacks" to (deps.searchBarCallbacks === search.searchBarCallbacks),
            "searchSheetVisible" to (deps.searchSheetVisible === search.controller.sheetVisible),
            "onSearchSheetDismissed" to (deps.onSearchSheetDismissed !== d1.onSearchSheetDismissed),
            "onSearchSheetOffsetsChanged" to (deps.onSearchSheetOffsetsChanged !== d1.onSearchSheetOffsetsChanged),
            "searchUnavailableDocName" to (deps.searchUnavailableDocName === search.searchUnavailableDocName),
            "onSearchUnavailableMessageShown" to (deps.onSearchUnavailableMessageShown !== d1.onSearchUnavailableMessageShown),
            // platform-built slots
            "agentLogSlot" to (deps.agentLogSlot != null && d1.agentLogSlot == null),
            "speakBarSlot" to (deps.speakBarSlot != null && d1.speakBarSlot == null),
            "agentLogVisible" to (deps.agentLogVisible !== d1.agentLogVisible),
            "speakBarVisible" to (deps.speakBarVisible !== d1.speakBarVisible),
            "searchSheetSlot" to (deps.searchSheetSlot !== d1.searchSheetSlot),
            "searchSettingsSlot" to (deps.searchSettingsSlot !== d1.searchSettingsSlot),
            "textSettingsEditorSlot" to (deps.textSettingsEditorSlot !== d1.textSettingsEditorSlot),
            "speakSettingsSlot" to (deps.speakSettingsSlot !== d1.speakSettingsSlot),
            "quickSheetContent" to (deps.quickSheetContent !== d1.quickSheetContent),
            "readingDialogSlot" to (deps.readingDialogSlot !== d1.readingDialogSlot),
        )
        assertEquals(emptyList(), supplied.filterValues { !it }.keys.toList(), "fields left at the inert default")

        // INERT ALLOW-LIST, each with its reason, each asserted to be still inert:
        // - generation, imeBottomPaddingPx: Android bumps/feeds them from activity events; iOS never rebuilds the pane subtree
        //   and Compose handles the keyboard inset itself.
        // - fullScreen: iOS has no full-screen reading mode yet (named on IosReadingToolbarHost).
        // - overlayText, activeIsBibleShown: the Bible reference overlay is not wired on iOS (a parity gap, see the R2 final-fix report).
        // - onDrawerInMotion, onDrawerIdleClosed: Android shows/hides the system bars around the drawer; iOS has no such chrome.
        // - the seven LLM fields: no agent start on iOS (the LLM dialog host is not mounted; see hooksAndSlot...).
        val inert: Map<String, Boolean> = mapOf(
            "generation" to (deps.generation.value == 0),
            "imeBottomPaddingPx" to (deps.imeBottomPaddingPx.value == 0),
            "fullScreen" to !deps.fullScreen.value,
            "overlayText" to (deps.overlayText.value == ""),
            "activeIsBibleShown" to !deps.activeIsBibleShown.value,
            "onDrawerInMotion" to (deps.onDrawerInMotion === d1.onDrawerInMotion),
            "onDrawerIdleClosed" to (deps.onDrawerIdleClosed === d1.onDrawerIdleClosed),
            "llmDialog" to (deps.llmDialog.value == net.bible.sharedcore.ai.reading.ReadingLlmDialogState()),
            "onLlmPromptChosen" to (deps.onLlmPromptChosen === d1.onLlmPromptChosen),
            "onLlmToggleFavorite" to (deps.onLlmToggleFavorite === d1.onLlmToggleFavorite),
            "onLlmCategoryExpandedChanged" to (deps.onLlmCategoryExpandedChanged === d1.onLlmCategoryExpandedChanged),
            "onLlmSpecifySubmitted" to (deps.onLlmSpecifySubmitted === d1.onLlmSpecifySubmitted),
            "onLlmModelChosen" to (deps.onLlmModelChosen === d1.onLlmModelChosen),
            "onLlmRegenerateConfirmed" to (deps.onLlmRegenerateConfirmed === d1.onLlmRegenerateConfirmed),
            "onLlmDismiss" to (deps.onLlmDismiss === d1.onLlmDismiss),
        )
        assertEquals(emptyList(), inert.filterValues { !it }.keys.toList(), "allow-listed fields that are no longer inert")

        assertEquals(emptySet(), supplied.keys.intersect(inert.keys), "a field is both supplied and inert")
        assertEquals(
            net.bible.sharedui.reading.host.ReadingHostDepsFields.ALL, supplied.keys + inert.keys,
            "every ReadingHostDeps field must be supplied or named inert",
        )
    }

    /**
     * Every `ReadingToolbarCallbacks` member is invoked by name; none may log a pending. The allow-list of callbacks
     * that may stay pending is EMPTY: every gap is inside a wired callback and named on `IosReadingToolbarHost` (full
     * screen, auto assign labels and LLM actions in the overflow menu) and `ReadingDrawerMenu.OMITTED_ROWS`.
     * Not compiled or run on Linux (no Mac): owed to the Mac run.
     */
    @Test
    fun toolbarCallbacksAreWiredAndNoneIsPending() = withReadingHost { host, deps ->
        val c = deps.toolbarCallbacks
        val calls: Map<String, () -> Unit> = mapOf(
            "onHome" to c.onHome, "onTitleTap" to c.onTitleTap, "onTitleLongPress" to c.onTitleLongPress,
            "onTitleFlingVertical" to c.onTitleFlingVertical, "onTitleFlingHorizontal" to { c.onTitleFlingHorizontal(true) },
            "onBible" to c.onBible, "onBibleLong" to c.onBibleLong, "onCommentary" to c.onCommentary,
            "onCommentaryLong" to c.onCommentaryLong, "onStrongs" to c.onStrongs, "onStrongsLong" to c.onStrongsLong,
            "onSearch" to c.onSearch, "onSpeak" to c.onSpeak, "onSpeakLong" to c.onSpeakLong,
            "onWorkspace" to c.onWorkspace, "onOverflow" to c.onOverflow,
        )
        // Premise: the map names every member (the data class has 16).
        assertEquals(16, calls.size)
        val allowedPending = emptySet<String>()
        for ((name, call) in calls) {
            IosPending.resetForTest()
            call()
            val pending = IosPending.reported.filter { it.startsWith("ReadingToolbar.") }
            if (name !in allowedPending) assertEquals(emptyList(), pending, "$name is still a logged pending")
        }

        // Observable effects of the ones that open something.
        host.hostState.closeDrawer()
        c.onHome()
        assertTrue(host.hostState.isDrawerOpen, "home opens the drawer")
        c.onHome()
        assertFalse(host.hostState.isDrawerOpen, "home with the drawer open closes it (Android's toggleDrawer)")
        c.onTitleLongPress()
        assertTrue(host.hostState.quickSheet.value is net.bible.sharedcore.reading.ReadingQuickSheet.Documents, "title long press: document sheet")
        host.hostState.closeQuickSheet()
        c.onWorkspace()
        assertEquals(net.bible.sharedcore.reading.ReadingQuickSheet.Workspaces, host.hostState.quickSheet.value)
        host.hostState.closeQuickSheet()
        c.onTitleFlingVertical()
        assertEquals(net.bible.sharedcore.reading.ReadingQuickSheet.Workspaces, host.hostState.quickSheet.value)
        host.hostState.closeQuickSheet()
        c.onSpeakLong()
        assertTrue(host.slots.speakSheet.depth > 0, "speak long press opens the speak settings")

        // The overflow menu opens with Android's rows (minus the named gaps) and a row closes it.
        c.onOverflow()
        assertTrue(deps.overflowExpanded.value)
        val ids = deps.overflowItems.value.map { it.id }
        assertTrue("nightMode" in ids && "switchToWorkspace" in ids && "allTextOptions" in ids, "overflow rows: $ids")
        deps.onOverflowItemClick("switchToWorkspace")
        assertFalse(deps.overflowExpanded.value, "a non-toggle row closes the menu")
        assertEquals(net.bible.sharedcore.reading.ReadingQuickSheet.Workspaces, host.hostState.quickSheet.value)

        // A drawer row acts, and the toolbar state is the service's flow (not the empty default).
        host.hostState.closeQuickSheet()
        deps.onDrawerItemClick("historyButton")
        assertEquals(net.bible.sharedcore.reading.ReadingQuickSheet.History, host.hostState.quickSheet.value)
        assertTrue(deps.toolbar === KoinPlatform.getKoin().get<net.bible.sharedcore.reading.ToolbarStateService>().toolbar)
    }

    /**
     * The `ReadingHostHooks` and `ReadingHostSlots` parameters. What iOS supplies must not be the inert default; what
     * it deliberately leaves inert is the named allow-list below, each entry asserted to still hold, so lifting one
     * (or breaking a supplied one) fails here and the list has to be updated on purpose.
     */
    @Test
    fun hooksAndSlotParametersAreSuppliedExceptTheNamedInertOnes() = withReadingHost { host, _ ->
        val defaults = net.bible.sharedui.reading.host.ReadingHostHooks()
        val h = host.hooks
        // Premise: default hook lambdas are shared instances (else every comparison below is vacuous).
        assertTrue(defaults.closeSpeakSheet === net.bible.sharedui.reading.host.ReadingHostHooks().closeSpeakSheet, "premise")
        val supplied = mapOf(
            "closeSpeakSheet" to (h.closeSpeakSheet !== defaults.closeSpeakSheet),
            "closeTextSettingsEditor" to (h.closeTextSettingsEditor !== defaults.closeTextSettingsEditor),
            "openCompare" to (h.openCompare !== defaults.openCompare),
            "applyChosenVerse" to (h.applyChosenVerse !== defaults.applyChosenVerse),
            "formatHistoryTimestamp" to (h.formatHistoryTimestamp !== defaults.formatHistoryTimestamp),
            "installedDocRows" to (h.installedDocRows !== defaults.installedDocRows),
            "recentDocumentInitials" to (h.recentDocumentInitials !== defaults.recentDocumentInitials),
            "paneMenuItemsFor" to (h.paneMenuItemsFor !== defaults.paneMenuItemsFor),
        )
        assertEquals(emptyList(), supplied.filterValues { !it }.keys.toList(), "hooks left at the default")
        // INERT ALLOW-LIST (hooks): LLM sheets/regenerate (Task 4 left them: the agent run is started by Android's
        // AgentForegroundService.startAgent/startRegenerate, which has no common or iOS counterpart, and the LLM dialog host is
        // not mounted on iOS; the agent's permission prompt and question dialogs ARE real now, in IosAgentPlatform), and the three optional overrides that
        // default to the repository's own behaviour.
        val inertHooks = mapOf(
            "dismissLlmSheets" to (h.dismissLlmSheets === defaults.dismissLlmSheets),
            "openPromptSelector" to (h.openPromptSelector === defaults.openPromptSelector),
            "openRegenerate" to (h.openRegenerate === defaults.openRegenerate),
            "activeWindowId" to (h.activeWindowId == null),
            "historyItemsFor" to (h.historyItemsFor == null),
            "forVerseInitials" to (h.forVerseInitials == null),
        )
        assertEquals(emptyList(), inertHooks.filterValues { !it }.keys.toList(), "allow-listed hooks that are no longer inert")

        val s = host.slots
        assertTrue(s.speakPlatform is IosSpeakSettingsPlatform, "speakPlatform")
        assertTrue(s.quickActions is IosReadingQuickActions, "quickActions")
        assertTrue(s.keyChooserPage is IosReadingKeyChooserPage, "keyChooserPage")
        assertTrue(s.searchState.platform is IosReadingSearchPlatform, "search platform")
        // INERT ALLOW-LIST (slot parameters): fullScreen and thumbnailFor constant,
        // toast goes through UserNotifier to AppToasts (real since Task 3), image picker / external links are Stream F's
        // pending ports.
        assertTrue(s.navigator is IosReadingNavigator && s.navigator === host.navigator, "navigator")
        assertFalse(s.fullScreen(), "fullScreen")
        assertEquals(null, s.thumbnailFor("any"), "thumbnailFor")
        assertTrue(s.imagePicker is net.bible.sharedui.ios.ports.IosImagePicker, "imagePicker")
        assertTrue(s.textShare is net.bible.sharedui.ios.ports.IosTextShare, "textShare (real share sheet since Task 3)")
        assertTrue(s.externalLinks is net.bible.sharedui.ios.ports.IosExternalLinks, "externalLinks")
    }

    /**
     * The ☰ pane menu: the hook builds the common menu (rows iOS cannot run left out) and a chosen row acts through the
     * common dispatcher on the real window model, with the pin toggle keeping the menu open as on Android.
     */
    @Test
    fun paneMenuIsBuiltAndAChosenRowActs() = withReadingHost { host, deps ->
        val control = KoinPlatform.getKoin().get<WindowControl>()
        val active = control.activeWindow.id.toString()
        val items = host.hooks.paneMenuItemsFor(active)
        assertTrue(items != null, "the hook builds items for a known window")
        val ids = items.map { it.id }
        assertTrue(WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU in ids, "text options row: $ids")
        assertTrue(WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE in ids, "maximise row: $ids")
        for (omitted in IOS_OMITTED_PANE_MENU_ROWS) assertFalse(omitted in ids, "omitted row $omitted is absent")
        assertEquals(null, host.hooks.paneMenuItemsFor("no-such-window"), "an unknown window has no menu")

        assertFalse(control.windowRepository.isMaximized)
        assertFalse(deps.handlePaneMenuItem(active, WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE), "maximise closes the menu")
        assertTrue(control.windowRepository.isMaximized, "the row acted on the window model")
        // A stale or unknown window id is ignored, not thrown.
        assertFalse(deps.handlePaneMenuItem("no-such-window", WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE))
    }

    /** The overlay-exclusion hooks reach the slots' own stacks: opening a quick sheet closes the Speak sheet. */
    @Test
    fun overlayExclusionClosesTheSpeakSheetThroughTheSlots() = withReadingHost { host, _ ->
        host.slots.showSpeakSettings()
        assertTrue(host.slots.speakSheet.depth > 0, "speak sheet opened")
        host.hostState.showHistorySheet()
        assertEquals(0, host.slots.speakSheet.depth, "speak sheet closed by the quick sheet")
    }

    /** The Speak sheet's system-TTS row opens the app's page in Settings through the host's URL port. */
    @Test
    fun speakSettingsOpensSystemSettingsThroughThePort() {
        val opened = mutableListOf<String>()
        withReadingHost(openUrl = { opened += it }) { host, _ ->
            host.slots.speakPlatform.openSystemTtsSettings()
        }
        assertEquals(listOf(UIApplicationOpenSettingsURLString), opened)
    }

    /** The port on its own: one call, one URL, the Settings-app URL. */
    @Test
    fun speakSettingsPlatformOpensTheSettingsUrl() {
        val opened = mutableListOf<String>()
        IosSpeakSettingsPlatform { opened += it }.openSystemTtsSettings()
        assertEquals(listOf(UIApplicationOpenSettingsURLString), opened)
    }
}
