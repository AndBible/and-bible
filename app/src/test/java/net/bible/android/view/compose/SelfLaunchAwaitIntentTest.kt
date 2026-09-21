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
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity

/**
 * F53. A `startActivityForResult` aimed at THIS `singleTop` host, from `awaitIntent`, is answered by
 * the platform with an immediate synthetic `RESULT_CANCELED` that destroys the awaiting deferred, so
 * the real answer the nav-graph collector delivers 3 s later reaches nobody.
 *
 * Both tests below FAIL on the pre-fix tree:
 *  - [aSelfLaunchAtAnAsyncCodeDoesNotReachThePlatform] fails because the intent IS started;
 *  - [aSyntheticCancelDoesNotSpendTheAwaitedResult] fails because the awaited result is the CANCELED
 *    one, not the OK one that follows.
 *
 * **[aSyntheticCancelDoesNotSpendTheAwaitedResult] is gated on the platform actually having been
 * asked**, i.e. on [ShadowActivity.nextStartedActivityForResult] being non-null, rather than
 * unconditionally injecting the synthetic cancel. That mirrors what a device does: the platform can
 * only answer a `startActivityForResult` it was actually given. Pre-fix the platform IS asked (test 1
 * proves it), so the cancel this test injects is the one Robolectric does not synthesise for us but a
 * device would; post-fix nothing is ever started (test 1 again), so there is no call for the platform
 * to answer and this test injects none — `ActivityBase.onActivityResult`'s "first result for a code
 * wins" bookkeeping is unchanged by F53's fix (it only stops the self-targeted
 * `startActivityForResult` from reaching the platform in the first place), so an unconditional
 * injection here would fail after the fix for a reason that has nothing to do with F53: nobody would
 * ever issue that call on a real, fixed device either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SelfLaunchAwaitIntentTest {

    /**
     * `firstTime` is pinned false first, and that is not incidental: it is a file-level `var` in
     * `ActivityBase.kt`, and `ActivityBase.fixNightMode()` — called from `onCreate` — arms
     * `lifecycleScope.launch { delay(250); recreate() }` while it is true (the night-mode hack; see
     * `ActivityBase.kt:64,167-178`). Robolectric does not reset it between test METHODS in this JVM,
     * only between fresh processes, so every `host()` here would otherwise arm that delayed recreate
     * — and once `idleMainLooper()` advances the fake clock past the 250 ms delay, it fires and
     * cancels `lifecycleScope`, which is what corrupted an earlier version of
     * [aSyntheticCancelDoesNotSpendTheAwaitedResult] (see its kdoc). It has nothing to do with
     * `navigateInsteadOfSelfLaunch`/`navigateToRoute` — confirmed by pinning `firstTime` here and
     * re-running with the coroutine back on `activity.lifecycleScope`, which then behaves exactly
     * like every production caller (`BibleView.assignLabels`, …) does.
     */
    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.READING,
            ),
        ).setup().get()
    }

    private fun labelsIntent(activity: NavHostComposeActivity): Intent =
        NavHostComposeActivity.intentFor(activity, NavRoutes.manageLabels(LABEL_PAYLOAD))

    @Test
    fun aSelfLaunchAtAnAsyncCodeDoesNotReachThePlatform() {
        val activity = host()
        val shadow: ShadowActivity = org.robolectric.Shadows.shadowOf(activity)

        activity.startActivityForResult(labelsIntent(activity), ActivityBase.ASYNC_REQUEST_CODE_START)

        assertEquals(
            "a self-launch at an async request code must navigate the live graph, not ask the " +
                "platform for a result it answers with a synthetic cancel",
            null,
            shadow.nextStartedActivityForResult,
        )
    }

    /**
     * Runs the awaiting coroutine on `activity.lifecycleScope`, exactly like every production caller
     * (`BibleView.assignLabels`, …). Earlier this used a scope of its own, on the mistaken belief
     * that `navigateInsteadOfSelfLaunch`'s `navigateToRoute` call was cascading into an Activity
     * recreate that cancelled `lifecycleScope` mid-await; the real cause was `ActivityBase`'s
     * `firstTime`-gated night-mode `recreate()` hack going off because a previous test in this JVM
     * had already armed it (see [host]'s kdoc) — unrelated to F53 or to this call. Pinning
     * `firstTime` false there is what let this go back to `lifecycleScope`.
     *
     * **The synthetic-cancel injection below does not fire post-fix.** `shadow.nextStartedActivityForResult`
     * is null once F53 is fixed (test 1 proves it), so the `if` guarding it is false and this test's
     * only onActivityResult call is the real OK. Its value post-fix is as a REGRESSION GUARD: if a
     * future change makes this self-launch reach the platform again (the `if` turns true), the
     * injected cancel destroys the deferred before the OK is delivered and this test goes red with
     * the CANCELED value, catching the very regression F53 fixes. Pre-fix, this is what makes the
     * test fail for the documented reason (see the class kdoc).
     */
    @OptIn(ExperimentalCoroutinesApi::class) // `getCompleted()` — the assertion is the only reader.
    @Test
    fun aSyntheticCancelDoesNotSpendTheAwaitedResult() = runTest {
        val activity = host()
        val shadow: ShadowActivity = org.robolectric.Shadows.shadowOf(activity)
        val awaited = CompletableDeferred<Int>()

        activity.lifecycleScope.launch(Dispatchers.Main) {
            val result = activity.awaitIntent(labelsIntent(activity))
            awaited.complete(result.resultCode)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // What a device does: the synthetic cancel first, the real answer afterwards -- but only the
        // platform can synthesise a cancel for a call it was actually given (see the class doc).
        if (shadow.nextStartedActivityForResult != null) {
            activity.onActivityResult(ActivityBase.ASYNC_REQUEST_CODE_START, Activity.RESULT_CANCELED, null)
        }
        activity.onActivityResult(ActivityBase.ASYNC_REQUEST_CODE_START, Activity.RESULT_OK, Intent())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(
            "the awaited result must be the real RESULT_OK, not the synthetic cancel that precedes it",
            Activity.RESULT_OK,
            awaited.getCompleted(),
        )
    }

    private companion object {
        const val LABEL_PAYLOAD = """{"mode":"ASSIGN"}"""
    }
}
