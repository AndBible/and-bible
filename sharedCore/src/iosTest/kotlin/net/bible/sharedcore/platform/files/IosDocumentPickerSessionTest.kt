@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedcore.platform.files

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.yield
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeData
import kotlin.test.*

/** Models UIKit's relationship and delayed completion; does not claim app-hosted presentation coverage. */
class IosDocumentPickerSessionTest {
    private class Host : UIViewController(null, null) {
        var attached: Sheet? = null
        val presentations = mutableListOf<Sheet>()
        override fun presentedViewController(): UIViewController? = attached
        override fun presentViewController(viewControllerToPresent: UIViewController, animated: Boolean, completion: (() -> Unit)?) {
            check(attached == null)
            val sheet = viewControllerToPresent as Sheet
            attached = sheet
            presentations.add(sheet)
            sheet.host = this
            sheet.presentedDelegate = sheet.delegate
            completion?.invoke()
        }
    }

    private class Sheet : UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true) {
        var host: Host? = null
        var completion: (() -> Unit)? = null
        var presentedDelegate: UIDocumentPickerDelegateProtocol? = null
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

    @Test fun staleReplacementCannotAcquireOrCancelTheLatestSheetAfterDismissal() = runOnMainRunLoop {
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = Host()
        window.rootViewController = host
        window.makeKeyAndVisible()
        val session = IosDocumentPickerSession()
        val a = Sheet()
        val b = Sheet()
        val c = Sheet()
        var latest = 1
        val first = async(start = CoroutineStart.UNDISPATCHED) { session.pick(a) { host } }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(b, isCurrent = { latest == 1 }) { host }
        }
        latest = 2
        val third = async(start = CoroutineStart.UNDISPATCHED) {
            session.pick(c, isCurrent = { latest == 2 }) { host }
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
            c.delegate!!.documentPickerWasCancelled(c)
            c.completeDismissal()
            assertEquals(emptyList(), third.await())
        } finally {
            first.cancel(); second.cancel(); third.cancel()
            repeat(8) {
                yield()
                host.attached?.takeIf { it.completion != null }?.completeDismissal()
            }
            first.cancelAndJoin(); second.cancelAndJoin(); third.cancelAndJoin()
            window.hidden = true
            window.rootViewController = null
        }
    }

    @Test fun competingReplacementsRecheckTheActiveSheetAfterDelayedDismissal() = runOnMainRunLoop {
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = Host()
        window.rootViewController = host
        window.makeKeyAndVisible()
        val session = IosDocumentPickerSession()
        val a = Sheet()
        val b = Sheet()
        val c = Sheet()
        val first = async(start = CoroutineStart.UNDISPATCHED) { session.pick(a) { host } }
        val lateA = assertNotNull(a.delegate)
        val second = async(start = CoroutineStart.UNDISPATCHED) { session.pick(b) { host } }
        val third = async(start = CoroutineStart.UNDISPATCHED) { session.pick(c) { host } }
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
            val lateB = assertNotNull(b.presentedDelegate)
            assertFalse(second.isCompleted)
            assertFalse(third.isCompleted)
            b.completeDismissal()
            repeat(4) { yield() }
            assertEquals(emptyList(), second.await())
            assertEquals(listOf(a, b, c), host.presentations)
            assertSame(c, host.attached)
            lateA.documentPicker(a, didPickDocumentsAtURLs = listOf(NSURL.fileURLWithPath("/tmp/late-a")))
            lateB?.documentPicker(b, didPickDocumentsAtURLs = listOf(NSURL.fileURLWithPath("/tmp/late-b")))
            assertFalse(third.isCompleted)
            c.delegate!!.documentPickerWasCancelled(c)
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
            window.hidden = true
            window.rootViewController = null
        }
    }
}
