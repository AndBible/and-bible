package net.bible.sharedcore.platform.search

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosSearchIndexingRunnerTest {
    private val ended = mutableListOf<ULong>()
    private var expire: (() -> Unit)? = null
    private val runner = IosSearchIndexingRunner(
        begin = { handler -> expire = handler; 7uL },
        end = { ended += it },
    )

    @Test fun normalCompletionEndsTheTaskOnceAndReturnsTheResult() = runTest {
        var cancels = 0
        val result = runner.run(cancelBuild = { cancels++ }) { "built" }
        assertEquals("built", result)
        assertEquals(listOf(7uL), ended)
        assertEquals(0, cancels)
        // A late expiry after completion must neither cancel nor end again.
        expire!!.invoke()
        assertEquals(listOf(7uL), ended)
    }

    @Test fun exceptionStillEndsTheTaskOnceAndPropagates() = runTest {
        assertFailsWith<IllegalStateException> {
            runner.run(cancelBuild = {}) { error("build failed") }
        }
        assertEquals(listOf(7uL), ended)
    }

    @Test fun expiryCancelsTheBuildAndEndsTheTaskBeforeTheBuildReturns() = runTest {
        var cancels = 0
        var endedWhenExpiryReturned = -1
        runner.run(cancelBuild = { cancels++ }) {
            expire!!.invoke()
            endedWhenExpiryReturned = ended.size
            expire!!.invoke() // UIKit may not repeat, but a repeat must be harmless
        }
        assertEquals(1, cancels)
        assertEquals(1, endedWhenExpiryReturned)
        assertEquals(listOf(7uL), ended, "the build returning afterwards must not end the task again")
    }

    @Test fun expiryInsideBeginCancelsBeforeTheBuildStarts() = runTest {
        var cancels = 0
        var cancelsSeenByBuild = -1
        val early = IosSearchIndexingRunner(
            begin = { handler -> handler(); 3uL },
            end = { ended += it },
        )
        early.run(cancelBuild = { cancels++ }) { cancelsSeenByBuild = cancels }
        assertEquals(1, cancels)
        assertEquals(1, cancelsSeenByBuild)
        assertEquals(listOf(3uL), ended)
    }

    @Test fun invalidIdentifierIsNeverEnded() = runTest {
        val denied = IosSearchIndexingRunner(
            begin = { platform.UIKit.UIBackgroundTaskInvalid },
            end = { ended += it },
        )
        denied.run(cancelBuild = {}) { }
        assertEquals(emptyList(), ended)
    }

    @Test fun throwingCancelStillEndsTheTask() = runTest {
        runner.run(cancelBuild = { error("cancel failed") }) {
            assertFailsWith<IllegalStateException> { expire!!.invoke() }
        }
        assertEquals(listOf(7uL), ended)
    }
}
