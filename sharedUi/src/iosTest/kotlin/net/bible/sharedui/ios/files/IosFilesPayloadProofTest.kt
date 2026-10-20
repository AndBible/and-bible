@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.files

import kotlinx.coroutines.*
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.ios.ports.IosDocumentPicker
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Tests the diagnostic's ability to reject wrong destinations, not actual system picker presentation. */
class IosFilesPayloadProofTest {
    private class RecordingHost : UIViewController(null, null) {
        var picker: UIDocumentPickerViewController? = null
        override fun presentViewController(viewControllerToPresent: UIViewController, animated: Boolean, completion: (() -> Unit)?) {
            picker = viewControllerToPresent as UIDocumentPickerViewController
            completion?.invoke()
        }
    }

    @Test fun createRejectsWritableSameNameDecoyInsteadOfSelectedDestination() = onMain {
        val name = "proof-decoy-${NSUUID().UUIDString}.csv"
        val folder = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("proof-selected-${NSUUID().UUIDString}", true)!!
        val expected = folder.URLByAppendingPathComponent(name)!!
        val decoyDir = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("proof-${NSUUID().UUIDString}", true)!!
        val decoy = decoyDir.URLByAppendingPathComponent(name)!!
        val manager = NSFileManager.defaultManager
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = RecordingHost()
        window.rootViewController = host
        window.makeKeyAndVisible()
        try {
            assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(folder, true, null, null))
            assertTrue(manager.createDirectoryAtURL(decoyDir, true, null, null))
            val result = async {
                runCatching { IosFilesPayloadProof.create(IosDocumentPicker { host }, name, DocumentRef(folder.absoluteString!!)) }
            }
            withTimeout(10_000) { while (host.picker == null) delay(1) }
            // The selected destination exists but is empty; the picker incorrectly returns a different
            // writable URL with the same name. A proof that re-reads only that URL must NOT pass.
            assertTrue(NSString.create(string = "").writeToURL(expected, false, NSUTF8StringEncoding, null))
            assertTrue(NSString.create(string = "").writeToURL(decoy, false, NSUTF8StringEncoding, null))
            val sheet = assertNotNull(host.picker)
            assertNotNull(sheet.delegate).documentPicker(sheet, didPickDocumentsAtURLs = listOf(decoy))
            val outcome = result.await()
            assertTrue(outcome.isFailure, "Diagnostic falsely verified a same-name decoy while the selected file stayed empty")
            assertEquals(0uL, assertNotNull(NSData.dataWithContentsOfURL(expected)).length,
                "The selected destination must remain the independent oracle")
        } finally {
            manager.removeItemAtURL(folder, null)
            manager.removeItemAtURL(decoyDir, null)
            window.hidden = true
            window.rootViewController = null
        }
    }

    @Test fun createAcceptsSelectedDestinationAndWritesPayloadThere() = onMain {
        val name = "proof-correct-${NSUUID().UUIDString}.csv"
        val folder = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("proof-selected-${NSUUID().UUIDString}", true)!!
        val expected = folder.URLByAppendingPathComponent(name)!!
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = RecordingHost()
        window.rootViewController = host
        window.makeKeyAndVisible()
        try {
            assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(folder, true, null, null))
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(expected.path!!))
            val result = async { IosFilesPayloadProof.create(IosDocumentPicker { host }, name, DocumentRef(folder.absoluteString!!)) }
            withTimeout(10_000) { while (host.picker == null) delay(1) }
            assertTrue(NSString.create(string = "").writeToURL(expected, false, NSUTF8StringEncoding, null))
            val sheet = assertNotNull(host.picker)
            assertNotNull(sheet.delegate).documentPicker(sheet, didPickDocumentsAtURLs = listOf(expected))
            result.await()
            assertEquals("AndBible files port payload\n$name\nsecond line\n",
                NSString.stringWithContentsOfURL(expected, NSUTF8StringEncoding, null))
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(folder, null)
            window.hidden = true
            window.rootViewController = null
        }
    }

    private fun onMain(block: suspend CoroutineScope.() -> Unit) {
        check(NSThread.isMainThread)
        val result = CoroutineScope(Dispatchers.Main).async(block = block)
        val started = TimeSource.Monotonic.markNow()
        while (!result.isCompleted) {
            if (started.elapsedNow() > 20.seconds) {
                result.cancel()
                fail("Payload proof regression timed out")
            }
            NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        result.getCompleted()
    }
}
