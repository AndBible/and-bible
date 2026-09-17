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
package net.bible.android.control.page

import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.service.download.FakeBookFactory
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading-host re-typing T8a item 1: `CurrentGeneralBookPage.startKeyChooser` opened with
 * `if(context !is MainBibleActivity) return`, so on `NavHostComposeActivity` a general-book /
 * StudyPad / multi-document key-chooser tap was a **silent no-op** — no log line, no crash, a dead
 * button. Three of the four callers (`BibleJavascriptInterface`'s `CtrlKeyB`,
 * `ReadingCommands.composeStartKeyChooser`'s fall-through tail, `CurrentPageManager`'s auto-open)
 * reach it with an `ActivityBase` that is whatever host is live, so the defect was reachable from
 * the composed reading view the moment R8 made the `reading` destination real.
 *
 * **What the check actually was.** `b718fe85c` (2022) widened the parameter from
 * `MainBibleActivity` to `ActivityBase` and added the early return in the same commit — it is a
 * TYPE artefact, not a policy: the body's one Activity-specific read is
 * `context.workspaceSettings`, which `MainBibleActivity` declares (`:400`) and `ActivityBase` does
 * not. Every sibling page (`CurrentBiblePage`, `CurrentDictionaryPage`, `CurrentMapPage`,
 * `CurrentCommentaryPage`) has always run its own `startKeyChooser` against ANY `ActivityBase`.
 *
 * **The fix re-homes the read onto the page's own window** —
 * `pageManager.window.windowRepository.workspaceSettings` — so nothing in the body needs a reading
 * host at all and no replacement check is needed. That spelling is also the one the batch's
 * repository-identity finding (R6c1/R6d) demands: the settings a key chooser seeds and writes back
 * belong to the workspace of the WINDOW whose page this is, which is unambiguous even with two live
 * hosts, whereas `windowControl.windowRepository` is whichever host resumed last.
 *
 * Driven against the REAL [NavHostComposeActivity] on the reading route, the same idiom
 * `ReadingDestinationInGraphTest` uses: a probe host would not be able to show that the no-op was
 * about the Activity's TYPE.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CurrentGeneralBookPageKeyChooserTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /**
     * `.create()` and no further: `bootstrapIfNeeded()` runs in `onCreate`, which is what gives the
     * host its own `WindowRepository`, and nothing here needs the destination to compose — the
     * subject is a page method that takes the Activity, not the reading view.
     */
    private fun host(): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    ).also { controllers += it }.create().get()

    private fun generalBookPageOf(activity: NavHostComposeActivity): CurrentGeneralBookPage =
        activity.hostWindowRepository.activeWindow.pageManager.currentGeneralBook

    /** `startKeyChooser` dispatches on `lifecycleScope.launch(Dispatchers.Main)`. */
    private fun drainMain() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * The plain general-book arm (`else ->`): the key chooser screen must actually start.
     *
     * Before the fix this asserted nothing started at all — the whole coroutine returned on line
     * one. Mutation: restore `if(context !is MainBibleActivity) return` and this fails with a null
     * started Intent.
     */
    @Test
    fun aGeneralBookKeyChooserStartsOnAHostThatIsNotMainBibleActivity() {
        val activity = host()
        generalBookPageOf(activity).startKeyChooser(activity)
        drainMain()

        val started = assertNotNull(
            shadowOf(activity).nextStartedActivityForResult,
            "a general-book key-chooser tap on the nav host must start the chooser — it was a " +
                "silent no-op, which is the failure shape this batch rules against",
        )
        // `Screen.ChooseGeneralBookKey` is NOT in `ScreenLauncher.MIGRATED`, so this arm still
        // starts the standalone chooser Activity — exactly as it does from classic. What T8a fixes
        // is that the arm RUNS at all; which screen it opens is unchanged.
        assertTrue(
            started.intent.component?.className.orEmpty().contains("ChooseGeneralBookKey"),
            "expected the general-book key chooser; started: ${started.intent}",
        )
    }

    /**
     * The StudyPad arm — the ONLY arm that reads workspace settings, and therefore the only one the
     * `!is MainBibleActivity` check ever had a type reason to exist for.
     *
     * Asserts the IDENTITY of the repository the settings come from, not merely that something
     * started: `autoAssignPrimaryLabel` is set on the repository that owns this page's window, and
     * must appear in the `ManageLabels` route the branch builds.
     *
     * **`windowControl` is pointed at a different repository first, and that is what gives the
     * assertion teeth** (fix round 1, review Minor 2b). Without it the assertion could not tell the
     * window's repository from `windowControl`'s: `createWindowRepository()` ends with
     * `windowControl.windowRepository = windowRepository`, so in a one-host fixture all three are
     * the same object and a `windowControl.windowRepository.workspaceSettings` implementation would
     * pass. With a foreign repository published there — the two-live-hosts state R6c1/R6d's identity
     * finding is about — only a body that reads through the page's OWN window sees the marker.
     */
    @Test
    fun theStudyPadArmSeedsManageLabelsFromTheOwningWindowsWorkspaceSettings() {
        val activity = host()
        val marker = IdType()
        activity.hostWindowRepository.workspaceSettings.autoAssignPrimaryLabel = marker
        val foreign = WindowRepository(activity.lifecycleScope)
        assertNotEquals(
            marker, foreign.workspaceSettings.autoAssignPrimaryLabel,
            "sanity: the decoy repository must not already carry the marker, or this proves nothing",
        )
        CommonUtils.windowControl.windowRepository = foreign

        val page = generalBookPageOf(activity)
        page.onlySetCurrentDocument(FakeBookFactory.journalDocument)
        page.startKeyChooser(activity)
        drainMain()

        val started = assertNotNull(
            shadowOf(activity).nextStartedActivityForResult,
            "the StudyPad arm must open ManageLabels on the nav host",
        )
        val route = assertNotNull(
            started.intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
            "the ManageLabels screen is reached through the nav host's own route extra",
        )
        assertTrue(
            route.contains("manageLabels"),
            "expected a manageLabels route, got: $route",
        )
        assertTrue(
            route.contains(marker.toString()),
            "the route's ManageLabelsData must carry the autoAssignPrimaryLabel of the repository " +
                "that owns this page's window, NOT of whichever repository windowControl currently " +
                "publishes — otherwise the chooser is seeded from, and writes back to, some other " +
                "host's workspace. route: $route",
        )
    }

    /**
     * Anti-vacuity for the two assertions above: nothing else in `create()` starts an Activity for
     * result, so a non-null `nextStartedActivityForResult` there really is the key chooser.
     */
    @Test
    fun aFreshHostHasStartedNothingForResult() {
        val activity = host()
        assertNull(
            shadowOf(activity).nextStartedActivityForResult,
            "if host creation already started something for result, the assertions above could " +
                "not tell the key chooser from it",
        )
    }
}
