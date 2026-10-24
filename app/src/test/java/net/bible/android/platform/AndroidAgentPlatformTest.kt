package net.bible.android.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidAgentPlatformTest {
    @Test fun permissionChoicesMapExactlyAndCancellationReleasesWaiter() = runTest {
        val platform = AndroidAgentPlatform()
        val permissions = org.koin.core.context.GlobalContext.get().get<net.bible.sharedcore.ai.AgentPermissionController>()
        try {
            for (choice in net.bible.sharedcore.ai.AgentPermissionChoice.entries) {
                val answer = async { platform.askToolPermission("Tool", "Description", "Action") }
                runCurrent()
                assertEquals("Tool", permissions.pending.value!!.toolDisplayName)
                permissions.respond(choice)
                assertEquals(net.bible.service.llm.agent.ToolPermissionAnswer.valueOf(choice.name), answer.await())
                assertNull(permissions.pending.value)
            }
            val cancelled = async { platform.askToolPermission("Tool", "Description", null) }
            runCurrent()
            assertNotNull(permissions.pending.value)
            cancelled.cancelAndJoin()
            assertNull(permissions.pending.value)
        } finally {
            permissions.dismiss()
        }
    }

    @Test fun questionCanBeAnsweredWhileBackgroundedAndCancellationReleasesWaiter() = runTest {
        val platform = AndroidAgentPlatform()
        val dialogs = org.koin.core.context.GlobalContext.get().get<net.bible.sharedcore.ui.dialog.AppDialogController>()
        CurrentActivityHolder.forgetActivitiesForTest()
        try {
            val answer = async { platform.askQuestion("Title", "Message") }
            runCurrent()
            assertNotNull(dialogs.pending.value)
            dialogs.respond(dialogs.pending.value!!.id, net.bible.sharedcore.ui.dialog.AppDialogResult.Ok)
            assertTrue(answer.await())
            val cancelled = async { platform.askQuestion(null, "Message") }
            runCurrent()
            assertNotNull(dialogs.pending.value)
            cancelled.cancelAndJoin()
            assertNull(dialogs.pending.value)
        } finally {
            dialogs.cancelAll()
        }
    }

    @Test fun foregroundEventsFollowRealActivityPauseAndResume() {
        CurrentActivityHolder.forgetActivitiesForTest()
        val platform = AndroidAgentPlatform()
        val seen = mutableListOf<Boolean>()
        val subscription = platform.foregroundChanges.subscribe { seen += it }
        val activity = Robolectric.buildActivity(CalculatorComposeActivity::class.java)
        try {
            assertFalse(platform.hasForegroundUi)
            activity.setup()
            assertTrue(platform.hasForegroundUi)
            activity.pause().stop()
            assertFalse(platform.hasForegroundUi)
            activity.resume()
            assertTrue(platform.hasForegroundUi)
            assertEquals(listOf(true, false, true), seen)
        } finally {
            subscription.cancel()
            activity.pause().stop().stop().destroy()
            CurrentActivityHolder.forgetActivitiesForTest()
        }
    }

    @Test fun waitPollsAt500msAndCancellationEndsTheWait() = runTest {
        CurrentActivityHolder.forgetActivitiesForTest()
        val platform = AndroidAgentPlatform()
        val activity = Robolectric.buildActivity(CalculatorComposeActivity::class.java)
        val waiter = async { platform.awaitForegroundUi() }
        try {
            runCurrent()
            assertFalse(waiter.isCompleted)
            advanceTimeBy(499)
            activity.setup()
            assertFalse(waiter.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(waiter.await())
            activity.pause().stop()
            val cancelled = async { platform.awaitForegroundUi() }
            runCurrent()
            assertFalse(cancelled.isCompleted)
            cancelled.cancelAndJoin()
            assertTrue(cancelled.isCancelled)
        } finally {
            waiter.cancelAndJoin()
            activity.stop().destroy()
            CurrentActivityHolder.forgetActivitiesForTest()
        }
    }
}
