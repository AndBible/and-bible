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

package net.bible.sharedui.workspaces.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.KEY_OPEN_GLOBAL_SETTINGS
import net.bible.sharedcore.settings.KEY_OPEN_WORKSPACE_SETTINGS
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsScreenState
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot

/**
 * The text-display-settings destination's ported LOGIC, exercised rather than text-scanned.
 *
 * **Why this file exists** (nav-graph slice 7 Task 5 fix round 1, Major 2). Task 5 moved four pieces
 * of classic `TextDisplaySettingsComposeActivity` into `WorkspaceNavGraph.kt` -- the drill-up
 * `pop()`, the `onNavigate` branch table, `shouldCloseSearchOnBack` and `badgeLabel` -- and added two
 * tests that assert certain STRINGS appear in the graph source. A source scan cannot fail for a
 * wrong answer, only for a missing call, and each of these four was covered by a classic test that
 * Task 13 deletes along with the Activity:
 *
 * - `pop()` and its `startedAtColors` branch: classic reached the same behaviour through
 *   `TextDisplaySettingsComposeActivityColorsTest` (which, honestly, only pins that a plain launch
 *   starts on the LIST -- `pop()` itself was never driven, so these are a strengthening rather than
 *   a straight port).
 * - `shouldCloseSearchOnBack`: `TextDisplaySettingsComposeActivityBackTest`'s four cases. Classic
 *   made its copy `internal` *specifically* so a test could prove a defect the gate once hid (an
 *   unconditional `searchActive` check swallowed a back press from inside the colours
 *   sub-destination); the `commonMain` twin was `private` and untested, i.e. the lesson was ported
 *   and the protection was not.
 * - `badgeLabel`: covered nowhere.
 *
 * These are plain `commonTest` tests: none of this needs a Compose runtime or a `NavHost`. The arm
 * WIRING that surrounds them -- the memo, the channels, the navigation -- is a different kind of
 * claim and is pinned by `:app`'s `TextDisplaySettingsInGraphTest`, which composes the real graph.
 */
class TextDisplaySettingsNavStateTest {

    // ——— fixtures ———————————————————————————————————————————————————————————————————————————————

    /**
     * Enough of [TextDisplaySettingsService] for a real [TextDisplaySettingsController] to build and
     * [TextDisplaySettingsController.refresh] to be OBSERVABLE -- which is the whole point of the
     * `loadCalls` counter: `pop()`'s two refreshing branches have no other visible effect, so a fake
     * that did not count reads could not tell a `pop()` that refreshes from one that does not.
     *
     * The colours half throws: nothing in [TextDisplaySettingsNavState] touches it (the arm builds
     * colour controllers, this class only records which scope is showing), so a stub returning
     * plausible values would merely hide a future call that should not be there.
     */
    private class FakeService : TextDisplaySettingsService {
        val loadCalls = mutableListOf<SettingsScope>()
        val revertCalls = mutableListOf<Pair<SettingsScope, TextSettingType>>()

        override fun loadText(scope: SettingsScope): TextSettingsSnapshot {
            loadCalls += scope
            return snapshot(scope)
        }

        override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) = Unit
        override fun revert(scope: SettingsScope, type: TextSettingType) { revertCalls += scope to type }
        override fun reset(scope: SettingsScope) = Unit

