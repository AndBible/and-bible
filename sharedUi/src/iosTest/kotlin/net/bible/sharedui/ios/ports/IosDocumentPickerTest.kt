@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.*
import net.bible.sharedcore.platform.files.iosDocumentContentTypes
import net.bible.sharedui.app.ports.DocumentRef
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class IosDocumentPickerTest {
    private class Host : UIViewController(null, null) {
        var picker: UIDocumentPickerViewController? = null
        override fun presentViewController(viewControllerToPresent: UIViewController, animated: Boolean, completion: (() -> Unit)?) {
            picker = viewControllerToPresent as UIDocumentPickerViewController
            completion?.invoke()
        }
    }

    @Test fun cancelReturnsEmptyOnce() = onMain {
        withHost { host ->
            val result = async { IosDocumentPicker { host }.openDocuments(listOf("application/zip"), true) }
            yield()
            val sheet = assertNotNull(host.picker)
            assertTrue(sheet.allowsMultipleSelection)
            val delegate = assertNotNull(sheet.delegate)
            delegate.documentPickerWasCancelled(sheet)
            delegate.documentPicker(sheet, didPickDocumentsAtURLs = listOf(NSURL.fileURLWithPath("/tmp/late.zip")))
            assertEquals(emptyList(), result.await())
            assertNull(sheet.delegate)
        }
    }

    @Test fun secondOpenCompletesTheFirstWithEmpty() = onMain {
        withHost { host ->
            val picker = IosDocumentPicker { host }
            val first = async { picker.openDocuments(listOf("application/zip"), false) }
            yield()
            val oldSheet = assertNotNull(host.picker)
            val second = async { picker.openDocuments(listOf("application/epub+zip"), false) }
            yield()
            assertEquals(emptyList(), first.await())
            assertNull(oldSheet.delegate)
            val sheet = assertNotNull(host.picker)
            assertNotSame(oldSheet, sheet)
            val urls = listOf(NSURL.fileURLWithPath("/tmp/a.epub"), NSURL.fileURLWithPath("/tmp/b.epub"))
            sheet.delegate!!.documentPicker(sheet, didPickDocumentsAtURLs = urls)
            assertEquals(urls.map { DocumentRef(it.absoluteString!!) }, second.await())
        }
    }

    @Test fun createReturnsTheMovedDestination() = onMain {
        withHost { host ->
            val temporary = NSTemporaryDirectory().toPath()
            val before = FileSystem.SYSTEM.list(temporary).toSet()
            val result = async { IosDocumentPicker { host }.createDocument("export.csv", "text/csv") }
            while (host.picker == null) delay(1)
            val staging = FileSystem.SYSTEM.list(temporary).single { it !in before && it.name.startsWith("andbible-document-") }
            val staged = staging / "export.csv"
            assertEquals(0L, FileSystem.SYSTEM.metadata(staged).size)
            val destination = NSURL.fileURLWithPath((temporary / "moved-${NSUUID().UUIDString}.csv").toString())
            try {
                FileSystem.SYSTEM.atomicMove(staged, destination.path!!.toPath())
                val sheet = assertNotNull(host.picker)
                sheet.delegate!!.documentPicker(sheet, didPickDocumentsAtURLs = listOf(destination))
                assertEquals(DocumentRef(destination.absoluteString!!), result.await())
                assertFalse(FileSystem.SYSTEM.exists(staging))
                assertTrue(FileSystem.SYSTEM.exists(destination.path!!.toPath()))
            } finally {
                FileSystem.SYSTEM.deleteRecursively(staging, mustExist = false)
                FileSystem.SYSTEM.delete(destination.path!!.toPath(), mustExist = false)
            }
        }
    }

    @Test fun folderPickReturnsAFolderUrl() = onMain {
        withHost { host ->
            val result = async { IosDocumentPicker { host }.openDocumentTree() }
            yield()
            val sheet = assertNotNull(host.picker)
            val url = NSURL.fileURLWithPath(NSTemporaryDirectory(), isDirectory = true)
            sheet.delegate!!.documentPicker(sheet, didPickDocumentsAtURLs = listOf(url))
            assertEquals(DocumentRef(url.absoluteString!!), result.await())
        }
    }

    @Test fun contentTypesForEpubAndZip() {
        assertEquals("org.idpf.epub-container", iosDocumentContentTypes(listOf("application/epub+zip")).single().identifier)
        assertEquals("public.zip-archive", iosDocumentContentTypes(listOf("application/zip")).single().identifier)
    }

    @Test fun noPresenterReturnsCancelledResults() = onMain {
        val picker = IosDocumentPicker { null }
        assertEquals(emptyList(), picker.openDocuments(listOf("*/*"), false))
        assertNull(picker.createDocument("export.csv", "text/csv"))
        assertNull(picker.openDocumentTree())
        assertNull(picker.resumeDocumentTree())
        assertEquals(emptyList(), picker.resumeOpenDocuments())
    }

    @Test fun coroutineCancellationClearsDelegate() = onMain {
        withHost { host ->
            val result = async { IosDocumentPicker { host }.openDocumentTree() }
            yield()
            val sheet = assertNotNull(host.picker)
            result.cancelAndJoin()
            yield()
            assertNull(sheet.delegate)
        }
    }

    private suspend fun CoroutineScope.withHost(block: suspend CoroutineScope.(Host) -> Unit) {
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = Host()
        window.rootViewController = host
        window.makeKeyAndVisible()
        try { block(host) } finally { window.hidden = true; window.rootViewController = null }
    }

    // Spin UIKit's main queue rather than blocking it with runTest/runBlocking.
    private fun onMain(block: suspend CoroutineScope.() -> Unit) {
        check(NSThread.isMainThread)
        val result = CoroutineScope(Dispatchers.Main).async(block = block)
        val started = TimeSource.Monotonic.markNow()
        while (!result.isCompleted) {
            if (started.elapsedNow() > 20.seconds) {
                result.cancel()
                fail("Document picker test timed out")
            }
            NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        result.getCompleted()
    }
}
