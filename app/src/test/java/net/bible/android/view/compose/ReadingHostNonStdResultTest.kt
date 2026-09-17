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

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import net.bible.android.BibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **Reading-host re-typing T8d: every request code classic answers, the flipped host answers too.**
 *
 * T8b gave `NavHostComposeActivity` a `STD_REQUEST_CODE` dispatch and T8c gave the reading
 * destination its in-graph collectors. Neither looked at the OTHER arms of classic
 * `MainBibleActivity.onActivityResult`, and the override T8b wrote began
 * `if (requestCode != ActivityBase.STD_REQUEST_CODE) return` — so three of the four request codes
 * classic answers went nowhere on the host that actually runs:
 *
 *  - `MainBibleActivity.WORKSPACE_CHANGED`, a REAL Activity result (`Screen.WorkspaceSelector` is
 *    not in `ScreenLauncher.MIGRATED`), silently discarded: the user picked a workspace, confirmed,
 *    and was put back in the one they left.
 *  - `IntentHelper.REFRESH_DISPLAY_ON_FINISH` and `.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH`, whose
 *    screens are destinations of this host's OWN graph — so the launch is a `singleTop` self-launch
 *    that produces no result at all, and the whole of classic's `preferenceSettingsChanged()` (the
 *    only production caller of `CommonUtils.changeAppIconAndName`, and the only producer of
 *    `SynchronizeWindowsEvent(true)` in the tree) simply stopped running.
 *
 * Every test below is red on the pre-T8d behaviour — restore the `requestCode != STD_REQUEST_CODE`
 * early return, or make `recordReadingReturnDebt` a no-op, and they fail rather than pass quietly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostNonStdResultTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var synchronizeWindows = 0

    /**
     * Classic's `REFRESH_DISPLAY_ON_FINISH` tail runs `MenuCommandHandler.restartIfRequiredOnReturn`
     * before the refresh, and that RESTARTS THE APP (`CommonUtils.restartApp` ends in
     * `exitProcess(2)`) when the UI locale in force differs from the one the process started with.
     * In the real app the two agree unless the user has just changed the locale; in this fixture
     * `BibleApplication.localeOverrideAtStartUp` is not the preference's value, so every one of
     * these tests would take the app down with it. Restoring the invariant is what makes the tests
     * exercise the refresh rather than the restart -- the restart branch is classic's, unchanged,
     * and is not what T8d is about.
     */
    @Before
    fun theLocaleInForceIsTheOneThisProcessStartedWith() {
        BibleApplication::class.java.getDeclaredField("localeOverrideAtStartUp")
            .apply { isAccessible = true }
            .set(BibleApplication.application, CommonUtils.localePref ?: "")
    }

    /**
     * …and the launcher alias already says what `discrete_mode` says.
     *
     * `CommonUtils.changeAppIconAndName()` — the privacy step of `preferenceSettingsChanged`, and
     * the one whose absence on the flipped host is the sharpest half of item 2 — ends in
     * `forceStopApp()` whenever it actually MOVES a component's enabled state, and `forceStopApp`
     * is `exitProcess(2)`. In a fresh Robolectric process both aliases read
     * `COMPONENT_ENABLED_STATE_DEFAULT`, so the first call would take the test JVM down with it
     * (it does: without this the whole class dies with "finished with non-zero exit value 2" and
     * no test failure at all). Pre-setting them to what `discrete_mode = false` means leaves
     * `changeAppIconAndName` with nothing to change, so it runs, reaches its `settingsChanged`
     * check, and returns — which is the state every real app has on all but the one settings return
     * that actually flips discrete mode. That the call is REACHED is pinned by
     * `ReadingHostAnsweredRequestCodeGuardTest.thePrivacyStepIsInTheSharedBody`; what it DOES is
     * classic's, unchanged, and is not T8d's to re-verify.
     */
    @Before
    fun theLauncherAliasAlreadyMatchesTheDiscreteModeSetting() {
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        pm.setComponentEnabledSetting(
            ComponentName(BuildConfig.APPLICATION_ID, "net.bible.android.activity.StartupActivity"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            ComponentName(BuildConfig.APPLICATION_ID, "net.bible.android.view.activity.Calculator"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
    }

    @After
    fun tearDown() {
        ABEventBus.unregister(this)
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /** A host on the reading route, composed: `.visible()` is what composes the destination. */
    private fun composedHost(state: Bundle? = null): ActivityController<NavHostComposeActivity> =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply { create(state).start().resume().visible() }

    private fun countSynchronizeWindows() {
        synchronizeWindows = 0
        ABEventBus.register(this) { on<SynchronizeWindowsEvent> { synchronizeWindows++ } }
    }

    private fun aSecondWorkspace(): WorkspaceEntities.Workspace =
        WorkspaceEntities.Workspace(name = "T8d target workspace").also {
            DatabaseContainer.instance.workspaceDb.workspaceDao().insertWorkspace(it)
        }

    private fun workspaceResult(workspaceId: String?, changed: Boolean) = Intent().apply {
        if (workspaceId != null) putExtra("workspaceId", workspaceId)
        putExtra("changed", changed)
    }

    // ——— item 1: the workspace switch the selector returns ————————————————————————————————————————

    /**
     * The headline defect. `WorkspaceSelectorComposeActivity` only `setResult`s — it posts no event,
     * and nothing else calls `switchToWorkspace` on that path — so the arm IS the switch.
     */
    @Test
    fun theWorkspaceTheSelectorReturnsIsSwitchedTo() {
        val controller = composedHost()
        val activity = controller.get()
        val target = aSecondWorkspace()
        assertNotEquals(
            target.id, activity.hostWindowRepository.id,
            "the fixture must not already be on the workspace this test switches to",
        )

        controller.pause()
        activity.onActivityResult(
            MainBibleActivity.WORKSPACE_CHANGED,
            Activity.RESULT_OK,
            workspaceResult(target.id.toString(), changed = false),
        )
        assertNotEquals(
            target.id, activity.hostWindowRepository.id,
            "the switch must be HELD: onActivityResult runs before onResume has reclaimed " +
                "windowControl's repository for this host",
        )

        controller.resume()
        assertEquals(
            target.id, activity.hostWindowRepository.id,
            "…and applied once this host has resumed. Before T8d the result hit the " +
                "`requestCode != STD_REQUEST_CODE` early return and the user was returned to the " +
                "workspace they had just left",
        )
    }

    /** A cancelled selector changes nothing — classic's arm is gated on `RESULT_OK`. */
    @Test
    fun aCancelledWorkspaceSelectorSwitchesNothing() {
        val controller = composedHost()
        val activity = controller.get()
        val target = aSecondWorkspace()
        val before = activity.hostWindowRepository.id

        controller.pause()
        activity.onActivityResult(
            MainBibleActivity.WORKSPACE_CHANGED,
            Activity.RESULT_CANCELED,
            workspaceResult(target.id.toString(), changed = true),
        )
        controller.resume()

        assertEquals(before, activity.hostWindowRepository.id)
    }

    // ——— item 2: the Settings return ——————————————————————————————————————————————————————————————

    /**
     * The whole of classic's `preferenceSettingsChanged()` runs when the graph comes back to
     * `reading` from a Settings screen this host launched at `REFRESH_DISPLAY_ON_FINISH`.
     *
     * Two independent observables, because two of the five steps are the ones that matter:
     * `SynchronizeWindowsEvent(true)`, which nothing else in the tree posts, and the composition
     * rebuild the reading view needs to re-read `toolbar_button_actions` and its three
     * inside-the-composition settings.
     */
    @Test
    fun theSettingsReturnRunsTheReadingViewsRefresh() {
        val controller = composedHost()
        val activity = controller.get()
        val host = requireNotNull(activity.readingCommands.composeReadingViewHost) {
            "the reading view must be composed for this test to observe its refresh"
        }
        val generationBefore = host.generationForTest.state.value
        countSynchronizeWindows()

        activity.startActivityForResult(
            ScreenLauncher.intentFor(activity, Screen.Settings),
            IntentHelper.REFRESH_DISPLAY_ON_FINISH,
        )
        activity.applyReadingReturnDebts(NavRoutes.READING)

        assertEquals(
            1, synchronizeWindows,
            "returning from Settings must post SynchronizeWindowsEvent(true) — nothing else in the " +
                "tree posts it, so without this arm window synchronisation never reconciles after a " +
                "settings change",
        )
        assertTrue(
            host.generationForTest.state.value > generationBefore,
            "…and must rebuild the reading view's composition, which is what re-reads " +
                "toolbar_button_actions, hide_bible_reference_overlay, hide_window_buttons and " +
                "full_screen_hide_buttons_pref",
        )
    }

    /** The gate: a return to `reading` that was owed nothing must do nothing. */
    @Test
    fun aReturnToReadingOwedNothingRefreshesNothing() {
        val activity = composedHost().get()
        countSynchronizeWindows()

        activity.applyReadingReturnDebts(NavRoutes.READING)

        assertEquals(0, synchronizeWindows, "an ordinary navigation back to reading owes nothing")
    }

    /** …and a debt is spent exactly once, however often the graph returns to `reading`. */
    @Test
    fun aDebtIsSpentOnceAndOnlyOnce() {
        val activity = composedHost().get()
        activity.startActivityForResult(
            ScreenLauncher.intentFor(activity, Screen.Settings),
            IntentHelper.REFRESH_DISPLAY_ON_FINISH,
        )
        countSynchronizeWindows()

        activity.applyReadingReturnDebts(NavRoutes.READING)
        activity.applyReadingReturnDebts(NavRoutes.READING)

        assertEquals(1, synchronizeWindows, "the record must be cleared as it is claimed")
    }

    /** A landing on some OTHER destination is not the answer this debt is waiting for. */
    @Test
    fun aDebtIsNotSpentOnADestinationThatIsNotTheReadingView() {
        val activity = composedHost().get()
        activity.startActivityForResult(
            ScreenLauncher.intentFor(activity, Screen.Settings),
            IntentHelper.REFRESH_DISPLAY_ON_FINISH,
        )
        countSynchronizeWindows()

        activity.applyReadingReturnDebts(NavRoutes.SETTINGS)

        assertEquals(0, synchronizeWindows)
        // …and it is still owed, so the real return still pays it.
        activity.applyReadingReturnDebts(NavRoutes.READING)
        assertEquals(1, synchronizeWindows)
    }

    /**
     * **The privacy feature, and the reason the debt has to survive a `recreate()`.** Writing
     * `discrete_mode` in Settings is one of `RECREATE_ON_CHANGE_KEYS`, so the host is recreated —
     * and `recreate()` does not swap the launcher alias. `CommonUtils.changeAppIconAndName()`, whose
     * only production caller is this body, is what does; a debt lost across the recreate is the app
     * icon and name staying visible after the user asked for them to be hidden.
     */
    @Test
    fun theSettingsDebtSurvivesARecreate() {
        val controller = composedHost()
        controller.get().startActivityForResult(
            ScreenLauncher.intentFor(controller.get(), Screen.Settings),
            IntentHelper.REFRESH_DISPLAY_ON_FINISH,
        )
        val state = Bundle()
        controller.saveInstanceState(state)
        assertTrue(
            state.containsKey("nav_reading_return_debts"),
            "what the reading view is owed must be written into the instance state",
        )

        countSynchronizeWindows()
        val recreated = composedHost(state).get()
        recreated.applyReadingReturnDebts(NavRoutes.READING)

        assertEquals(
            1, synchronizeWindows,
            "a recreate()d host must still pay what the pre-recreate host was owed",
        )
    }

    /**
     * A launch that is NOT a self-launch records nothing: that one produces a real Activity result,
     * and recording a debt as well would apply the refresh twice.
     */
    @Test
    fun aLaunchAtTheSameCodeAimedElsewhereRecordsNoDebt() {
        val activity = composedHost().get()
        countSynchronizeWindows()

        activity.startActivityForResult(
            ScreenLauncher.intentFor(activity, Screen.TextDisplaySettings),
            IntentHelper.REFRESH_DISPLAY_ON_FINISH,
        )
        activity.applyReadingReturnDebts(NavRoutes.READING)

        assertEquals(0, synchronizeWindows)
    }

    /**
     * …and when such a launch DOES come back as a real Activity result — the case where this host
     * was not top of its task and the platform made a second instance — the same shared body runs,
     * deferred to `onResume` exactly as every other held result is.
     */
    @Test
    fun aRealActivityResultAtTheRefreshCodeRunsTheSameRefresh() {
        val controller = composedHost()
        val activity = controller.get()
        countSynchronizeWindows()

        controller.pause()
        activity.onActivityResult(IntentHelper.REFRESH_DISPLAY_ON_FINISH, Activity.RESULT_OK, null)
        assertEquals(0, synchronizeWindows, "held until onResume, like every other Activity result")

        controller.resume()
        assertEquals(1, synchronizeWindows)
    }

    // ——— item 3: the Download return ——————————————————————————————————————————————————————————————

    /**
     * Classic's `UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH` arm is `updateActions()` and nothing else —
     * on this host, `onToolbarStateMayHaveChanged()`, which rebuilds the drawer and refreshes the
     * toolbar state but does NOT rebuild the composition. So the discriminating observation is that
     * this debt is spent (the generation is the one thing `preferenceSettingsChanged` bumps and this
     * arm must not).
     */
    @Test
    fun theDownloadReturnRefreshesTheToolbarWithoutRebuildingTheComposition() {
        val activity = composedHost().get()
        val host = requireNotNull(activity.readingCommands.composeReadingViewHost)
        val generationBefore = host.generationForTest.state.value
        countSynchronizeWindows()

        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.download()),
            IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH,
        )
        activity.applyReadingReturnDebts(NavRoutes.READING)

        assertEquals(
            generationBefore, host.generationForTest.state.value,
            "classic's arm for this code is updateActions(), not preferenceSettingsChanged()",
        )
        assertEquals(
            0, synchronizeWindows,
            "…and it posts no SynchronizeWindowsEvent either — that belongs to the Settings arm",
        )
        // The debt really was recorded and really was spent: a second return finds nothing owed,
        // and the Settings arm's own observable proves the ledger is not simply inert.
        activity.startActivityForResult(
            ScreenLauncher.intentFor(activity, Screen.Settings),
            IntentHelper.REFRESH_DISPLAY_ON_FINISH,
        )
        activity.applyReadingReturnDebts(NavRoutes.READING)
        assertEquals(1, synchronizeWindows)
    }
}
