@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.report

import kotlinx.coroutines.*
import net.bible.sharedui.assembly.hostStringTable
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.GzipSource
import okio.buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*
import net.bible.sharedui.host.report.Attachment
import net.bible.sharedui.host.report.ReportDevice
import net.bible.sharedcore.log.Log

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
        val report = IosBugReport(hostStringTable("en"), logSnapshot = { "" }, confirm = { true }, send = { _, _, _, _ ->
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

    private fun gunzip(bytes: ByteArray) = GzipSource(Buffer().write(bytes)).buffer().readUtf8()

    @Test fun exportedLogIncludesFreshFactsAfterRealHistoryOverflow() = awaitOnMain {
        val originalSink = Log.sinkOverride
        try {
            Log.sinkOverride = null
            val marker = NSUUID().UUIDString
            Log.i("ReportOverflow", "$marker early")
            repeat(300) { Log.i("ReportOverflow", "$marker filler $it ${"x".repeat(1024)}") }
            Log.i("ReportOverflow", "$marker latest")
            val device = ReportDevice("synthetic.app", "1.2.3", "iOS 18.0", "test model",
                listOf("Build" to "456", "Commit" to "abc123", "Platform" to "iOS app on Mac", "Locale" to "en"))
            val extra = Attachment("extra.txt", "text/plain", "extra".encodeToByteArray())
            var sent = false
            IosBugReport(hostStringTable("en"), confirm = { assertFalse(it); true },
                reportDevice = { device }, crashDiagnostic = { null }, failure = { throw it },
                send = { to, subject, body, attachments ->
                    sent = true
                    assertEquals("errors.andbible@gmail.com", to)
                    assertEquals("Bug Report v1.2.3", subject)
                    assertTrue("App id: synthetic.app" in body)
                    assertTrue("Version: 1.2.3" in body)
                    assertTrue("OS version: iOS 18.0" in body)
                    assertTrue("Locale: en" in body)
                    assertEquals(extra, attachments[1])
                    val attachment = attachments.first()
                    assertEquals("app_log.txt.gz", attachment.name)
                    assertEquals("application/gzip", attachment.mime)
                    val text = gunzip(attachment.bytes)
                    assertTrue(text.startsWith("AndBible diagnostic log\nVersion: 1.2.3\nBuild: 456\nCommit: abc123\nOS: iOS 18.0\nPlatform: iOS app on Mac\n\nRecent log history (bounded):\n"))
                    assertFalse("$marker early" in text)
                    assertTrue("$marker latest" in text)
                }).report(extraAttachments = listOf(extra))
            assertTrue(sent)
        } finally { Log.sinkOverride = originalSink }
    }

    @Test fun emptySnapshotUsesUnknownForBlankAndMissingFacts() = awaitOnMain {
        var text: String? = null
        IosBugReport(hostStringTable("en"), confirm = { true }, failure = { throw it },
            crashDiagnostic = { null }, logSnapshot = { "" },
            reportDevice = { ReportDevice("", "  ", "", "", listOf("Build" to " ", "Platform" to "")) },
            send = { _, _, _, attachments -> text = gunzip(attachments.single().bytes) }).report()
        assertEquals("AndBible diagnostic log\nVersion: unknown\nBuild: unknown\nCommit: unknown\nOS: unknown\nPlatform: unknown\n\nRecent log history (bounded):\n", text)
    }

    @Test fun absentPlatformDefaultsToIosAndReportReadsProvidersForEachExport() = awaitOnMain {
        var snapshot = "first history\n"
        var device = ReportDevice("app", "one", "iOS one", "model", listOf("Build" to "11", "Commit" to "aaa"))
        val exports = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val reporter = IosBugReport(hostStringTable("en"), confirm = { true }, failure = { throw it },
            crashDiagnostic = { null }, logSnapshot = { snapshot }, reportDevice = { device },
            send = { _, _, body, attachments -> bodies += body; exports += gunzip(attachments.single().bytes) })
        reporter.report()
        snapshot = "second history\n"
        device = ReportDevice("app", "two", "iOS two", "model", listOf("Build" to "22", "Commit" to "bbb"))
        reporter.report()
        assertEquals(listOf(
            "AndBible diagnostic log\nVersion: one\nBuild: 11\nCommit: aaa\nOS: iOS one\nPlatform: iOS\n\nRecent log history (bounded):\nfirst history\n",
            "AndBible diagnostic log\nVersion: two\nBuild: 22\nCommit: bbb\nOS: iOS two\nPlatform: iOS\n\nRecent log history (bounded):\nsecond history\n"
        ), exports)
        assertTrue("Version: one" in bodies[0])
        assertTrue("Version: two" in bodies[1])
    }

    @Test fun eachReportReadsDeviceOnceOnMainAndUsesSameSnapshot() = awaitOnMain {
        var calls = 0
        val mainThreadCalls = mutableListOf<Boolean>()
        val messages = mutableListOf<Triple<String, String, String>>()
        val reporter = IosBugReport(hostStringTable("en"), confirm = { true }, failure = { throw it },
            crashDiagnostic = { null }, logSnapshot = { "" },
            reportDevice = {
                mainThreadCalls += NSThread.isMainThread
                val version = "snapshot-${++calls}"
                ReportDevice("app", version, "iOS", "model", emptyList())
            },
            send = { _, subject, body, attachments ->
                messages += Triple(subject, body, gunzip(attachments.single().bytes))
            })

        reporter.report()
        assertEquals(1, calls)
        reporter.report()
        assertEquals(2, calls)
        assertEquals(listOf(true, true), mainThreadCalls)
        assertEquals(2, messages.size)
        messages.forEachIndexed { index, (subject, body, heading) ->
            val version = "snapshot-${index + 1}"
            assertEquals("Bug Report v$version", subject)
            assertTrue("Version: $version" in body)
            assertTrue(heading.startsWith("AndBible diagnostic log\nVersion: $version\n"))
        }
    }

    @Test fun envelopeWrittenBySwiftEncoderIsAttachedGzipped() = awaitOnMain {
        // The fixture comes from the real JSONEncoder: base64 with '/' (escaped as \/), '+' and '='.
        val path = (NSTemporaryDirectory() + NSUUID().UUIDString).toPath()
        try {
            FileSystem.SYSTEM.write(path) { writeUtf8(CRASH_ENVELOPE_FIXTURE) }
            val attachment = assertNotNull(readCrashDiagnostic(path, CRASH_ENVELOPE_FIXTURE_OCCURRENCE + 10))
            assertEquals(CRASH_DIAGNOSTIC_ATTACHMENT, attachment.name)
            assertEquals("application/gzip", attachment.mime)
            assertEquals(CRASH_ENVELOPE_FIXTURE_PAYLOAD, gunzip(attachment.bytes))
        } finally { FileSystem.SYSTEM.delete(path, mustExist = false) }
    }

    @Test fun swiftEnvelopeIsAttachedOnlyWhenFreshAndValid() = awaitOnMain {
        val path = (NSTemporaryDirectory() + NSUUID().UUIDString).toPath()
        val fs = FileSystem.SYSTEM
        val now = 2_000_000.0
        suspend fun report(envelope: String, confirmed: Boolean = true): List<Attachment>? {
            fs.write(path) { writeUtf8(envelope) }
            var sent: List<Attachment>? = null
            IosBugReport(hostStringTable("en"), logSnapshot = { "" }, confirm = { confirmed },
                send = { _, _, _, attachments -> sent = attachments },
                failure = { throw it }, crashDiagnostic = { readCrashDiagnostic(path, now) }).report()
            return sent
        }
        try {
            // JSONEncoder encodes Swift Data as base64; this is the array [{"x":1}].
            fun envelope(time: Double, payload: String) = """{"occurrence":$time,"payload":"$payload"}"""
            val sent = assertNotNull(report(envelope(now - 1, "W3sieCI6MX1d")))
            val attachment = sent.single { it.name == CRASH_DIAGNOSTIC_ATTACHMENT }
            assertEquals("application/gzip", attachment.mime)
            assertEquals("[{\"x\":1}]", gunzip(attachment.bytes))
            for (invalid in listOf(envelope(now - 86401, "W3sieCI6MX1d"), envelope(now + 1, "W3sieCI6MX1d"),
                "not json", envelope(now, "bad!"), envelope(now, ""))) {
                assertTrue(assertNotNull(report(invalid)).none { it.name == CRASH_DIAGNOSTIC_ATTACHMENT })
            }
            val oversized = ByteArray(2 * 1024 * 1024 + 1).toByteString().base64()
            assertTrue(assertNotNull(report(envelope(now, oversized))).none { it.name == CRASH_DIAGNOSTIC_ATTACHMENT })
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
