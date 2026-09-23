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
package net.bible.android.view.compose

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.settings.buildBackgroundImageChooserLabels
import net.bible.android.view.activity.settings.buildColorSettingsLabels
import net.bible.android.view.activity.settings.buildTextDisplayScreenLabels
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedcore.workspaces.WorkspaceSelectorController
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.NavSessionMemo
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsDeps
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsNavState
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsSession
import net.bible.sharedui.workspaces.nav.WorkspaceNavDeps
import net.bible.sharedui.workspaces.nav.WorkspaceSelectorDeps
import net.bible.sharedui.workspaces.nav.workspaceNavGraph
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The workspace cluster's two arms, driven over a real `NavHost` and a real [NavResultChannel] --
 * the twin of [ChooserInGraphResultTest], and it exists for the same reason: `NavResultChannelGuardTest`
 * proves the graph SOURCE contains the right calls by scanning its text, which cannot tell you that a
 * result survives a real back stack, that a session survives a child destination, or that a pending
 * result is applied once rather than on every recomposition.
 *
 * **Written in nav-graph slice 7 Task 5's fix round 1, for two Critical defects that a source scan
 * could not have seen** (both live in a build of the day Task 5 landed, since the host's
 * global-text-settings row reaches `settings/textDisplay` today):
 *
 * - the Hide-labels row `awaitIntent`ed the host's own `singleTop` self, so the user's label choice
 *   was dropped and published into a channel nothing here collected
 *   ([theHideLabelsRowPushesTheLabelManagerAndItsAnswerReachesTheController]);
 * - the editor's whole working set was a plain `remember(args)`, so anything pushed on top of it
 *   silently reset the edit and leaving then published NOTHING
 *   ([theEditorsSessionSurvivesAChildDestination], [aDetachedEditSurvivesTheLabelManagerAndReachesTheSelector]).
 *
 * Design §1.1 is pinned throughout, as in the chooser twin: both channels' host-side `exitWithResult`
 * is a hard error in production, so every test asserts [channelExits] stayed at zero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceInGraphResultTest {

    /** An ANDROID rule: the arms' `PlatformBackHandler`s need a real `onBackPressedDispatcher`. */
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var workspaceResults: NavResultChannel<WorkspaceResult>
    private lateinit var textSettingsResults: NavResultChannel<TextSettingsResult>
    private lateinit var manageLabelsResults: NavResultChannel<ManageLabelsResult>

    /** How often ANY channel took its `exitWithResult` branch. Design §1.1: must stay 0. */
    private var channelExits = 0
    private var exitHostCalls = 0

    // — what the arms asked the host for, captured —
    private val builtSessions = mutableListOf<FakeEdit>()
    private val hideLabelsPayloadsBuilt = mutableListOf<SettingsScope>()
    private val appliedLabelResults = mutableListOf<Pair<String, TextDisplaySettingsController>>()
    private var lastTextOnNavigate: ((String) -> Unit)? = null
    private var selectorController: WorkspaceSelectorController? = null

    /**
     * Stands in for `DetachedWorkspaceEdit`: one per SESSION, so "the arm rebuilt the session"
     * is observable as "the edit went back to unchanged". That is the whole of Critical 2 --
     * a fixture that shared one edit across sessions could not fail for it.
     */
    private class FakeEdit(val bundleJson: String) {
        var changed = false
    }

    // ——— fakes ——————————————————————————————————————————————————————————————————————————————————

    private class FakeTextService : TextDisplaySettingsService {
        override fun loadText(scope: SettingsScope) = snapshot(scope)
        override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) = Unit
        override fun revert(scope: SettingsScope, type: TextSettingType) = Unit
        override fun reset(scope: SettingsScope) = Unit
        override fun loadColors(scope: SettingsScope): ColorsSnapshot = error("not used")
        override fun loadBackgroundOptions(): List<BackgroundImageOption> = error("not used")
        override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) = error("not used")
        override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) = error("not used")
        override fun setWorkspaceColor(scope: SettingsScope, argb: Int) = error("not used")
        override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) = error("not used")
        override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) = error("not used")
        override fun resetColors(scope: SettingsScope) = error("not used")
        override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? =
            error("not used")
        override fun deleteBackgroundImage(initials: String) = error("not used")
    }

    /** Enough [WorkspaceService] for a real [WorkspaceSelectorController]; every call the selector
     *  round trip makes is recorded, `applyWorkspaceSettings` above all. */
    private class FakeWorkspaceService : WorkspaceService {
        val rows = mutableListOf(WorkspaceRowVd("ws-1", "Workspace one", null, 0x444444, true))
        val applied = mutableListOf<Triple<String, String, Boolean>>()

        override fun currentWorkspaceId() = "ws-1"
        override fun saveCurrentIntoDb() = Unit
        override fun loadAll() = rows.toList()
        override fun createWorkspace(name: String) = WorkspaceRowVd("new", name, null, 0x444444, false)
        override fun cloneWorkspace(sourceId: String, name: String) =
            WorkspaceRowVd("clone", name, null, 0x444444, false)
        override fun applyChanges(
            orderedIds: List<String>,
            deletedIds: List<String>,
            renamed: Map<String, String>,
            changedIds: Set<String>,
        ) = Unit
        override fun deleteCreated(ids: List<String>) = Unit
        override fun settingTypeLabels(sourceId: String) = listOf("Font")
        override fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>) = emptyList<WorkspaceRowVd>()
        override fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>) = Unit
        override fun settingsBundleJson(id: String) = BUNDLE_JSON
        override fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd {
            applied += Triple(id, settingsBundleJson, reset)
            return rows.first()
        }
    }

    private val textService = FakeTextService()
    private val workspaceService = FakeWorkspaceService()

    private fun deps(workspaceIdOf: (String) -> String = { "ws-1" }): WorkspaceNavDeps {
        val context: Context = ApplicationProvider.getApplicationContext()
        workspaceResults = NavResultChannel { channelExits++ }
        textSettingsResults = NavResultChannel { channelExits++ }
        manageLabelsResults = NavResultChannel { channelExits++ }
        return WorkspaceNavDeps(
            exitHost = { exitHostCalls++ },
            setWindowTitle = {},
            workspaceResults = workspaceResults,
            textSettingsResults = textSettingsResults,
            manageLabelsResults = manageLabelsResults,
            workspaceSelector = WorkspaceSelectorDeps(
                controllerFor = { onResult, onCancel, onEditSettings ->
                    // The host MEMOISES this; reproduced here, because a per-call factory would make
                    // the selector's own survival across the editor untestable.
                    selectorController ?: WorkspaceSelectorController(
                        service = workspaceService,
                        scope = CoroutineScope(Dispatchers.Main),
                        onResult = { id, changed -> selectorController = null; onResult(id, changed) },
                        onCancel = { selectorController = null; onCancel() },
                        onEditSettings = onEditSettings,
                    ).also { it.load(); selectorController = it }
                },
                title = "Workspaces",
                settingsBundleJson = { workspaceService.settingsBundleJson(it) },
                workspaceIdOf = workspaceIdOf,
                onHelp = {},
            ),
            textDisplaySettings = TextDisplaySettingsDeps(
                sessionFor = { args -> sessionFor(args) },
                navStateMemo = NavSessionMemo<TextDisplaySettingsArgs, TextDisplaySettingsNavState>(),
                windowTitle = "Text options",
                activeWorkspaceId = { "ws-1" },
                screenLabels = { buildTextDisplayScreenLabels(context) },
                colorSettingsLabels = { buildColorSettingsLabels(context) },
                backgroundImageChooserLabels = { buildBackgroundImageChooserLabels(context) },
                inheritedFromWorkspace = "Workspace",
                inheritedFromGlobal = "Global",
                thumbnailFor = { null },
            ),
        )
    }

    /**
     * The host's `textDisplaySettingsSessionFor`, in fixture form. Each call makes a FRESH [FakeEdit],
     * exactly as the real one makes a fresh `DetachedWorkspaceEdit` out of the route's JSON -- which
     * is what makes "the session was rebuilt" fail a test instead of passing silently.
     */
    private fun sessionFor(args: TextDisplaySettingsArgs): TextDisplaySettingsSession {
        val edit = args.settingsBundleJson?.let { FakeEdit(it) }
        if (edit != null) builtSessions += edit
        return TextDisplaySettingsSession(
            initialScope = SettingsScope.Workspace("ws-1"),
            controllerFor = { scope, onNavigate ->
                lastTextOnNavigate = onNavigate
                TextDisplaySettingsController(
                    service = textService,
                    settingsScope = scope,
                    labels = TextDisplaySettingsLabels.forTest(),
                    onNavigateCallback = onNavigate,
                )
            },
            colorControllerFor = { error("colours are not exercised here") },
            hideLabelsPayload = { scope -> hideLabelsPayloadsBuilt += scope; LABEL_PAYLOAD },
            applyHideLabelsResult = { json, controller -> appliedLabelResults += json to controller },
            resultOnLeave = {
                edit?.takeIf { it.changed }
                    ?.let { TextSettingsResult(settingsBundleJson = it.bundleJson, reset = false) }
            },
        )
    }

    // ——— harness ————————————————————————————————————————————————————————————————————————————————

    private fun setGraph(d: WorkspaceNavDeps, startDestination: String = NavRoutes.READING) {
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        // Task 9's destination, as a stand-in -- nothing routes to the selector yet.
                        composable(NavRoutes.READING) { Text("reading") }
                        // The label manager belongs to BookmarkNavGraph, which this test does not
                        // register; a stand-in is enough, because what is under test is that the
                        // text-settings arm PUSHES it and collects its channel, not what it draws.
                        composable(
                            route = NavRoutes.MANAGE_LABELS_PATTERN,
                            arguments = listOf(
                                navArgument(NavRoutes.ARG_MANAGE_LABELS_DATA) {
                                    type = NavType.StringType; nullable = true; defaultValue = null
                                },
                            ),
                        ) { Text("labels") }
                        workspaceNavGraph(navController, d)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    private fun navigateTo(route: String) {
        compose.runOnIdle { navController.navigate(route) }
        compose.waitForIdle()
    }

    private fun popBack() {
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()
    }

    /** A real system back press, so the arm's `PlatformBackHandler` is what decides. */
    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun detachedRoute() = NavRoutes.textDisplaySettings(settingsBundle = BUNDLE_JSON)

    // ——— Critical 2: the session outlives a child destination ————————————————————————————————————

    /**
     * **The regression test for Critical 2.** Open a detached edit, change something, push a child on
     * top, come back, and leave: the change must still be there, so leaving publishes a result.
     *
     * Against the pre-fix code (`remember(args) { TextDisplaySettingsNavState(deps.sessionFor(args), ...) }`,
     * no memo) the arm's composition is disposed while the child is on top, the session is rebuilt
     * from the route JSON on the way back with `changed == false`, and `resultOnLeave()` returns null
     * -- so the edit vanishes with no error anywhere. Both assertions below fail in that world: the
     * session count is 2, and nothing is published.
     */
    @Test
    fun theEditorsSessionSurvivesAChildDestination() {
        setGraph(deps())
        navigateTo(detachedRoute())
        assertEquals(1, builtSessions.size)
        builtSessions.single().changed = true

        navigateTo(NavRoutes.manageLabels(LABEL_PAYLOAD))
        popBack()

        assertEquals(
            1, builtSessions.size,
            "the editor's session was rebuilt while a child was on top -- every unsaved edit it held is gone",
        )
        pressBack()

        val delivered = textSettingsResults.consume()
        assertNotNull(delivered, "an edit made before the child destination was never published")
        assertEquals(BUNDLE_JSON, delivered.settingsBundleJson)
        assertEquals(0, channelExits)
    }

    /**
     * The other half of the memo's contract: a FINISHED visit must not be resumable. Without the
     * `drop()` in `leave()`, re-entering the route would reopen the session that has already
     * delivered -- and leaving it again would deliver the same edit a second time.
     *
     * Mutation this catches: deleting `d.navStateMemo.drop()` from `leave()`'s delivering branch.
     */
    @Test
    fun leavingWithAResultDropsTheSessionSoAReEntryStartsFresh() {
        setGraph(deps())
        navigateTo(detachedRoute())
        builtSessions.single().changed = true
        pressBack()
        assertNotNull(textSettingsResults.consume())

        navigateTo(detachedRoute())
        assertEquals(2, builtSessions.size, "a finished visit was resumed instead of starting fresh")
        assertEquals(0, channelExits)
    }

    /**
     * The SAME drop on the other branch -- a visit that publishes nothing (the unedited detached
     * open, plan D3) still ends. Mutation this catches: dropping the memo only where a result is
     * delivered, which is the easy half to remember.
     */
    @Test
    fun leavingWithNoResultDropsTheSessionToo() {
        setGraph(deps())
        navigateTo(detachedRoute())
        assertEquals(1, builtSessions.size)
        pressBack()
        assertNull(textSettingsResults.consume(), "plan D3: an unedited open publishes nothing")
        assertEquals(NavRoutes.READING, currentRoute)

        navigateTo(detachedRoute())
        assertEquals(2, builtSessions.size, "a finished visit was resumed instead of starting fresh")
    }

    // ——— Critical 1: the Hide-labels round trip ——————————————————————————————————————————————————

    /**
     * **The regression test for Critical 1.** The row must push the label manager INSIDE the graph
     * and collect its answer from the channel.
     *
     * Pre-fix, the arm called a host lambda that did `awaitIntent(intentFor(this, manageLabels))` --
     * a `startActivityForResult` at the host's own `singleTop` self. Nothing in the graph moved, so
     * the first assertion (`currentRoute` is the label manager) fails outright; and the answer was
     * published into `manageLabelsResults` with no collector in this arm, so the last one fails too.
     *
     * The row is driven through the controller's own `onNavigateCallback`, which is the exact lambda
     * the screen's Hide-labels row calls.
     */
    @Test
    fun theHideLabelsRowPushesTheLabelManagerAndItsAnswerReachesTheController() {
        setGraph(deps())
        navigateTo(detachedRoute())
        val onNavigate = assertNotNull(lastTextOnNavigate)

        compose.runOnUiThread { onNavigate(TextSettingType.BOOKMARKS_HIDELABELS.name) }
        compose.waitForIdle()

        assertEquals(listOf<SettingsScope>(SettingsScope.Workspace("ws-1")), hideLabelsPayloadsBuilt.toList())
        assertEquals(NavRoutes.MANAGE_LABELS_PATTERN, currentRoute)
        assertEquals(0, exitHostCalls, "the round trip must not leave the host")

        compose.runOnUiThread {
            manageLabelsResults.deliver(navController, ManageLabelsResult(LABEL_ANSWER))
        }
        compose.waitForIdle()

        assertEquals(NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN, currentRoute)
        assertEquals(listOf(LABEL_ANSWER), appliedLabelResults.map { it.first })
        assertNull(manageLabelsResults.pending.value, "a consumed result must be cleared")
        assertEquals(0, channelExits)
    }

    /**
     * Once-only consumption, made visible: the arm's collector re-runs on every entry into this
     * destination, so a consumer that did not CLEAR the channel would re-apply the same label set
     * each time. Applying twice is not idempotent -- the second application rewrites the workspace's
     * recent-labels list over whatever the user has done since.
     *
     * Mutation this catches: `consume()` -> `pending.value`.
     */
    @Test
    fun aLabelAnswerIsAppliedOnceEvenAfterTheArmIsRecomposedAndReEntered() {
        setGraph(deps())
        navigateTo(detachedRoute())
        compose.runOnUiThread { lastTextOnNavigate!!(TextSettingType.BOOKMARKS_HIDELABELS.name) }
        compose.waitForIdle()
        compose.runOnUiThread { manageLabelsResults.deliver(navController, ManageLabelsResult(LABEL_ANSWER)) }
        compose.waitForIdle()
        assertEquals(1, appliedLabelResults.size)

        // Leave and come back: a fresh composition of the same arm, which re-runs the collector.
        pressBack()
        navigateTo(detachedRoute())
        assertEquals(1, appliedLabelResults.size, "the label answer was applied a second time")
    }

    /**
     * The gate that makes sharing `manageLabelsResults` with `BookmarkNavGraph` safe: a result this
     * visit did not ask for is left in the channel for whoever did. Without
     * `TextDisplaySettingsNavState.awaitingHideLabels`, this arm would consume (and silently drop)
     * the bookmark list's own pending result.
     *
     * The label manager is entered here WITHOUT going through the Hide-labels row, which is exactly
     * the shape of "some other destination asked".
     */
    @Test
    fun aLabelAnswerThisVisitDidNotAskForIsLeftInTheChannel() {
        setGraph(deps())
        navigateTo(detachedRoute())
        navigateTo(NavRoutes.manageLabels(LABEL_PAYLOAD))

        compose.runOnUiThread { manageLabelsResults.deliver(navController, ManageLabelsResult(LABEL_ANSWER)) }
        compose.waitForIdle()

        assertTrue(appliedLabelResults.isEmpty(), "an unrequested label answer must not be applied")
        assertEquals(
            LABEL_ANSWER, manageLabelsResults.pending.value?.data,
            "the answer was consumed by an arm that never asked for it",
        )
    }

    // ——— the in-cluster round trip ————————————————————————————————————————————————————————————————

    /**
     * The whole of design §6.2 in one test, and the reason these two destinations migrated together:
     * selector -> detached edit -> label manager -> back -> leave -> the selector applies the edit.
     *
     * It is also the end-to-end form of both Criticals: the label manager is a REAL child of the
     * editor here, so the session that carries the detached edit has to survive it, and the selector's
     * own memoised controller has to survive the editor. Exactly one `applyWorkspaceSettings` --
     * `consume()` clears the channel, so a recomposition cannot apply the same edit twice.
     */
    @Test
    fun aDetachedEditSurvivesTheLabelManagerAndReachesTheSelector() {
        setGraph(deps(), startDestination = NavRoutes.WORKSPACE_SELECTOR)
        val selector = assertNotNull(selectorController)

        navigateTo(detachedRoute())
        compose.runOnUiThread { lastTextOnNavigate!!(TextSettingType.BOOKMARKS_HIDELABELS.name) }
        compose.waitForIdle()
        compose.runOnUiThread { manageLabelsResults.deliver(navController, ManageLabelsResult(LABEL_ANSWER)) }
        compose.waitForIdle()

        builtSessions.single().changed = true
        pressBack()

        assertEquals(NavRoutes.WORKSPACE_SELECTOR, currentRoute)
        assertSame(selector, selectorController, "the selector's working set was thrown away")
        assertEquals(
            listOf(Triple("ws-1", BUNDLE_JSON, false)), workspaceService.applied,
            "the detached edit did not reach the selector exactly once",
        )
        assertNull(textSettingsResults.pending.value)
        assertEquals(0, channelExits)
        assertEquals(0, exitHostCalls)
    }

    /**
     * Slice 8 B8 (spec §3.5, parent spec §11.4): the graph pair's answer to the Activity pair's
     * process-death reasoning. The workspace id must be read back out of the RETURNED JSON
     * (`WorkspaceNavDeps.workspaceSelector.workspaceIdOf`), never from host-side state, because after
     * process death there IS no host-side state: the selector's memoised controller, the editor's
     * session memo and both channels are gone; only the saved back stack comes back.
     *
     * Green by construction on this tree (the arm already reads the JSON). Proven able to fail by
     * mutating `WorkspaceNavGraph.kt`'s `id = d.workspaceIdOf(result.settingsBundleJson)` to
     * `id = "ws-1"` -- see this task's commit message.
     */
    @Test
    fun aDetachedEditSurvivesTheHostProcessDyingWhileTheEditorIsOnScreen() {
        val jsonId: (String) -> String = { json -> Regex("\"workspaceId\":\"([^\"]+)\"").find(json)!!.groupValues[1] }
        var current = deps(workspaceIdOf = jsonId)
        val restorer = StateRestorationTester(compose)
        restorer.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = NavRoutes.READING) {
                        composable(NavRoutes.READING) { Text("reading") }
                        composable(
                            route = NavRoutes.MANAGE_LABELS_PATTERN,
                            arguments = listOf(
                                navArgument(NavRoutes.ARG_MANAGE_LABELS_DATA) {
                                    type = NavType.StringType; nullable = true; defaultValue = null
                                },
                            ),
                        ) { Text("labels") }
                        workspaceNavGraph(navController, current)
                    }
                }
            }
        }
        compose.waitForIdle()
        navigateTo(NavRoutes.WORKSPACE_SELECTOR)
        navigateTo(NavRoutes.textDisplaySettings(settingsBundle = OTHER_BUNDLE_JSON))
        assertEquals(NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN, currentRoute)

        // Process death: every host-held object goes; only the saved back stack survives.
        selectorController = null
        builtSessions.clear()
        current = deps(workspaceIdOf = jsonId)
        restorer.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN, currentRoute, "the back stack must come back on the editor")
        val edit = builtSessions.single()
        assertEquals(OTHER_BUNDLE_JSON, edit.bundleJson, "the editor rebuilds its detached edit from the ROUTE's bundle")
        edit.changed = true
        pressBack()

        assertEquals(NavRoutes.WORKSPACE_SELECTOR, currentRoute)
        assertEquals(
            listOf(Triple("ws-9", OTHER_BUNDLE_JSON, false)),
            workspaceService.applied,
            "the id comes out of the returned JSON (ws-9), not out of anything the dead host held",
        )
        assertEquals(0, channelExits)
    }

    /** The selector's own channel, for completeness: a Save delivers in-graph and never exits. */
    @Test
    fun theSelectorDeliversItsResultInGraph() {
        setGraph(deps())
        navigateTo(NavRoutes.WORKSPACE_SELECTOR)
        compose.runOnUiThread { selectorController!!.save() }
        compose.waitForIdle()

        val result = assertNotNull(workspaceResults.consume())
        assertNull(result.workspaceId, "a plain Save selects no workspace")
        assertTrue(result.changed)
        assertEquals(0, channelExits)
        assertFalse(currentRoute == NavRoutes.WORKSPACE_SELECTOR)
    }

    private companion object {
        const val BUNDLE_JSON = """{"workspaceId":"ws-1","fixture":true}"""
        const val OTHER_BUNDLE_JSON = """{"workspaceId":"ws-9","fixture":"other"}"""
        const val LABEL_PAYLOAD = """{"mode":"HIDELABELS","fixture":"payload"}"""
        const val LABEL_ANSWER = """{"mode":"HIDELABELS","fixture":"answer"}"""

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
