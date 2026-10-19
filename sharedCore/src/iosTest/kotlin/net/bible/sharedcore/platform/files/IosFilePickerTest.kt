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
    // Real document/alert lifecycle coverage lives in app-hosted ReportLifecycleTests.

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
