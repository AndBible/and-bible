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
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.android.TEST_SDK
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.ReadingResultKind
import net.bible.android.view.activity.nav.ReadingResultRequests
import net.bible.android.view.activity.nav.readingResultKindForLaunch
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Per FLOW**: the launch each of the nine reading-view call sites actually builds must be
 * recognised as one this host will answer in-graph (reading-host re-typing T8c).
 *
 * This is the half [ReadingInGraphResultTest] cannot reach. That test proves the reading destination
 * collects a channel once something has recorded a request; this one proves that the intents the
 * real call sites build are what record it. Between them the chain is closed: call site -> recorded
 * request -> in-graph push -> collected answer -> the caller that asked.
 *
 * **Why it is a test and not a comment.** Every one of these launches is built by a DIFFERENT route
 * builder — three of them go through `ScreenLauncher.MIGRATED`, five build
 * `NavRoutes.manageLabels(data)` by hand, one builds `NavRoutes.readingProgress(tab)` because the
 * MIGRATED entry is the argument-free route — and the payload-carrying ones only agree with their
 * pattern up to the `?`. A route renamed or a builder changed on one of those paths would leave
 * that one flow silently uncollected again, which is precisely the failure mode this task exists to
 * end, and precisely the one a green suite already failed to notice once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingResultRequestTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val host = NavHostComposeActivity::class.java.name

    private fun kindOf(intent: Intent) = readingResultKindForLaunch(intent, host)

    private fun navHostIntent(route: String) = NavHostComposeActivity.intentFor(context, route)

    // ——— the five manage-labels entry points ————————————————————————————————————————————————————

    /**
     * `BibleView.assignLabels`, `HideLabelsPreference.openDialog`, `AutoAssignPreference.openDialog`,
     * `MenuCommandHandler`'s StudyPads row and `CurrentGeneralBookPage`'s StudyPad arm all build
     * `NavHostComposeActivity.intentFor(ctx, NavRoutes.manageLabels(data))` — the same route with a
     * different `mode` inside the payload. The payload is what makes the route base test
     * load-bearing: `manageLabels` puts the whole `ManageLabelsData` JSON in the query string, so a
     * whole-route comparison would match none of them.
     */
    @Test
    fun everyLabelManagerEntryPointIsRecognised() {
        val payloads = listOf(
            """{"mode":"ASSIGN"}""",
            """{"mode":"HIDELABELS","isWindow":true}""",
            """{"mode":"WORKSPACE"}""",
            """{"mode":"STUDYPAD"}""",
            // A payload with a literal `?` and `&` in a label name: the worst case for a route base
            // read, and the reason the base is taken with `substringBefore('?')` on the ROUTE rather
            // than by pattern matching.
            """{"mode":"ASSIGN","labels":["what? and & why"]}""",
        )
        for (payload in payloads) {
            assertEquals(
                ReadingResultKind.ManageLabels,
                kindOf(navHostIntent(NavRoutes.manageLabels(payload))),
                "the label manager launched with $payload was not recognised",
            )
        }
    }

    /** `CurrentGeneralBookPage`'s my-document arm — three required arguments, all in the query. */
    @Test
    fun theMyDocumentPageChooserIsRecognised() {
        assertEquals(
            ReadingResultKind.MyDocumentPages,
            kindOf(navHostIntent(NavRoutes.myDocumentPages("doc-1", "MyDoc", "My document"))),
        )
    }

    /**
     * Reading progress from BOTH of its entry points, which build the route differently on purpose:
     * `MenuCommandHandler`'s row goes through `ScreenLauncher.MIGRATED` (the argument-free route,
     * meaning "the tab the user was last on"), and `BibleJavascriptInterface.openReadingProgress` is
     * the one caller that names a tab and so builds `NavRoutes.readingProgress(tab)` by hand.
     */
    @Test
    fun bothReadingProgressEntryPointsAreRecognised() {
        assertEquals(
            ReadingResultKind.ReadingProgress,
            kindOf(ScreenLauncher.intentFor(context, Screen.ReadingProgress)),
            "the menu row",
        )
        assertEquals(
            ReadingResultKind.ReadingProgress,
            kindOf(navHostIntent(NavRoutes.readingProgress(1))),
            "BibleJavascriptInterface.openReadingProgress(tab)",
        )
    }

    /** `MenuCommandHandler`'s bookmarks row — the channel the review's inventory missed. */
    @Test
    fun theBookmarkListIsRecognised() {
        assertEquals(
            ReadingResultKind.Bookmarks,
            kindOf(ScreenLauncher.intentFor(context, Screen.Bookmarks)),
        )
        // …and with a label filter, which a caller that knew one would build directly.
        assertEquals(ReadingResultKind.Bookmarks, kindOf(navHostIntent(NavRoutes.bookmarks(3))))
    }

    /** `MenuCommandHandler`'s my-documents row — the fifth channel. */
    @Test
    fun theMyDocumentsListIsRecognised() {
        assertEquals(
            ReadingResultKind.MyDocuments,
            kindOf(ScreenLauncher.intentFor(context, Screen.MyDocuments)),
        )
    }

    // ——— and nothing else ————————————————————————————————————————————————————————————————————————

    /**
     * A self-launch on a route that produces NO result records nothing. `download` is the one that
     * matters: `MenuCommandHandler`'s download row and `BibleJavascriptInterface.openDownloads` both
     * launch this host at `UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH`, and an entry left recorded for
     * them would be a request no channel could ever fill.
     */
    @Test
    fun aSelfLaunchThatProducesNoResultRecordsNothing() {
        assertNull(kindOf(navHostIntent(NavRoutes.download())), "download")
        assertNull(kindOf(ScreenLauncher.intentFor(context, Screen.Settings)), "settings")
        assertNull(kindOf(navHostIntent(NavRoutes.READING)), "the reading route itself")
    }

    /**
     * A launch at ANOTHER component is an ordinary Activity round trip and is answered the ordinary
     * way — `ChooseDocument`, `ChooseGeneralBookKey`, `GridChoosePassageBook` and the rest still
     * reach `NavHostComposeActivity.onActivityResult` for real. Claiming one here would take an
     * answer away from `applyPendingActivityResult`.
     */
    @Test
    fun aLaunchAtAnotherComponentIsNotOurs() {
        assertNull(kindOf(ScreenLauncher.intentFor(context, Screen.ChooseDocument)))
        assertNull(kindOf(ScreenLauncher.intentFor(context, Screen.ChooseGeneralBookKey)))
    }

    /** An implicit intent — the share chooser, a browser link — has no component and is not ours. */
    @Test
    fun anImplicitIntentIsNotOurs() {
        assertNull(kindOf(Intent(Intent.ACTION_VIEW)))
    }

    /**
     * Anti-vacuity for every `assertNull` above: the same reader, given the same intent shape but
     * this host's own class name, DOES answer — so "null" above means "not a result launch", not
     * "this function never answers".
     */
    @Test
    fun theReaderAnswersAtAll() {
        assertEquals(ReadingResultKind.Bookmarks, kindOf(navHostIntent(NavRoutes.bookmarks())))
        assertNull(
            readingResultKindForLaunch(navHostIntent(NavRoutes.bookmarks()), "some.other.Activity"),
            "…and only for THIS host: a second host class would be a second instance, which answers " +
                "with a real Activity result",
        )
    }

    // ——— the gate's three properties —————————————————————————————————————————————————————————————

    /**
     * **It cannot be spent twice.** [ReadingResultRequests.claim] forgets as it reads, which is the
     * half of the once-only guarantee that lives outside the channel: `consume()` stops the same
     * pending VALUE being read twice, and this stops a second delivery being applied at all.
     */
    @Test
    fun aClaimedRequestIsForgotten() {
        val requests = ReadingResultRequests()
        requests.record(ReadingResultKind.ManageLabels, 1900)

        assertTrue(requests.isAwaiting(ReadingResultKind.ManageLabels))
        assertEquals(1900, requests.claim(ReadingResultKind.ManageLabels))
        assertFalse(requests.isAwaiting(ReadingResultKind.ManageLabels), "the request must be gone")
        assertNull(requests.claim(ReadingResultKind.ManageLabels), "and stay gone")
    }

    /**
     * **A stale request is overwritten, never spent.** The user can leave the bookmark list without
     * picking a row, which leaves a request recorded with nothing to fill it. The next launch
     * rewrites it — and since an answer can only reach the reading destination if a launch pushed
     * the producing route onto it, a left-over entry can never be the one an answer is applied
     * under.
     */
    @Test
    fun aLaterRequestOverwritesAnAbandonedOne() {
        val requests = ReadingResultRequests()
        requests.record(ReadingResultKind.Bookmarks, 1)
        requests.record(ReadingResultKind.Bookmarks, 1903)

        assertEquals(1903, requests.claim(ReadingResultKind.Bookmarks))
    }

    /** Each channel is gated on its OWN request; arming one must not open another. */
    @Test
    fun theKindsDoNotShareARequest() {
        val requests = ReadingResultRequests()
        requests.record(ReadingResultKind.ManageLabels, 1900)

        for (kind in ReadingResultKind.entries.filter { it != ReadingResultKind.ManageLabels }) {
            assertFalse(requests.isAwaiting(kind), "$kind must not be opened by a ManageLabels request")
        }
    }
}
