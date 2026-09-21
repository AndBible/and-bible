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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
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

    private fun host(): NavHostComposeActivity =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.READING,
            ),
        ).setup().get()

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

    @OptIn(ExperimentalCoroutinesApi::class) // `getCompleted()` — the assertion is the only reader.
    @Test
    fun aSyntheticCancelDoesNotSpendTheAwaitedResult() = runTest {
        val activity = host()
        val shadow: ShadowActivity = org.robolectric.Shadows.shadowOf(activity)
        val awaited = CompletableDeferred<Int>()

        // A scope of its own, deliberately NOT `activity.lifecycleScope`: the fix under test
        // navigates the live graph, which disposes the `reading` destination's composition and, in
        // this Robolectric harness, cascades into an Activity recreate — cancelling
        // `lifecycleScope` mid-await for a reason that has nothing to do with what this test
        // proves (whether the REAL RESULT_OK reaches the awaited deferred, not the synthetic
        // CANCELED that precedes it). `resultByCode`/`onActivityResult` bookkeeping this exercises
        // lives on the Activity itself, not on the caller's scope, so this substitution does not
        // change what is under test.
        val observerScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
        observerScope.launch {
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