        override fun loadColors(scope: SettingsScope): ColorsSnapshot = error("not used by the nav state")
        override fun loadBackgroundOptions(): List<BackgroundImageOption> = error("not used by the nav state")
        override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) = error("not used")
        override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) = error("not used")
        override fun setWorkspaceColor(scope: SettingsScope, argb: Int) = error("not used")
        override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) = error("not used")
        override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) = error("not used")
        override fun resetColors(scope: SettingsScope) = error("not used")
        override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? =
            error("not used")
        override fun deleteBackgroundImage(initials: String) = error("not used")

        fun loadsFor(scope: SettingsScope): Int = loadCalls.count { it == scope }
    }

    private val activeWorkspace = "active-workspace-id"
    private val windowScope = SettingsScope.Window(windowId = "win-1", workspaceId = "ws-1")

    /** What the host handed the graph for this scope; a fixture value no arm could guess. */
    private fun payloadFor(scope: SettingsScope) = "payload-for-$scope"

    private class Harness {
        val service = FakeService()
        val navigated = mutableListOf<String>()
        val applied = mutableListOf<Pair<String, TextDisplaySettingsController>>()
    }

    private fun navState(
        harness: Harness,
        initialScope: SettingsScope = SettingsScope.Window("win-1", "ws-1"),
        startedAtColors: Boolean = false,
    ): TextDisplaySettingsNavState {
        val session = TextDisplaySettingsSession(
            initialScope = initialScope,
            controllerFor = { scope, onNavigate ->
                TextDisplaySettingsController(
                    service = harness.service,
                    settingsScope = scope,
                    labels = TextDisplaySettingsLabels.forTest(),
                    onNavigateCallback = onNavigate,
                )
            },
            colorControllerFor = { error("the nav state never builds a colour controller") },
            hideLabelsPayload = { scope -> payloadFor(scope) },
            applyHideLabelsResult = { json, controller -> harness.applied += json to controller },
            resultOnLeave = { null },
        )
        return TextDisplaySettingsNavState(
            session = session,
            startedAtColors = startedAtColors,
            activeWorkspaceId = { activeWorkspace },
            navigateToHideLabels = { payload -> harness.navigated += payload },
        )
    }

    // ——— shouldCloseSearchOnBack ————————————————————————————————————————————————————————————————

    /**
     * The four cases of classic `TextDisplaySettingsComposeActivityBackTest`, which Task 13 deletes.
     * [searchActiveAwayFromTheListDoesNotCloseSearch] is the one that carries the defect: the search
     * state is hoisted onto the visit (so it outlives pushing colours/chooser, which render no search
     * UI at all), and an unconditional `searchActive` gate would `close()` an invisible search bar
     * and SWALLOW the press -- `pop()` never running, the user apparently stuck.
     *
     * Mutation these catch: `atListDestination && searchActive` -> `searchActive`.
     */
    @Test fun searchActiveAtTheListClosesSearch() {
        assertTrue(shouldCloseSearchOnBack(atListDestination = true, searchActive = true))
    }

    @Test fun searchInactiveAtTheListDoesNotCloseSearch() {
        assertFalse(shouldCloseSearchOnBack(atListDestination = true, searchActive = false))
    }

    @Test fun searchActiveAwayFromTheListDoesNotCloseSearch() {
        assertFalse(shouldCloseSearchOnBack(atListDestination = false, searchActive = true))
    }

    @Test fun searchInactiveAwayFromTheListDoesNotCloseSearch() {
        assertFalse(shouldCloseSearchOnBack(atListDestination = false, searchActive = false))
    }

    // ——— badgeLabel ————————————————————————————————————————————————————————————————————————————

    private fun stateWith(type: TextSettingType, inheritedFrom: InheritedFrom) =
        TextDisplaySettingsScreenState(
            title = "t",
            items = emptyList(),
            rows = mapOf(
                type to TextSettingRow(
                    type = type,
                    value = TextSettingRowValue.Bool(true),
                    inheritedFrom = inheritedFrom,
                    enabled = true,
                    visible = true,
                ),
            ),
        )

    /**
     * The whole of classic's `badgeLabel`, which had no test of its own on either side.
     *
     * The two `?.let`s that return null are not decoration: [KEY_OPEN_WORKSPACE_SETTINGS] and
     * [KEY_OPEN_GLOBAL_SETTINGS] reach this same lambda from the drill-up link rows and are NOT
     * [TextSettingType] names, so a `valueOf` without the `runCatching` throws while the screen is
     * drawing. Mutations these catch: swapping the two `InheritedFrom` arms; `InheritedFrom.NONE ->`
     * returning a label; dropping the `runCatching`.
     */
    @Test fun badgeLabelNamesTheScopeARowInheritsFrom() {
        assertEquals(
            "from workspace",
            badgeLabel(
                stateWith(TextSettingType.JUSTIFY, InheritedFrom.WORKSPACE),
                TextSettingType.JUSTIFY.name, "from workspace", "from global",
            ),
        )
        assertEquals(
            "from global",
            badgeLabel(
                stateWith(TextSettingType.JUSTIFY, InheritedFrom.GLOBAL),
                TextSettingType.JUSTIFY.name, "from workspace", "from global",
            ),
        )
    }

    @Test fun badgeLabelIsNullForARowSetAtThisScope() {
        assertNull(
            badgeLabel(
                stateWith(TextSettingType.JUSTIFY, InheritedFrom.NONE),
                TextSettingType.JUSTIFY.name, "from workspace", "from global",
            ),
        )
    }

    @Test fun badgeLabelIsNullForAKeyThatIsNotASettingType() {
        val state = stateWith(TextSettingType.JUSTIFY, InheritedFrom.WORKSPACE)
        assertNull(badgeLabel(state, KEY_OPEN_WORKSPACE_SETTINGS, "from workspace", "from global"))
        assertNull(badgeLabel(state, KEY_OPEN_GLOBAL_SETTINGS, "from workspace", "from global"))
        assertNull(
            badgeLabel(state, TextSettingType.MORPH.name, "from workspace", "from global"),
            "a row the snapshot does not carry has nothing to inherit from",
        )
    }

    // ——— the drill-up stack ————————————————————————————————————————————————————————————————————

    /**
     * Classic's `onNavigate` for the two parent links, including the `searchMode.close()` that is
     * easy to lose: the pushed scope is rendered fresh, and a query typed against the scope being
     * LEFT would silently filter a list it was never typed against.
     *
     * The workspace link resolves the id through `activeWorkspaceId()` at TAP time -- the host always
     * edits the active workspace -- so the fixture id here is deliberately not the window scope's own
     * `workspaceId`; a mutation that captured the scope's id instead would return "ws-1".
     */
    @Test fun theWorkspaceLinkPushesTheActiveWorkspaceAndClosesSearch() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        nav.searchMode.open()
        nav.onNavigate(windowScope, KEY_OPEN_WORKSPACE_SETTINGS)
        assertEquals(listOf(windowScope, SettingsScope.Workspace(activeWorkspace)), nav.navStack)
        assertFalse(nav.searchMode.active.value, "the pushed scope must be rendered with no query")
    }

    @Test fun theGlobalLinkPushesGlobalAndClosesSearch() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        nav.searchMode.open()
        nav.onNavigate(windowScope, KEY_OPEN_GLOBAL_SETTINGS)
        assertEquals(listOf(windowScope, SettingsScope.Global), nav.navStack)
        assertFalse(nav.searchMode.active.value)
    }

    /**
     * `pop()` off a drilled-up scope REFRESHES the one revealed underneath -- classic
     * `TextDisplaySettingsActivity.onBackPressed`'s `refreshFromInMemoryState`, because edits made at
     * the deeper scope change what the shallower one inherits. Mutation this catches: dropping the
     * `controllerFor(it).refresh()` line (the stack would still pop, and a source scan would still
     * pass, while every inherited-value badge underneath went stale).
     */
    @Test fun poppingADrilledUpScopeRefreshesTheOneUnderneath() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        nav.controllerFor(windowScope)
        assertEquals(1, h.service.loadsFor(windowScope))

        nav.onNavigate(windowScope, KEY_OPEN_GLOBAL_SETTINGS)
        assertTrue(nav.pop())

        assertEquals(listOf(windowScope), nav.navStack)
        assertEquals(
            2, h.service.loadsFor(windowScope),
            "the revealed scope's controller was not refreshed, so its inherited values are stale",
        )
    }

    /**
     * The colours sub-destination's own `pop()` arm, refresh included: colours are edited through a
     * SEPARATE controller, so the cached text controller for that scope can be showing a stale COLORS
     * inheritance badge by the time the user comes back.
     */
    @Test fun poppingTheColoursDestinationReturnsToTheListAndRefreshesIt() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        nav.controllerFor(windowScope)
        nav.onNavigate(windowScope, TextSettingType.COLORS.name)
        assertEquals(windowScope, nav.colorsScope)

        assertTrue(nav.pop())
        assertNull(nav.colorsScope)
        assertEquals(2, h.service.loadsFor(windowScope))
    }

    /** The background-image chooser sits on top of colours, so it pops first and reveals colours. */
    @Test fun theBackgroundImageChooserPopsBeforeTheColoursDestination() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        nav.onNavigate(windowScope, TextSettingType.COLORS.name)
        nav.selectBackgroundImageSlot(night = true)

        assertTrue(nav.pop())
        assertNull(nav.chooserNight)
        assertEquals(windowScope, nav.colorsScope, "popping the chooser must not also leave colours")
    }

    /**
     * `pop()`'s two terminal branches, which are the ONLY thing that tells the arm to leave. The
     * `startedAtColors` one is classic's own: a colours-originated entry has nothing underneath the
     * colours destination, so backing out of it must LEAVE rather than reveal a list the user never
     * opened. Mutation this catches: deleting the `colorsScope != null && startedAtColors` arm, which
     * makes the next arm run instead -- `pop()` returns true, the list appears, and the entry is
     * stuck one level deeper than it started.
     */
    @Test fun aColoursOriginatedEntryLeavesRatherThanRevealingAListItNeverOpened() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope, startedAtColors = true)
        assertEquals(windowScope, nav.colorsScope, "startAtColors opens AT the colours destination")
        assertFalse(nav.pop(), "there is nothing underneath a colours-originated entry")
        assertEquals(windowScope, nav.colorsScope, "a refused pop must not half-dismantle the state")
    }

    @Test fun popAtTheRootOfTheDrillUpStackLeaves() {
        val h = Harness()
        assertFalse(navState(h, initialScope = windowScope).pop())
    }

    /** Classic's `controllerCache`: one controller per scope, so a pop back reuses the instance
     *  holding the edits made while drilled deeper rather than re-reading the service. */
    @Test fun oneControllerPerScopeIsCached() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        assertSame(nav.controllerFor(windowScope), nav.controllerFor(windowScope))
        assertEquals(1, h.service.loadsFor(windowScope))
    }

    // ——— the Hide-labels round trip ————————————————————————————————————————————————————————————

    /**
     * Fix round 1's Critical 1. The row must PUSH the label manager with the host's payload -- not
     * `awaitIntent` the host's own `singleTop` self, which answered through `onNewIntent` and lost
     * the user's choice.
     *
     * Mutation this catches: reverting `navigateToHideLabels(session.hideLabelsPayload(scope))` to a
     * call that only applies a result (or navigating with something other than the scope's payload --
     * the fake answers per-scope, so a hard-coded or wrong-scope payload fails here).
     */
    @Test fun theHideLabelsRowPushesTheLabelManagerWithThisScopesPayload() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        nav.onNavigate(windowScope, TextSettingType.BOOKMARKS_HIDELABELS.name)
        assertEquals(listOf(payloadFor(windowScope)), h.navigated)
        assertTrue(nav.awaitingHideLabels, "the visit must remember that it is the one waiting")
    }

    /**
     * The answer reaches the controller of the scope that ASKED, and exactly once.
     *
     * Both halves matter. "Exactly once" because applying a label set twice is not idempotent -- the
     * second application rewrites the workspace's recent-labels list over whatever the user has done
     * since -- and the arm re-runs its collector on every entry into this destination. "The scope
     * that asked" because a deeper scope may have been pushed while the label manager was up; the
     * asking scope is remembered, never re-derived from `navStack`.
     *
     * Mutations these catch: dropping the `hideLabelsScope = null` reset (the second call would
     * apply again); resolving the controller from `navStack.last()` instead of the remembered scope.
     */
    @Test fun theLabelManagersAnswerIsAppliedOnceToTheScopeThatAsked() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        val asking = nav.controllerFor(windowScope)
        nav.onNavigate(windowScope, TextSettingType.BOOKMARKS_HIDELABELS.name)
        // A deeper scope is pushed while the label manager is up -- navStack.last() is no longer the
        // scope that asked.
        nav.onNavigate(windowScope, KEY_OPEN_GLOBAL_SETTINGS)

        assertTrue(nav.applyHideLabelsResult("""{"answer":1}"""))
        assertEquals(1, h.applied.size)
        assertEquals("""{"answer":1}""", h.applied.single().first)
        assertSame(asking, h.applied.single().second)

        assertFalse(nav.applyHideLabelsResult("""{"answer":1}"""), "a second apply must be a no-op")
        assertEquals(1, h.applied.size)
        assertFalse(nav.awaitingHideLabels)
    }

    /** Nothing asked, nothing applies -- the arm's gate against consuming a result that belongs to
     *  the bookmark list, whose graph shares this channel. */
    @Test fun anUnrequestedLabelResultIsNotApplied() {
        val h = Harness()
        val nav = navState(h, initialScope = windowScope)
        assertFalse(nav.awaitingHideLabels)
        assertFalse(nav.applyHideLabelsResult("""{"answer":1}"""))
        assertTrue(h.applied.isEmpty())
    }

    private companion object {
        /** A full 35-type snapshot; the controller reads `rows.getValue(...)` for every type. */
        fun snapshot(scope: SettingsScope): TextSettingsSnapshot {
            val choice = TextSettingRowValue.Choice("0", listOf(SettingsItem.Choice("0", "Off")))
            val numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt")
            val margins = TextSettingRowValue.Margins(3, 3, 170, 30, 30, 500, "3/3/170 mm")
            val rows = TextSettingType.entries.associateWith { t ->
                val value = when (t) {
                    TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
                    TextSettingType.SCROLL_HELPER_LINE_STYLE, TextSettingType.FONTFAMILY -> choice
                    TextSettingType.FONTSIZE, TextSettingType.TOPMARGIN,
                    TextSettingType.LINE_SPACING -> numeric
                    TextSettingType.MARGINSIZE -> margins
                    TextSettingType.COLORS -> TextSettingRowValue.ColorsNav("Colours")
                    TextSettingType.BOOKMARKS_HIDELABELS -> TextSettingRowValue.HideLabels("2 hidden")
                    else -> TextSettingRowValue.Bool(true)
                }
                TextSettingRow(t, value, InheritedFrom.NONE, enabled = true, visible = true)
            }
            return TextSettingsSnapshot(
                scope = scope,
                screenTitle = "Title",
                workspaceName = "My WS",
                rows = rows,
                showParentCategory = scope !is SettingsScope.Global,
                showWorkspaceLink = scope is SettingsScope.Window,
                showGlobalLink = scope !is SettingsScope.Global,
            )
        }
    }
}
