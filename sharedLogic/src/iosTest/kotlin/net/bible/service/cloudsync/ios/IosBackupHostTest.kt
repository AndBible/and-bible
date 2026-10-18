package net.bible.service.cloudsync.ios

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import okio.Buffer
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosBackupHostTest {
    private val picked = PickedSource("tok", "b.zip", null)
    private val source = Buffer()
    private val picker = object : FilePicker {
        var mime: String? = null
        override suspend fun pickBackupFile(mime: String): PickedSource? { this.mime = mime; return picked }
        override suspend fun openInput(source: PickedSource): Source = this@IosBackupHostTest.source
    }
    private val delivery = object : FileDelivery {
        var args: List<Any?>? = null
        override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String,
                                     saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
            args = listOf(file, fileName, mode)
            return DeliveryResult.CANCELLED
        }
    }
    private val controller = AppDialogController()
    private val host = IosBackupHost(picker, delivery, { controller }, { "OK" })

    @Test fun pickerAndDeliveryDelegate() = runTest {
        assertSame(picked, host.pickBackupFile("application/zip"))
        assertEquals("application/zip", picker.mime)
        assertSame(source, host.openInput(picked))
        assertEquals(DeliveryResult.CANCELLED,
            host.deliver("/f", "n.zip", SaveOrShare.SHARE, "a/b", "c/d", null, null, "t"))
        assertEquals(listOf<Any?>("/f", "n.zip", SaveOrShare.SHARE), delivery.args)
    }

    @Test fun showErrorReportRaisesOneMessageDialogAndReturnsWhenAnswered() = runTest {
        var returned = false
        val job = launch { host.showErrorReport("boom <x>", null); returned = true }
        runCurrent()
        val shown = assertNotNull(controller.pending.value)
        val req = assertIs<AppDialogRequest.Message>(shown.request)
        assertTrue("boom" in req.message)
        assertTrue("<x>" !in req.message)
        assertEquals("OK", req.confirmText)
        assertTrue(!returned)
        controller.respond(shown.id, AppDialogResult.Ok)
        runCurrent()
        job.join()
        assertTrue(returned)
        assertNull(controller.pending.value)
    }

    @Test fun restoreModulesIsPendingNoOp() = runTest {
        host.restoreModules()
        assertNull(controller.pending.value)
    }
}
