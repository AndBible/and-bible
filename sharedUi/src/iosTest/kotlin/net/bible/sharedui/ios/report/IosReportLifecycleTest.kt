@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.report

import kotlinx.coroutines.*
import net.bible.sharedui.assembly.hostStringTable
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*
import net.bible.sharedui.host.report.Attachment

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

    @Test fun throwingSenderLeavesReporterUsableWithoutUncaughtFailure() = awaitOnMain {
        val problem = IllegalStateException("staging unavailable")
        val errors = mutableListOf<Exception>()
        var attempts = 0
        val report = IosBugReport(hostStringTable("en"), confirm = { true }, send = { _, _, _, _ ->
            if (attempts++ == 0) throw problem
        }, failure = { errors += it })
        report.report()
        report.report()
        assertEquals(2, attempts)
        assertEquals(listOf<Exception>(problem), errors)
    }

    @Test fun declinedConfirmationDoesNotStageOrSend() = awaitOnMain {
        val before = staged()
        var sends = 0
        val report = IosBugReport(hostStringTable("en"), confirm = { false }, send = { _, _, _, _ -> sends++ })
        report.report(IllegalStateException("not sent"))
        report.ai("model", "details", byteArrayOf(1), true)
        assertEquals(0, sends)
        assertEquals(before, staged())
    }

    @Test fun swiftEnvelopeIsAttachedOnlyWhenFreshAndValid() = awaitOnMain {
        val path = (NSTemporaryDirectory() + NSUUID().UUIDString).toPath()
        val fs = FileSystem.SYSTEM
        val now = 2_000_000.0
        suspend fun report(envelope: String, confirmed: Boolean = true): List<Attachment>? {
            fs.write(path) { writeUtf8(envelope) }
            var sent: List<Attachment>? = null
            IosBugReport(hostStringTable("en"), confirm = { confirmed },
                send = { _, _, _, attachments -> sent = attachments },
                failure = { throw it }, crashDiagnostic = { readCrashDiagnostic(path, now) }).report()
            return sent
        }
        try {
            // JSONEncoder encodes Swift Data as base64; this is the array [{"x":1}].
            fun envelope(time: Double, payload: String) = """{"occurrence":$time,"payload":"$payload"}"""
            val sent = assertNotNull(report(envelope(now - 1, "W3sieCI6MX1d")))
            val attachment = sent.single { it.name == "recent_crash_diagnostic.json" }
            assertEquals("application/json", attachment.mime)
            assertEquals("[{\"x\":1}]", attachment.bytes.decodeToString())
            for (invalid in listOf(envelope(now - 86401, "W3sieCI6MX1d"), envelope(now + 1, "W3sieCI6MX1d"),
                "not json", envelope(now, "bad!"), envelope(now, ""))) {
                assertTrue(assertNotNull(report(invalid)).none { it.name == "recent_crash_diagnostic.json" })
            }
            val oversized = ByteArray(2 * 1024 * 1024 + 1).toByteString().base64()
            assertTrue(assertNotNull(report(envelope(now, oversized))).none { it.name == "recent_crash_diagnostic.json" })
            assertNull(report(envelope(now, "W3sieCI6MX1d"), confirmed = false))
        } finally { fs.delete(path, mustExist = false) }
    }

    @Test fun emptyAiLogNeverAsksConfirmation() = awaitOnMain {
        val report = IosBugReport(hostStringTable("en"), confirm = { error("Must not ask") }, send = { _, _, _, _ -> error("Must not send") })
        report.ai("supported", "details", byteArrayOf(), true)
        report.ai("unsupported", "details", byteArrayOf(1), false)
    }

    @Test fun confirmedAiReportPreservesGzipMimeRecipientAndSubject() = awaitOnMain {
        val bytes = net.bible.service.llm.agent.RawLlmLog.gzipCompress("conversation")
        var sent = false
        IosBugReport(hostStringTable("en"), confirm = { ai -> assertTrue(ai); true }, send = { to, subject, body, attachments ->
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

    // Real modal/cancellation coverage lives in app-hosted ReportLifecycleTests (UIApplication scene).

    @Test fun iosReportFactsCarryTheLocale() {
        assertTrue(iosReportFacts().any { it.first == "Locale" && it.second.isNotBlank() })
    }
}
