@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedcore.platform.files

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import platform.UIKit.*
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.*
import platform.UniformTypeIdentifiers.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosFilePickerTest {
    @Test fun applicationWildcardUsesData() {
        assertEquals(listOf(UTTypeData), contentTypesFor("application/*"))
    }
    @Test fun allWildcardUsesData() {
        assertEquals(listOf(UTTypeData), contentTypesFor("*/*"))
    }
    @Test fun zipUsesZipType() {
        assertTrue(contentTypesFor("application/zip").single().conformsToType(UTTypeZIP))
    }
    @Test fun unknownMimeUsesData() {
        assertEquals(listOf(UTTypeData), contentTypesFor("x-foo/bar"))
    }
    @Test fun terminalCallbacksReleaseOnceAndIgnoreLaterResults() {
        runOnMainRunLoop {
            var releases = 0
            val result = suspendCancellableCoroutine { continuation ->
                val presentation = FilePresentation(UIViewController(), continuation, DeliveryResult.CANCELLED,
                    release = { releases++ })
                presentation.finish(DeliveryResult.DELIVERED)
                presentation.finish(DeliveryResult.CANCELLED)
            }
            assertEquals(DeliveryResult.DELIVERED, result)
            assertEquals(1, releases)
        }
    }
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    @Test fun consecutiveRealPresentationsResumeOnlyAfterDismissal() {
        runOnMainRunLoop {
            val window = platform.UIKit.UIWindow(frame = platform.UIKit.UIScreen.mainScreen.bounds)
            val host = UIViewController()
            window.rootViewController = host
            window.makeKeyAndVisible()
            try {
                repeat(2) {
                    val sheet = platform.UIKit.UIDocumentPickerViewController(
                        forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
                    val result = suspendCancellableCoroutine { continuation ->
                        val presentation = FilePresentation(sheet, continuation, DeliveryResult.CANCELLED)
                        presentation.present(host)
                        // Use UIKit's actual presentation completion, not an unpresented stand-in.
                        fun finishWhenPresented() {
                            val transition = sheet.transitionCoordinator
                            if (transition != null) {
                                transition.animateAlongsideTransition(null) { presentation.finish(DeliveryResult.DELIVERED) }
                            } else presentation.finish(DeliveryResult.DELIVERED)
                        }
                        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { finishWhenPresented() }
                    }
                    assertEquals(DeliveryResult.DELIVERED, result)
                    assertNull(host.presentedViewController)
                    assertNull(sheet.presentingViewController)
                }
            } finally { window.hidden = true; window.rootViewController = null }
        }
    }

    @Test fun noPresenterCancelsPicker() = runOnMainRunLoop<Unit> {
        assertNull(IosFilePicker { null }.pickBackupFile())
    }
    @Test fun openInputReadsLocalCopy() = runTest {
        val path = NSTemporaryDirectory().toPath() / NSUUID().UUIDString
        try {
            FileSystem.SYSTEM.write(path) { writeUtf8("backup bytes") }
            IosFilePicker { null }.openInput(PickedSource(path.toString(), null, null)).buffer().use {
                assertEquals("backup bytes", it.readUtf8())
            }
        } finally { FileSystem.SYSTEM.delete(path, mustExist = false) }
    }
    @Test fun openInputReadsExactBackslashTokenNotNormalizedDecoy() = runTest {
        val root = "${NSTemporaryDirectory()}/backup-exact-url-${NSUUID().UUIDString}"
        val rootUrl = NSURL.fileURLWithPath(root, isDirectory = true)
        assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(rootUrl, true, null, null))
        var primary: Throwable? = null
        try {
            val selected = NSURL.fileURLWithPath("$root/picked\\backup.zip")
            assertTrue(NSString.create(string = "picked backup bytes").writeToURL(selected, true, NSUTF8StringEncoding, null))
            val decoy = "$root/picked".toPath()
            FileSystem.SYSTEM.createDirectory(decoy)
            FileSystem.SYSTEM.write(decoy / "backup.zip") { writeUtf8("wrong normalized backup bytes") }
            // The real picker stores NSURL.path as its token, not the file URL string.
            val source = PickedSource(selected.path!!, selected.lastPathComponent, "application/zip")
            IosFilePicker { null }.openInput(source).buffer().use {
                assertEquals("picked backup bytes", it.readUtf8())
            }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            // Okio cleanup would normalize the literal backslash filename too.
            if (!NSFileManager.defaultManager.removeItemAtURL(rootUrl, null)) {
                val cleanup = AssertionError("Failed to remove exact backup URL fixture")
                primary?.addSuppressed(cleanup) ?: throw cleanup
            }
        }
    }

    @Test fun openInputStreamsLargeBackupAndHonorsSourceLifecycle() = runTest {
        val path = NSTemporaryDirectory().toPath() / NSUUID().UUIDString
        val content = "backup contents\n".repeat(10000)
        try {
            FileSystem.SYSTEM.write(path) { writeUtf8(content) }
            val input = IosFilePicker { null }.openInput(PickedSource(path.toString(), null, null))
            try {
                val sink = Buffer()
                assertEquals(0L, input.read(sink, 0))
                assertEquals(0L, sink.size)
                assertFailsWith<IllegalArgumentException> { input.read(sink, -1) }
                var reads = 0
                while (true) {
                    val before = sink.size
                    val count = input.read(sink, 100000)
                    if (count == -1L) break
                    assertTrue(count in 1L..100000L)
                    assertEquals(count, sink.size - before)
                    reads++
                }
                assertTrue(reads > 1, "Backup is read in bounded chunks")
                assertEquals(content, sink.readUtf8())
                assertEquals(-1L, input.read(sink, 1))
            } finally { input.close() }
            input.close()
            assertFailsWith<IllegalStateException> { input.read(Buffer(), 1) }
        } finally { FileSystem.SYSTEM.delete(path, mustExist = false) }
    }

    @Test fun missingDeliveryFailsWithoutPresentation() = runOnMainRunLoop<Unit> {
        var presenterCalls = 0
        val result = IosFileDelivery { presenterCalls++; null }.deliver(
            (NSTemporaryDirectory().toPath() / NSUUID().UUIDString).toString(), "backup.zip",
            SaveOrShare.SAVE, "application/zip", "application/zip", null, null, "Export"
        )
        assertEquals(DeliveryResult.FAILED, result)
        assertEquals(0, presenterCalls)
    }
    @Test fun traversalExportNameFailsWithoutChangingSource() = runOnMainRunLoop<Unit> {
        val path = NSTemporaryDirectory().toPath() / NSUUID().UUIDString
        try {
            FileSystem.SYSTEM.write(path) { writeUtf8("original") }
            assertEquals(DeliveryResult.FAILED, IosFileDelivery { UIViewController() }.deliver(
                path.toString(), "../backup.zip", SaveOrShare.SAVE,
                "application/zip", "application/zip", null, null, "Export"
            ))
            assertEquals("original", FileSystem.SYSTEM.read(path) { readUtf8() })
        } finally { FileSystem.SYSTEM.delete(path, mustExist = false) }
    }
    @Test fun presenterIsResolvedAfterCopyAndCancellationRemovesStaging() = runOnMainRunLoop<Unit> {
        val temporary = NSTemporaryDirectory().toPath()
        val source = temporary / NSUUID().UUIDString
        val before = FileSystem.SYSTEM.list(temporary).toSet()
        var prepared: okio.Path? = null
        var calls = 0
        try {
            FileSystem.SYSTEM.write(source) { writeUtf8("complete backup") }
            val delivery = IosFileDelivery {
                calls++
                val directory = FileSystem.SYSTEM.list(temporary).single {
                    it !in before && it.name.startsWith("andbible-export-")
                }
                prepared = directory
                assertEquals("complete backup", FileSystem.SYSTEM.read(directory / "renamed.zip") { readUtf8() })
                null
            }
            assertEquals(DeliveryResult.CANCELLED, delivery.deliver(
                source.toString(), "renamed.zip", SaveOrShare.SAVE,
                "application/zip", "application/zip", null, null, "Export"
            ))
            assertEquals(1, calls)
            assertTrue(prepared != null)
            assertTrue(!FileSystem.SYSTEM.exists(prepared))
            assertEquals("complete backup", FileSystem.SYSTEM.read(source) { readUtf8() })
        } finally {
            prepared?.let { FileSystem.SYSTEM.deleteRecursively(it, mustExist = false) }
            FileSystem.SYSTEM.delete(source, mustExist = false)
        }
    }
    @Test fun existingDeliveryWithoutPresenterIsCancelled() = runOnMainRunLoop<Unit> {
        val path = NSTemporaryDirectory().toPath() / NSUUID().UUIDString
        try {
            FileSystem.SYSTEM.write(path) { writeUtf8("backup") }
            for (mode in SaveOrShare.entries) {
                assertEquals(DeliveryResult.CANCELLED, IosFileDelivery { null }.deliver(
                    path.toString(), "backup.zip", mode, "application/zip", "application/zip", null, null, "Export"
                ))
            }
        } finally { FileSystem.SYSTEM.delete(path, mustExist = false) }
    }
}
