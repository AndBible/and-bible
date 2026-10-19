@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.report

import kotlinx.coroutines.*
import net.bible.sharedui.assembly.iosStringTable
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*

class IosReportLifecycleTest {
    private fun awaitOnMain(block: suspend CoroutineScope.() -> Unit) {
        val scope = MainScope()
        try {
            val job = scope.async { block() }
            val deadline = NSDate.dateWithTimeIntervalSinceNow(15.0)
            while (!job.isCompleted && deadline.timeIntervalSinceNow > 0) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
            check(job.isCompleted) { "Report lifecycle timed out" }
            job.getCompleted()
        } finally { scope.cancel() }
    }

    private fun staged() = FileSystem.SYSTEM.list(NSTemporaryDirectory().toPath()).filter { it.name.startsWith("andbible-report-") }.toSet()

    @Test fun declinedConfirmationDoesNotStageOrSend() = awaitOnMain {
        val before = staged()
        var sends = 0
        val report = IosBugReport(iosStringTable("en"), confirm = { false }, send = { _, _, _, _ -> sends++ })
        report.report(IllegalStateException("not sent"))
        report.ai("model", "details", byteArrayOf(1), true)
        assertEquals(0, sends)
        assertEquals(before, staged())
    }

    @Test fun emptyAiLogNeverAsksConfirmation() = awaitOnMain {
        val report = IosBugReport(iosStringTable("en"), confirm = { error("Must not ask") }, send = { _, _, _, _ -> error("Must not send") })
        report.ai("supported", "details", byteArrayOf(), true)
        report.ai("unsupported", "details", byteArrayOf(1), false)
    }

    @Test fun confirmedAiReportPreservesGzipMimeRecipientAndSubject() = awaitOnMain {
        val bytes = net.bible.service.llm.agent.RawLlmLog.gzipCompress("conversation")
        var sent = false
        IosBugReport(iosStringTable("en"), confirm = { ai -> assertTrue(ai); true }, send = { to, subject, body, attachments ->
            sent = true
            assertEquals("errors.andbible@gmail.com", to)
            assertTrue(subject.startsWith("AI Bug Report v"))
            assertTrue(subject.endsWith(": model"))
            assertTrue("summary" in body)
            assertEquals("application/gzip", attachments.single().mime)
            assertContentEquals(bytes, attachments.single().bytes)
        }).ai("model", "summary", bytes, true)
        assertTrue(sent)
    }

    @Test fun noPresenterRemovesPreparedAttachments() = awaitOnMain {
        val before = staged()
        var path: okio.Path? = null
        IosMailOrShare {
            path = (staged() - before).single()
            assertEquals("payload", FileSystem.SYSTEM.read(path!! / "log.gz") { readUtf8() })
            null
        }.send("not-sent@example.invalid", "test", "body", listOf(Attachment("log.gz", "application/gzip", "payload".encodeToByteArray())))
        assertNotNull(path)
        assertFalse(FileSystem.SYSTEM.exists(path!!))
    }

    @Test fun coroutineCancellationDismissesAndDeletesStaging() = awaitOnMain {
        if (platform.MessageUI.MFMailComposeViewController.canSendMail()) return@awaitOnMain
        val before = staged()
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = UIViewController()
        window.rootViewController = host
        window.makeKeyAndVisible()
        try {
            val job = launch { IosMailOrShare { host }.send("test@example.invalid", "test", "body", listOf(Attachment("log.gz", "application/gzip", byteArrayOf(1)))) }
            withTimeout(5000) { while (host.presentedViewController == null) delay(20) }
            while (host.presentedViewController?.transitionCoordinator != null) delay(20)
            assertEquals(1, (staged() - before).size)
            job.cancelAndJoin()
            assertNull(host.presentedViewController)
            assertEquals(before, staged())
        } finally { window.hidden = true; window.rootViewController = null }
    }

    @Test fun shareCallbackKeepsAttachmentUntilDismissalAndCleansOnce() = awaitOnMain {
        // Simulator has no configured Mail account. Never invoke a real mail send action.
        if (platform.MessageUI.MFMailComposeViewController.canSendMail()) return@awaitOnMain
        val before = staged()
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = UIViewController()
        window.rootViewController = host
        window.makeKeyAndVisible()
        try {
            val job = launch {
                IosMailOrShare { host }.send("not-sent@example.invalid", "test", "body",
                    listOf(Attachment("log.gz", "application/gzip", "payload".encodeToByteArray())))
            }
            withTimeout(5000) { while (host.presentedViewController == null) delay(20) }
            val sheet = assertIs<UIActivityViewController>(host.presentedViewController)
            while (sheet.transitionCoordinator != null) delay(20)
            val path = (staged() - before).single()
            assertTrue(FileSystem.SYSTEM.exists(path / "log.gz"))
            sheet.popoverPresentationController?.let { assertSame(host.view, it.sourceView) }
            val completion = assertNotNull(sheet.completionWithItemsHandler)
            completion(null, false, null, null)
            completion(null, false, null, null)
            job.join()
            assertNull(host.presentedViewController)
            assertFalse(FileSystem.SYSTEM.exists(path))
            assertEquals(before, staged())
        } finally { window.hidden = true; window.rootViewController = null }
    }
}
