package net.bible.sharedui.desktop.misc

import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedcore.platform.files.DeliveryResult

class DesktopBugReportTest {
    @Test
    fun reportRespectsSizeCap() = runBlocking {
        val root = Files.createTempDirectory("report")
        var saved = ""
        val report = DesktopBugReport(root, "1.2", { "z".repeat(2_000_000) },
            { path, _, _ -> saved = GZIPInputStream(Files.newInputStream(path)).bufferedReader().readText(); DeliveryResult.DELIVERED }, {})
        report.report(null)
        assertTrue(saved.length <= 1024 * 1024)
        assertTrue(saved.isNotEmpty())
    }

    @Test
    fun mailtoCarriesIosSubject() = runBlocking {
        val root = Files.createTempDirectory("report-mail")
        var url = ""
        val report = DesktopBugReport(root, "1.2", { "log" },
            { _, _, _ -> DeliveryResult.DELIVERED }, { url = it })
        report.report(null)
        assertTrue(url.startsWith("mailto:errors.andbible@gmail.com?"))
        assertTrue("Bug+Report+v1.2" in url || "Bug%20Report%20v1.2" in url)
    }

    @Test
    fun aiReportBoundsExpandedGzip() = runBlocking {
        val root = Files.createTempDirectory("report-ai")
        val bytes = java.io.ByteArrayOutputStream().also { output ->
            java.util.zip.GZIPOutputStream(output).use { it.write(ByteArray(5_000_000) { 'x'.code.toByte() }) }
        }.toByteArray()
        var size = 0
        DesktopBugReport(root, "1.2", { "" }, { path, _, _ ->
            size = GZIPInputStream(Files.newInputStream(path)).use { it.readBytes().size }
            DeliveryResult.DELIVERED
        }, {}).ai("model", "summary", bytes, true)
        assertTrue(size in 1..1024 * 1024)
    }

    @Test
    fun recoveryReportKeepsGzipAndIncludesItsPathInMail() = runBlocking {
        val root = Files.createTempDirectory("recovery-report")
        var url = ""
        recoveryBugReport(root, "1.2", { "diagnostic log" }, "Introduction", { url = it }).report(null)
        val files = Files.list(root.resolve("reports")).use { it.toList() }
        assertTrue(files.size == 1)
        assertTrue(GZIPInputStream(Files.newInputStream(files.single())).bufferedReader().readText().contains("diagnostic log"))
        val decoded = java.net.URLDecoder.decode(url, Charsets.UTF_8)
        assertTrue(decoded.startsWith("mailto:errors.andbible@gmail.com?subject=Bug Report v1.2&body="))
        assertTrue(files.single().toString() in decoded)
    }

    @Test
    fun mailBodyContainsIntroductionAndDeviceDetails() = runBlocking {
        val root = Files.createTempDirectory("report-body")
        var url = ""
        DesktopBugReport(root, "1.2", { "private log" },
            { _, _, _ -> DeliveryResult.DELIVERED }, { url = it },
            introduction = { "Report introduction" }).report(null)
        val decoded = java.net.URLDecoder.decode(url, Charsets.UTF_8)
        assertTrue("body=" in decoded)
        assertTrue("Report introduction" in decoded)
        assertTrue("net.bible.desktop" in decoded)
        assertFalse("private log" in decoded)
    }

    @Test
    fun largeCrashAndCurrentExceptionKeepMailBoundedAndDiagnosticsInAttachment() = runBlocking {
        val root = Files.createTempDirectory("large-report")
        val crash = "crash-marker-" + "%".repeat(100_000) + "-crash-end"
        val current = "current-marker-" + "!".repeat(100_000) + "-current-end"
        Files.writeString(root.resolve("last_crash.txt"), crash)
        var url = ""
        var saved = ""
        DesktopBugReport(root, "1.2", { "log" }, { path, _, _ ->
            saved = GZIPInputStream(Files.newInputStream(path)).bufferedReader().readText()
            DeliveryResult.DELIVERED
        }, { url = it }, introduction = { "Introduction" }).report(IllegalStateException(current))
        assertTrue(url.toByteArray().size <= 8192)
        val decoded = java.net.URLDecoder.decode(url, Charsets.UTF_8)
        assertTrue("Introduction" in decoded && "net.bible.desktop" in decoded)
        assertTrue(crash in saved && current in saved)
    }

    @Test
    fun recoveryLargeDiagnosticsKeepBoundedMailAndRetainedGzip() = runBlocking {
        val root = Files.createTempDirectory("large-recovery")
        Files.createDirectories(root.resolve("logs"))
        val crash = "crash-marker-" + "%".repeat(100_000) + "-crash-end"
        val current = "current-marker-" + "!".repeat(100_000) + "-current-end"
        Files.writeString(root.resolve("logs/last_crash.txt"), crash)
        var url = ""
        recoveryBugReport(root, "1.2", { "log" }, "Introduction", { url = it })
            .report(IllegalStateException(current))
        val file = Files.list(root.resolve("reports")).use { it.toList().single() }
        val saved = GZIPInputStream(Files.newInputStream(file)).bufferedReader().readText()
        val decoded = java.net.URLDecoder.decode(url, Charsets.UTF_8)
        assertTrue(url.toByteArray().size <= 8192)
        assertTrue("Introduction" in decoded && "net.bible.desktop" in decoded && file.toString() in decoded)
        assertTrue(crash in saved && current in saved)
    }

    @Test
    fun recoveryRetainsGzipWhenMailLaunchFails() = runBlocking {
        val root = Files.createTempDirectory("failed-recovery")
        recoveryBugReport(root, "1.2", { "diagnostic log" }, "Introduction", { error("mail failed") }).report(null)
        val file = Files.list(root.resolve("reports")).use { it.toList().single() }
        assertTrue(GZIPInputStream(Files.newInputStream(file)).bufferedReader().readText().contains("diagnostic log"))
    }

    @Test
    fun cancelledSaveDoesNotOpenMail() = runBlocking {
        val root = Files.createTempDirectory("report-cancel")
        var opened = false
        DesktopBugReport(root, "1.2", { "log" },
            { _, _, _ -> DeliveryResult.CANCELLED }, { opened = true }).report(null)
        assertFalse(opened)
    }
}
