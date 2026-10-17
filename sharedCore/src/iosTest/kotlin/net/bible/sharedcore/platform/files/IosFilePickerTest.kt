package net.bible.sharedcore.platform.files

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import platform.UIKit.*
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.UniformTypeIdentifiers.*
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun noPresenterCancelsPicker() = runTest {
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
    @Test fun missingDeliveryFailsWithoutPresentation() = runTest {
        var presenterCalls = 0
        val result = IosFileDelivery { presenterCalls++; null }.deliver(
            (NSTemporaryDirectory().toPath() / NSUUID().UUIDString).toString(), "backup.zip",
            SaveOrShare.SAVE, "application/zip", "application/zip", null, null, "Export"
        )
        assertEquals(DeliveryResult.FAILED, result)
        assertEquals(0, presenterCalls)
    }
    @Test fun traversalExportNameFailsWithoutChangingSource() = runTest {
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
    @Test fun presenterIsResolvedAfterCopyAndCancellationRemovesStaging() = runTest {
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
    @Test fun existingDeliveryWithoutPresenterIsCancelled() = runTest {
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
