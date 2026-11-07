package net.bible.sharedui.desktop.misc

import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedui.host.report.BUG_REPORT_EMAIL

class DesktopBugReportTest {
    @Test
    fun headerHasJavaLocaleAndTheChosenEngine() {
        net.bible.sharedcore.log.ReportFacts.global.set("Web engine", "test-engine")
        val body = net.bible.sharedui.host.report.reportDetails(
            net.bible.sharedui.host.report.ReportDevice("net.bible.desktop", "1.0", "Linux 6", "amd64", desktopReportFacts()), 0, "", null)
        listOf("Java: ", "Locale: ", "Web engine: test-engine", "OS version: Linux 6").forEach { assertTrue(it in body, body) }
    }

    @Test
    fun reportRespectsSizeCap() = runBlocking {
        val root = Files.createTempDirectory("report")
        var saved = ""
        val report = DesktopBugReport(root, "1.2", { "z".repeat(2_000_000) },
            { path, _, _ -> saved = GZIPInputStream(Files.newInputStream(path)).bufferedReader().readText(); DeliveryResult.DELIVERED }, { true })
        report.report(null)
        assertTrue(saved.length <= 1024 * 1024)
        assertTrue(saved.isNotEmpty())
    }

    @Test
    fun mailtoCarriesIosSubject() = runBlocking {
        val root = Files.createTempDirectory("report-mail")
        var url = ""
        val report = DesktopBugReport(root, "1.2", { "log" },
            { _, _, _ -> DeliveryResult.DELIVERED }, { url = it; true })
        report.report(null)
        assertTrue(url.startsWith("mailto:errors.andbible@gmail.com?"))
        assertTrue("Bug+Report+v1.2" in url || "Bug%20Report%20v1.2" in url)
    }

    private fun instructionsReport(
        result: DeliveryResult = DeliveryResult.DELIVERED,
        open: suspend (String) -> Boolean,
        shown: MutableList<BugReportInstructions>,
    ) = DesktopBugReport(Files.createTempDirectory("report-instr"), "1.2", { "log" },
        { _, _, _ -> result }, open, savedPath = { "/home/u/andbible-report.gz" }, instructions = { shown += it })

    @Test
    fun instructionsShownWithPathAddressAndSubjectWhenMailClientDidNotOpen() = runBlocking {
        val shown = mutableListOf<BugReportInstructions>()
        instructionsReport(open = { false }, shown = shown).report(null)
        val only = shown.single()
        assertEquals(BUG_REPORT_EMAIL, only.address)
        assertEquals("AndBible 1.2 bug: <short description>", only.subject)
        assertEquals("/home/u/andbible-report.gz", only.savedPath)
    }

    @Test
    fun instructionsShownWhenOpeningMailThrows() = runBlocking {
        val shown = mutableListOf<BugReportInstructions>()
        instructionsReport(open = { error("no xdg-open") }, shown = shown).report(null)
        assertEquals(1, shown.size)
    }

    @Test
    fun instructionsShownEvenWhenMailClientOpens() = runBlocking {
        val shown = mutableListOf<BugReportInstructions>()
        instructionsReport(open = { true }, shown = shown).report(null)
        assertEquals(1, shown.size)
    }

    @Test
    fun instructionsComeBeforeTheMailClientOpens() = runBlocking {
        val events = mutableListOf<String>()
        DesktopBugReport(Files.createTempDirectory("report-order"), "1.2", { "log" },
            { _, _, _ -> DeliveryResult.DELIVERED }, { events += "mail"; true },
            savedPath = { "/home/u/r.gz" }, instructions = { events += "instructions" }).report(null)
        assertEquals(listOf("instructions", "mail"), events)
    }

    @Test
    fun suggestedSubjectHasVersionAndDescriptionPlaceholder() = runBlocking {
        val shown = mutableListOf<BugReportInstructions>()
        DesktopBugReport(Files.createTempDirectory("report-subj"), "6.0.1255-beta", { "log" },
            { _, _, _ -> DeliveryResult.DELIVERED }, { true }, instructions = { shown += it }).report(null)
        assertEquals("AndBible 6.0.1255-beta bug: <short description>", shown.single().subject)
    }

    @Test
    fun mailBodyAsksToAttachTheSavedFile() = runBlocking {
        var url = ""
        instructionsReport(open = { url = it; true }, shown = mutableListOf()).report(null)
        val decoded = java.net.URLDecoder.decode(url, Charsets.UTF_8)
        assertTrue(decoded.endsWith("Please attach the file /home/u/andbible-report.gz"), decoded)
    }

