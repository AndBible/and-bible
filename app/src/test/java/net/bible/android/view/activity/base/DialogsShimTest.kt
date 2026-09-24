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
package net.bible.android.view.activity.base

import android.os.Looper
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.util.Hourglass
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Task 7/8: `Dialogs` and `Hourglass` are shims over the app-wide `AppDialogController` /
 * `AgentPermissionController`, not builders of platform `AlertDialog`/`ProgressDialog`s.
 * One test class for both (Task 8's brief adds its two Hourglass tests here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DialogsShimTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    @After
    fun tearDown() {
        // Every built Activity is stopped so CurrentActivityHolder is empty again for the next
        // test -- ActivityBase.onStop is what calls CurrentActivityHolder.deactivate.
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
    }

    private fun activity() =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * `CurrentActivityHolder` (`app/src/main/java/net/bible/android/view/activity/base/CurrentActivityHolder.kt`)
     * is process-global with no public reset, and `tearDown()`'s pause/stop/destroy only unregisters
     * ACTIVITIES THIS TEST BUILT. In the full `:app` unit suite (one JVM) an earlier test class can
     * leave an Activity registered, so `withNoActivityAMessageBecomesAToast`'s assumption that the
     * holder starts empty does not hold — it did in isolation only by luck. Same reflection-based
     * save/clear/restore idiom as `CurrentPageManagerNoActivityTest.holderActivities()`, so this test
     * establishes and restores its own precondition instead of relying on suite ordering.
     */
    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    @Test
    fun showErrorMsgPostsANonCancellableMessageWithReport() {
        activity()
        Dialogs.showErrorMsg("boom <b>x</b>", RuntimeException("e"))
        idle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Message
        assertEquals("boom <b>x</b>", head.message)
        assertFalse(head.cancellable)
        assertNotNull(head.neutralText)
    }

    @Test
    fun okayCallbackRunsOnceOnOk() {
        activity()
        var calls = 0
        Dialogs.showErrorMsg("msg") { calls++ }
        idle()
        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.Ok)
        dialogs.respond(id, AppDialogResult.Ok)
        idle()
        assertEquals(1, calls)
    }

    @Test
    fun withNoActivityAMessageBecomesAToast() {
        // Own precondition, not suite-order luck: clear CurrentActivityHolder (an earlier test class
        // in the full suite can leave an Activity registered) and prove it's empty before calling
        // showErrorMsg, so a future pollution fails here with a clear message, not a mystery `null`
        // deep inside the Toast assertion.
        val savedActivities = ArrayList(holderActivities())
        holderActivities().clear()
        try {
            assertNull(CurrentActivityHolder.currentActivity)
            Dialogs.showErrorMsg("x")
            idle()
            assertEquals("x", ShadowToast.getTextOfLatestToast())
            assertNull(dialogs.pending.value)
        } finally {
            holderActivities().addAll(savedActivities)
        }
    }

    @Test
    fun aMessageFromABackgroundThreadIsQueuedAndItsCallbackRunsOnMain() {
        activity()
        var callbackThread: Thread? = null
        Thread { Dialogs.showErrorMsg("bg") { callbackThread = Thread.currentThread() } }.apply { start(); join() }
        idle()
        val id = dialogs.pending.value!!.id
        Thread { dialogs.respond(id, AppDialogResult.Ok) }.apply { start(); join() }
        idle()
        assertEquals(Looper.getMainLooper().thread, callbackThread)
    }

    @Test
    fun simpleQuestionIsTrueOnlyOnOk() = runTest {
        val activity = activity()
        val ok = async { Dialogs.simpleQuestion(activity, "message") }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(true, ok.await())

        val cancelled = async { Dialogs.simpleQuestion(activity, "message2") }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertEquals(false, cancelled.await())
    }

    @Test
    fun showMsg2MapsEveryButton() = runTest {
        val activity = activity()

        val ok = async { Dialogs.showMsg2(activity, "msg", isCancelable = true, showReport = true) }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(Dialogs.Result.OK, ok.await())

        val cancel = async { Dialogs.showMsg2(activity, "msg", isCancelable = true, showReport = true) }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertEquals(Dialogs.Result.CANCEL, cancel.await())

        val neutral = async { Dialogs.showMsg2(activity, "msg", isCancelable = true, showReport = true) }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Neutral)
        assertEquals(Dialogs.Result.REPORT, neutral.await())
    }

    @Test
    fun multiselectReturnsTheChosenItemsInOrder() = runTest {
        val activity = activity()
        val items = listOf("a", "b", "c")
        val chosen = async { Dialogs.multiselect(activity, "title", items) }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("2", "0")))
        // Order follows `items`, not the order the ids came back in.
        assertEquals(listOf("a", "c"), chosen.await())
    }

    @Test
    fun multiselectCancelIsEmpty() = runTest {
        val activity = activity()
        val items = listOf("a", "b", "c")
        val chosen = async { Dialogs.multiselect(activity, "title", items) }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertEquals(emptyList<String>(), chosen.await())
    }

    @Test
    fun agentPermissionDialogAlwaysUsesTheController() = runTest {
        val activity = activity()
        val permissions = KoinJavaComponent.get<net.bible.sharedcore.ai.AgentPermissionController>(
            net.bible.sharedcore.ai.AgentPermissionController::class.java,
        )
        val result = async { Dialogs.agentPermissionDialog(activity, "Tool", "Description") }
        yield()
        assertNotNull(permissions.pending.value)
        permissions.respond(AgentPermissionChoice.ALLOW)
        assertEquals(Dialogs.AgentPermissionResult.ALLOW, result.await())
    }

    // -- Task 8: Hourglass --

    @Test
    fun hourglassShowsAndDismissesAProgress() = runTest {
        val activity = activity()
        val h = Hourglass(activity)
        h.show()
        val head = dialogs.progress.value!!.request
        assertTrue(head is AppDialogRequest.Progress)
        assertEquals(activity.getString(R.string.please_wait), (head as AppDialogRequest.Progress).message)
        h.dismiss()
        assertNull(dialogs.progress.value)
    }

    @Test
    fun hourglassDismissTwiceIsHarmless() = runTest {
        val activity = activity()
        val h = Hourglass(activity)
        h.show()
        h.dismiss()
        h.dismiss()
        assertNull(dialogs.progress.value)
    }

    /** C1: the Hourglass's Progress must not block a question raised while it is showing. */
    @Test
    fun aQuestionWhileTheHourglassShowsIsAnswerable() = runTest {
        val activity = activity()
        val h = Hourglass(activity)
        h.show()
        assertNotNull(dialogs.progress.value)
        val answer = async { Dialogs.simpleQuestion(activity, "message") }
        yield()
        assertNotNull(dialogs.pending.value)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(true, answer.await())
        h.dismiss()
        assertNull(dialogs.progress.value)
    }
}
