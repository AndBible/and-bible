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
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.aCancelFromThisIntentWouldBeTheUsers
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reading-host re-typing T8b: a `STD_REQUEST_CODE` chooser result reaches the reading host, and
 * reaches it AFTER `onResume` rather than inside `onActivityResult`.
 *
 * Every chooser the reading view opens that is a separate Activity rather than a nav-graph
 * destination still answers this way — the dictionary chooser always (`KeyChooserRoute.sheetFor`
 * returns null for it), the full `ChooseDocument` screen from the document sheet's footer, the
 * passage grid, the map and general-book key lists. Classic `MainBibleActivity.onActivityResult` was
 * the tree's only reader of that request code, so with this host as the launcher and no override
 * here every one of them was discarded in silence.
 *
 * The deferral is not a detail: `onActivityResult` runs before `onResume`, i.e. before
 * `reclaimWindowRepository()`, before `ReadingHostPresence.setForeground(this)` (which is what makes
 * `ReadingViewVisibility.isVisible` true for the `AddHistoryItem` a `setKey` posts) and before the
 * bootstrap bridge is re-armed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostActivityResultTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun readingHost(): ActivityController<NavHostComposeActivity> =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.READING,
            ),
        ).also { controllers += it }

    private fun passageGridResult(osisRef: String) = Intent()
        .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.PassageGrid.name)
        .putExtra("verse", osisRef)

    private val NavHostComposeActivity.activeKey: String?
        get() = hostWindowRepository.activeWindow.pageManager.currentPage.key?.osisRef

    @Test
    fun aChooserResultIsHeldUntilTheHostHasResumedAndIsThenApplied() {
        val controller = readingHost().apply { create().start().resume() }
        val activity = controller.get()
        val before = activity.activeKey
        assertNotEquals(
            CHOSEN_CHAPTER,
            before,
            "the fixture must not already be on the verse this test chooses, or it proves nothing",
        )

        controller.pause()
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE, Activity.RESULT_OK, passageGridResult(CHOSEN))
        assertEquals(
            before,
            activity.activeKey,
            "the result must be HELD: onActivityResult runs before onResume has reclaimed the " +
                "window repository or declared this host foreground",
        )

        controller.resume()
        assertEquals(
            CHOSEN_CHAPTER,
            activity.activeKey,
            "…and applied once onResume has reconciled this host's reading state",
        )
    }

    /** A request code this host did not issue must fall straight through, untouched. */
    @Test
    fun aResultForSomeOtherRequestCodeIsNotClaimed() {
        val controller = readingHost().apply { create().start().resume() }
        val activity = controller.get()
        val before = activity.activeKey

        controller.pause()
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE + 1, Activity.RESULT_OK, passageGridResult(CHOSEN))
        controller.resume()

        assertEquals(before, activity.activeKey)
    }

    // --- T8b fix round 1, C2: a cancel nobody performed must not reach the history guard ----------

    /**
     * `NavHostComposeActivity` ported classic's `STD_REQUEST_CODE` + `RESULT_CANCELED` guard, which
     * steps back in history when a cancelled chooser left the page with no key. `MenuCommandHandler`
     * dispatches with that same default request code for rows that are NOT choosers — several of
     * them at this host itself (`dailyReadingPlanButton`, `readingProgressButton`, `managePrompts`,
     * …), and "Rate AndBible" at an `ACTION_VIEW` market link with `FLAG_ACTIVITY_NEW_TASK`, which
     * `Activity.startActivityForResult`'s own javadoc says yields an immediate cancel result.
     *
     * The observable consequence pinned here is the sharper of the two: a cancel nobody performed
     * used to CLOBBER a real chooser result that was already waiting for the next `onResume`, so the
     * user's actual selection was thrown away. (The other consequence, a history entry popped behind
     * the user's back, needs a key-less page and a populated history and is emulator territory.)
     */
    @Test
    fun aSyntheticCancelNeitherClobbersAPendingResultNorReachesTheHistoryGuard() {
        val controller = readingHost().apply { create().start().resume() }
        val activity = controller.get()
        val before = activity.activeKey
        assertNotEquals(CHOSEN_CHAPTER, before)

        controller.pause()
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE, Activity.RESULT_OK, passageGridResult(CHOSEN))
        // A menu row that opens one of this host's own routes, at the SAME request code.
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.READING_PLAN_SELECTOR),
            ActivityBase.STD_REQUEST_CODE,
        )
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE, Activity.RESULT_CANCELED, null)
        controller.resume()

        assertEquals(
            CHOSEN_CHAPTER,
            activity.activeKey,
            "a cancel the user never performed must not discard the chooser result already waiting",
        )
    }

    /** The decision itself, in all four shapes it has to tell apart. */
    @Test
    fun onlyAnExplicitInTaskLaunchCanComeBackAsACancelTheUserPerformed() {
        val host = NavHostComposeActivity::class.java.name
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertTrue(
            aCancelFromThisIntentWouldBeTheUsers(
                Intent(context, net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity::class.java),
                host,
            ),
            "a chooser Activity of this app, started in this task: its cancel IS the user's",
        )
        assertFalse(
            aCancelFromThisIntentWouldBeTheUsers(NavHostComposeActivity.intentFor(context, NavRoutes.READING_PLAN_SELECTOR), host),
            "the host starting ITSELF: singleTop delivers onNewIntent, so no result can ever come back",
        )
        assertFalse(
            aCancelFromThisIntentWouldBeTheUsers(
                Intent(context, net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity::class.java)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                host,
            ),
            "FLAG_ACTIVITY_NEW_TASK is a documented immediate cancel",
        )
        assertFalse(
            aCancelFromThisIntentWouldBeTheUsers(Intent(Intent.ACTION_VIEW), host),
            "an implicit intent (the share chooser, the market link) is not one of this app's choosers",
        )
        assertFalse(
            aCancelFromThisIntentWouldBeTheUsers(
                Intent().setComponent(ComponentName("com.example", host)),
                host,
            ),
            "the class name is what identifies the host, whatever package the component names",
        )
    }

    // --- T8b fix round 1, I3: a result held on a non-reading route is dropped, not stored ----------

    /**
     * `CurrentPageManager.setCurrentDocument`'s auto-open calls
     * `startKeyChooser(CurrentActivityHolder.currentActivity!!)` — which can be this host on a route
     * that owes no reading view at all. `applyPendingActivityResult` will not apply there (the
     * reading workspace belongs to another host, and classic would not have applied it either), but
     * it used to leave the result SITTING in the slot. If that host was later navigated onto
     * `reading` — which flips `readingAppBootstrapped` — the next resume applied a selection the user
     * had made minutes earlier on an unrelated screen.
     */
    @Test
    fun aResultHeldOnANonReadingRouteIsDroppedRatherThanAppliedWhenTheHostLaterOpensReading() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.download()),
        ).also { controllers += it }
        controller.create().start().resume()
        val activity = controller.get()

        controller.pause()
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE, Activity.RESULT_OK, passageGridResult(CHOSEN))
        controller.resume()

        // The same host is now sent onto the reading route, which bootstraps it.
        controller.newIntent(
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        controller.pause()
        controller.resume()

        assertNotEquals(
            CHOSEN_CHAPTER,
            activity.activeKey,
            "a result the host declined to apply must be dropped, not stored until it happens to " +
                "owe a reading view",
        )
    }

    // --- T8b fix round 1, I4: step 2's conclusion, exercised rather than grepped -------------------

    /**
     * The whole claim behind turning `requireNotNull(EXTRA_ROUTE)` into a loud default: after step 3
     * this host is the `android:parentActivityName` of seven Activities, and the platform
     * synthesises a BARE `Intent(context, NavHostComposeActivity::class.java)` — no extras — for the
     * Up affordance and for `TaskStackBuilder.addParentStack`. `NavHostStartRouteTest` covers the
     * pure function and `ReadingHostLauncherGuardTest` greps for the absence of the `requireNotNull`;
     * neither of them actually starts the Activity that way. This does.
     */
    @Test
    fun aHostStartedFromABareIntentOpensTheReadingViewInsteadOfThrowing() {
        val bare = Intent(
            ApplicationProvider.getApplicationContext(),
            NavHostComposeActivity::class.java,
        )
        val activity = Robolectric.buildActivity(NavHostComposeActivity::class.java, bare)
            .also { controllers += it }
            .create().start().resume().get()

        assertNotNull(
            activity.hostWindowRepository,
            "a bare parent Intent must land on the reading route and bootstrap it — that field " +
                "throws UninitializedPropertyAccessException on any other route",
        )
    }

    companion object {
        /** What the passage grid hands back: a single verse. */
        private const val CHOSEN = "Ps.23.1"

        /** What the Bible page's key becomes once that verse is applied — its CHAPTER. */
        private const val CHOSEN_CHAPTER = "Ps.23"
    }
}