    @Test
    fun savedLogFileHasNoWriteAboveThisLineIntroduction() = runBlocking {
        var saved = ""
        var url = ""
        DesktopBugReport(Files.createTempDirectory("report-nointro"), "1.2", { "the log" }, { path, _, _ ->
            saved = GZIPInputStream(Files.newInputStream(path)).bufferedReader().readText()
            DeliveryResult.DELIVERED
        }, { url = it; true }, introduction = { "WRITE ABOVE THIS LINE" }).report(null)
        assertFalse("WRITE ABOVE" in saved, saved)
        assertTrue("the log" in saved && "net.bible.desktop" in saved, saved)
        assertTrue("WRITE+ABOVE" in url || "WRITE%20ABOVE" in url, url)
    }

    @Test
    fun noInstructionsWhenSaveCancelled() = runBlocking {
        val shown = mutableListOf<BugReportInstructions>()
        instructionsReport(DeliveryResult.CANCELLED, open = { false }, shown = shown).report(null)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun mailtoUsesTheSharedAddressConstant() = runBlocking {
        var url = ""
        instructionsReport(open = { url = it; true }, shown = mutableListOf()).report(null)
        assertTrue(url.startsWith("mailto:$BUG_REPORT_EMAIL?"))
    }

    @Test
    fun manualInstructionsTextNamesPathAddressAndSubjectAndEscapesHtml() {
        val table = net.bible.sharedui.assembly.hostStringTable("en")
        val saved = table.manualSendText(BugReportInstructions(BUG_REPORT_EMAIL, "Bug Report v1.2", "/home/a&b/<r>.gz"))
        assertTrue("/home/a&amp;b/&lt;r&gt;.gz" in saved, saved)
        assertTrue(BUG_REPORT_EMAIL in saved && "Bug Report v1.2" in saved, saved)
        assertFalse("bug_report_manual" in saved, saved)
        val unsaved = table.manualSendText(BugReportInstructions(BUG_REPORT_EMAIL, "Bug Report v1.2", null))
        assertTrue(BUG_REPORT_EMAIL in unsaved && "Bug Report v1.2" in unsaved, unsaved)
        assertFalse("bug_report_manual" in unsaved, unsaved)
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
        }, { true }).ai("model", "summary", bytes, true)
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
            { _, _, _ -> DeliveryResult.DELIVERED }, { url = it; true },
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
        }, { url = it; true }, introduction = { "Introduction" }).report(IllegalStateException(current))
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
            { _, _, _ -> DeliveryResult.CANCELLED }, { opened = true; true }).report(null)
        assertFalse(opened)
    }

    @Test
    fun bugReportPathsAndMailtoOnWindows() = runBlocking {
        // Report and crash-file paths under a profile with a space and an umlaut, and a mailto launch without xdg-open.
        val root = Files.createTempDirectory("Jänis Ö")
        val opened = mutableListOf<String>()
        recoveryBugReport(root, "1.2", { "log" }, "Introduction", { opened += it }).report(null)
        val file = Files.list(root.resolve("reports")).use { it.toList().single() }
        val decoded = java.net.URLDecoder.decode(opened.single(), Charsets.UTF_8)
        assertTrue(file.toString() in decoded && "Jänis Ö" in decoded, decoded)
        java.net.URI(opened.single()) // the encoded mailto is a valid URI (spaces and backslashes are escaped)

        val calls = mutableListOf<String>()
        val uri = java.net.URI("mailto:errors.andbible@gmail.com?subject=Hi%20there")
        openMailto(uri, "Windows 11", mail = { calls += "mail"; error("no mail client") }, browse = { calls += "browse:$it" }, xdg = { calls += "xdg" })
        assertEquals(listOf("mail", "browse:$uri"), calls)
        calls.clear()
        openMailto(uri, "Windows 11", mail = { calls += "mail" }, browse = { calls += "browse" }, xdg = { calls += "xdg" })
        assertEquals(listOf("mail"), calls)
        calls.clear()
        openMailto(uri, "Linux", mail = { error("none") }, browse = { calls += "browse" }, xdg = { calls += "xdg:$it" })
        assertEquals(listOf("xdg:$uri"), calls)
    }
}
