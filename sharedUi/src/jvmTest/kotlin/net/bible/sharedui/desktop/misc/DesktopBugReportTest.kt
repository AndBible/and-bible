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
    fun cancelledSaveDoesNotOpenMail() = runBlocking {
        val root = Files.createTempDirectory("report-cancel")
        var opened = false
        DesktopBugReport(root, "1.2", { "log" },
            { _, _, _ -> DeliveryResult.CANCELLED }, { opened = true }).report(null)
        assertFalse(opened)
    }
}
