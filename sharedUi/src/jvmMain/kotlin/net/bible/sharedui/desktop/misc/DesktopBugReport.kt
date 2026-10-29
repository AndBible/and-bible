package net.bible.sharedui.desktop.misc

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedui.host.report.ReportDevice
import net.bible.sharedui.host.report.aiReportEligible
import net.bible.sharedui.host.report.reportDetails
import net.bible.sharedui.host.report.reportSafely

/** Saves diagnostics before opening the mail composer; mailto cannot attach the file itself. */
class DesktopBugReport(
    private val directory: Path,
    private val version: String,
    private val log: () -> String,
    private val deliver: suspend (Path, String, String) -> DeliveryResult,
    private val open: (String) -> Unit,
    private val confirm: suspend (Boolean) -> Boolean = { true },
    private val failure: suspend (Exception) -> Unit = { Log.w("DesktopBugReport", "Report failed", it) },
    private val introduction: () -> String = { "" },
    private val crashFile: Path = directory.resolve("last_crash.txt"),
) : net.bible.sharedui.desktop.assembly.DesktopBugReport {
    override suspend fun report(cause: Throwable?) = reportSafely(failure) {
        if (!confirm(false)) return@reportSafely
        val details = details(cause)
        send("Bug Report v$version", "${introduction()}\n\n$details\n\n${log()}", "app_log.txt.gz")
    }

    suspend fun ai(model: String, summary: String, compressedLog: ByteArray, supported: Boolean) = reportSafely(failure) {
        if (!aiReportEligible(supported, compressedLog) || !confirm(true)) return@reportSafely
        val text = withContext(Dispatchers.IO) {
            java.util.zip.GZIPInputStream(compressedLog.inputStream()).bufferedReader().use { it.readText() }
        }
        send("AI Bug Report v$version: $model", "${introduction()}\n\n$summary\n\n${details(null)}\n\n$text", "ai_raw_log.txt.gz")
    }

    private suspend fun send(subject: String, text: String, name: String) {
        val path = withContext(Dispatchers.IO) {
            Files.createDirectories(directory)
            val file = Files.createTempFile(directory, "report-", ".gz")
            val bytes = text.toByteArray(Charsets.UTF_8)
            GZIPOutputStream(Files.newOutputStream(file)).use { it.write(bytes, 0, minOf(bytes.size, MAX_REPORT_BYTES)) }
            file
        }
        try {
            val result = deliver(path, name, subject)
            if (result == DeliveryResult.FAILED) error("Report file delivery failed")
            if (result == DeliveryResult.DELIVERED) {
                open("mailto:errors.andbible@gmail.com?subject=${encode(subject)}")
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun details(cause: Throwable?): String {
        val stack = runCatching { if (Files.exists(crashFile)) Files.readString(crashFile) else "" }
            .getOrElse { "Crash file could not be read: ${it.message}" }
        val time = runCatching { Files.getLastModifiedTime(crashFile).toMillis() }.getOrDefault(0L)
        return reportDetails(ReportDevice("net.bible.desktop", version,
            "${System.getProperty("os.name")} ${System.getProperty("os.version")}", System.getProperty("os.arch")), time, stack, cause)
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    private companion object { const val MAX_REPORT_BYTES = 1024 * 1024 }
}
