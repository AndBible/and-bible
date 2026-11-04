@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedcore.platform.files

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.yield
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import kotlin.test.*

/** Exercises real session/terminal ownership without a scene, key window or system document browser. */
class IosDocumentPickerSessionTest {
    private class Host : UIViewController(null, null) {
        var attached: Sheet? = null
        val presentations = mutableListOf<Sheet>()

        fun present(sheet: Sheet, presentation: FilePresentation<List<NSURL>>) {
            check(attached == null)
            attached = sheet
            presentations.add(sheet)
            sheet.host = this
            sheet.presentation = presentation
        }
    }

    private class Sheet : UIViewController(null, null) {
        var host: Host? = null
        var completion: (() -> Unit)? = null
        lateinit var presentation: FilePresentation<List<NSURL>>
        override fun presentingViewController(): UIViewController? = host
        override fun dismissViewControllerAnimated(flag: Boolean, completion: (() -> Unit)?) {
            check(this.completion == null)
            this.completion = completion
        }
        fun completeDismissal() {
            val callback = assertNotNull(completion)
            completion = null
            host?.attached = null
            host = null
            callback()
        }
    }

    @Test fun presentationFailureReleasesTheSlotForTheNextRequest() = runOnMainRunLoop {
        val host = Host()
        val session = IosDocumentPickerSession()
        val failure = IllegalStateException("Presentation failed")
        val thrown = assertFailsWith<IllegalStateException> {
            session.pick(Sheet(), present = { throw failure })
        }
        assertSame(failure, thrown)
        val next = Sheet()
        val result = session.pick(next, present = {
            host.present(next, it)
            it.finish(listOf(NSURL.fileURLWithPath("/tmp/next")))
            next.completeDismissal()
        })
        assertEquals(listOf(NSURL.fileURLWithPath("/tmp/next")), result)
        assertNull(host.attached)
    }

    @Test fun cancellationWaitsForItsOwnDismissalBeforeReleasingTheSession() = runOnMainRunLoop {
        val host = Host()
        val session = IosDocumentPickerSession()
        val sheet = Sheet()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(sheet, present = { host.present(sheet, it) })
        }
        try {
            request.cancel()
            repeat(4) { yield() }
            assertNotNull(sheet.completion)
            assertFalse(request.isCompleted, "Cancellation must retain the sheet until dismissal")
            sheet.completeDismissal()
            request.join()
            assertTrue(request.isCancelled)
            assertNull(host.attached)
            assertTrue(session.cancelPrevious())
        } finally {
            request.cancel()
            repeat(8) {
                yield()
                host.attached?.takeIf { it.completion != null }?.completeDismissal()
            }
            request.cancelAndJoin()
        }
    }

    @Test fun staleReplacementCannotAcquireOrCancelTheLatestSheetAfterDismissal() = runOnMainRunLoop {
        val host = Host()
        val session = IosDocumentPickerSession()
        val a = Sheet()
        val b = Sheet()
        val c = Sheet()
        var latest = 1
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(a, present = { host.present(a, it) })
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(b, isCurrent = { latest == 1 }, present = { host.present(b, it) })
        }
        latest = 2
        val third = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(c, isCurrent = { latest == 2 }, present = { host.present(c, it) })
        }
        try {
            assertEquals(listOf(a), host.presentations)
            a.completeDismissal()
            repeat(4) { yield() }
            assertEquals(emptyList(), first.await())
            assertEquals(emptyList(), second.await())
            assertEquals(listOf(a, c), host.presentations, "Stale request must never acquire the slot")
            assertSame(c, host.attached)
            assertNull(c.completion, "Stale dismissal waiter must not cancel the latest sheet")
            assertFalse(third.isCompleted)
            c.presentation.finish(emptyList())
            c.completeDismissal()
            assertEquals(emptyList(), third.await())
        } finally {
            first.cancel(); second.cancel(); third.cancel()
            repeat(8) {
                yield()
                host.attached?.takeIf { it.completion != null }?.completeDismissal()
            }
            first.cancelAndJoin(); second.cancelAndJoin(); third.cancelAndJoin()
        }
    }

    @Test fun competingReplacementsRecheckTheActiveSheetAfterDelayedDismissal() = runOnMainRunLoop {
        val host = Host()
        val session = IosDocumentPickerSession()
        val a = Sheet()
        val b = Sheet()
        val c = Sheet()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(a, present = { host.present(a, it) })
        }
        val lateA = a.presentation
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(b, present = { host.present(b, it) })
        }
        val third = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(c, present = { host.present(c, it) })
        }
        try {
            assertEquals(listOf(a), host.presentations)
            assertNotNull(a.completion)
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            assertFalse(third.isCompleted)
            a.completeDismissal()
            repeat(4) { yield() }
            assertEquals(emptyList(), first.await())
            assertEquals(listOf(a, b), host.presentations)
            assertNotNull(b.completion, "The intermediate sheet must be cancelled after A's dismissal")
            val lateB = b.presentation
            assertFalse(second.isCompleted)
            assertFalse(third.isCompleted)
            b.completeDismissal()
            repeat(4) { yield() }
            assertEquals(emptyList(), second.await())
            assertEquals(listOf(a, b, c), host.presentations)
            assertSame(c, host.attached)
            lateA.finish(listOf(NSURL.fileURLWithPath("/tmp/late-a")))
            lateB.finish(listOf(NSURL.fileURLWithPath("/tmp/late-b")))
            assertFalse(third.isCompleted)
            c.presentation.finish(emptyList())
            assertFalse(third.isCompleted)
            c.completeDismissal()
            assertEquals(emptyList(), third.await())
            assertNull(host.attached)
        } finally {
            // Drain all held lifecycle completions before joining cancelled children.
            first.cancel(); second.cancel(); third.cancel()
            repeat(8) {
                yield()
                host.attached?.takeIf { it.completion != null }?.completeDismissal()
            }
            first.cancelAndJoin(); second.cancelAndJoin(); third.cancelAndJoin()
        }
    }
}
