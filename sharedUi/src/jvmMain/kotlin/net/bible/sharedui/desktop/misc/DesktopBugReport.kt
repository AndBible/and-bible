package net.bible.sharedui.desktop.misc

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedui.host.report.BUG_REPORT_EMAIL
import net.bible.sharedui.host.report.ReportDevice
import net.bible.sharedui.host.report.aiReportEligible
import net.bible.sharedui.host.report.reportDetails
import net.bible.sharedui.host.report.reportSafely

/**
 * What the user needs to send the report by hand: the file they saved ([savedPath], null when unknown), the [address]
 * and the suggested [subject]. Shown after every saved report: `mailto:` cannot attach the file itself.
 */
class BugReportInstructions(val address: String, val subject: String, val savedPath: String?)

/** The instructions dialog's HTML body for [info]; every inserted value is HTML-escaped. */
fun net.bible.sharedui.strings.keyed.StringTable.manualSendText(info: BugReportInstructions): String {
    fun esc(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val path = info.savedPath
    val text = if (path != null) fmt("bug_report_manual_text_saved", esc(path), esc(info.address), esc(info.subject))
        else fmt("bug_report_manual_text_unsaved", esc(info.address), esc(info.subject))
    return text.replace("\n", "<br>")
}

/** Saves diagnostics before opening the mail composer; mailto cannot attach the file itself. */
class DesktopBugReport(
    private val directory: Path,
    private val version: String,
    private val log: () -> String,
    private val deliver: suspend (Path, String, String) -> DeliveryResult,
    /** Opens the `mailto:` URL, after [instructions] has been shown. */
    private val open: suspend (String) -> Boolean,
    private val confirm: suspend (Boolean) -> Boolean = { true },
    private val failure: suspend (Exception) -> Unit = { Log.w("DesktopBugReport", "Report failed", it) },
    private val introduction: () -> String = { "" },
    private val crashFile: Path = directory.resolve("last_crash.txt"),
    private val retainReport: Boolean = false,
    /** Where the user's chosen save location is, once [deliver] has saved the file. */
    private val savedPath: () -> String? = { null },
    /** Shown after every saved report, before the mail client is opened. */
    private val instructions: suspend (BugReportInstructions) -> Unit = {},
    /** The subject suggested in [instructions]: version and a short description placeholder. */
    private val suggestedSubject: () -> String = { "AndBible $version bug: <short description>" },
    /** The mail body line asking to attach the saved file at the given path. */
    private val attachLine: (String) -> String = { "Please attach the file $it" },
) : net.bible.sharedui.desktop.assembly.DesktopBugReport {
    override suspend fun report(cause: Throwable?) = reportSafely(failure) {
        if (!confirm(false)) return@reportSafely
        val details = details(cause)
        send("Bug Report v$version", "$details\n\n${log()}", "app_log.txt.gz", "${introduction()}\n\n$details")
    }

    suspend fun ai(model: String, summary: String, compressedLog: ByteArray, supported: Boolean) = reportSafely(failure) {
        if (!aiReportEligible(supported, compressedLog) || !confirm(true)) return@reportSafely
        val text = withContext(Dispatchers.IO) {
            java.util.zip.GZIPInputStream(compressedLog.inputStream()).use { input ->
                input.readNBytes(MAX_REPORT_BYTES).toString(Charsets.UTF_8)
            }
        }
        val details = "$summary\n\n${details(null)}"
        send("AI Bug Report v$version: $model", "$details\n\n$text", "ai_raw_log.txt.gz", "${introduction()}\n\n$details")
    }

    private suspend fun send(subject: String, text: String, name: String, body: String) {
        Files.createDirectories(directory)
        val path = Files.createTempFile(directory, if (retainReport) "${System.currentTimeMillis()}-" else "report-", ".gz")
        var completed = false
        try {
            withContext(Dispatchers.IO) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                GZIPOutputStream(Files.newOutputStream(path)).use { it.write(bytes, 0, minOf(bytes.size, MAX_REPORT_BYTES)) }
            }
            val result = deliver(path, name, subject)
            if (result == DeliveryResult.FAILED) error("Report file delivery failed")
            if (result == DeliveryResult.DELIVERED) {
                completed = true
                // The file location the user must attach: their chosen save location; the retained file for recovery.
                val shown = savedPath() ?: if (retainReport) path.toString() else null
                try {
                    instructions(BugReportInstructions(BUG_REPORT_EMAIL, suggestedSubject(), shown))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DesktopBugReport", "Could not show the instructions", e)
                }
                val prefix = "mailto:$BUG_REPORT_EMAIL?subject=${encodeBounded(subject, 1024)}&body="
                val suffix = if (shown != null) encode("\n\n${attachLine(shown)}") else ""
                val mailBody = encodeBounded(body, (MAX_MAIL_BYTES - prefix.length - suffix.length).coerceAtLeast(0))
                try {
                    open(prefix + mailBody + suffix)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DesktopBugReport", "Could not open the mail client", e)
                }
            }
        } finally {
            if (!retainReport || !completed) Files.deleteIfExists(path)
        }
    }

    private fun details(cause: Throwable?): String {
        val stack = runCatching { if (Files.exists(crashFile)) Files.readString(crashFile) else "" }
            .getOrElse { "Crash file could not be read: ${it.message}" }
        val time = runCatching { Files.getLastModifiedTime(crashFile).toMillis() }.getOrDefault(0L)
        return reportDetails(ReportDevice("net.bible.desktop", version,
            "${System.getProperty("os.name")} ${System.getProperty("os.version")}", System.getProperty("os.arch"),
            desktopReportFacts()), time, stack, cause)
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun encodeBounded(value: String, limit: Int): String {
        var low = 0
        var high = minOf(value.length, limit)
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (encode(value.substring(0, middle)).length <= limit) low = middle else high = middle - 1
        }
        if (low < value.length && low > 0 && Character.isHighSurrogate(value[low - 1])) low--
        return encode(value.substring(0, low))
    }

    private companion object {
        const val MAX_REPORT_BYTES = 1024 * 1024
        const val MAX_MAIL_BYTES = 8 * 1024
    }
}

fun recoveryBugReport(directory: Path, version: String, log: () -> String, introduction: String, open: (String) -> Unit = ::openRecoveryMail): DesktopBugReport =

    DesktopBugReport(directory.resolve("reports"), version, log,

        { path, _, _ -> println(path); DeliveryResult.DELIVERED }, { open(it); true },

        introduction = { introduction }, crashFile = directory.resolve("logs").resolve("last_crash.txt"), retainReport = true)



private fun openRecoveryMail(url: String) = openMailto(java.net.URI(url))

/**
 * Opens a `mailto:` URI in the mail client: `Desktop.mail` first. When that fails, Windows and macOS fall back to
 * `Desktop.browse` (the shell opens mailto: links); other Unix desktops to `xdg-open`, which Windows does not have.
 */
internal fun openMailto(
    uri: java.net.URI,
    osName: String = System.getProperty("os.name").orEmpty(),
    mail: (java.net.URI) -> Unit = { java.awt.Desktop.getDesktop().mail(it) },
    browse: (java.net.URI) -> Unit = { java.awt.Desktop.getDesktop().browse(it) },
    xdg: (java.net.URI) -> Unit = { ProcessBuilder("xdg-open", it.toASCIIString()).start() },
) {
    try {
        mail(uri)
    } catch (_: Exception) {
        val lower = osName.lowercase()
        if (lower.startsWith("windows") || lower.startsWith("mac")) browse(uri) else xdg(uri)
    }
}

/**
 * The desktop report header's extra lines: build number, Java, locale, and whatever the app recorded in
 * [net.bible.sharedcore.log.ReportFacts.global] (the chosen web engine and renderer).
 */
internal fun desktopReportFacts(): List<Pair<String, String>> = buildList {
    System.getProperty("andbible.versionCode")?.takeIf { it.isNotBlank() }?.let { add("Build" to it) }
    add("Java" to "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}")
    add("Locale" to java.util.Locale.getDefault().toLanguageTag())
    addAll(net.bible.sharedcore.log.ReportFacts.global.snapshot().filter { (k, _) -> none { it.first == k } })
}

/**
 * Opens the folder holding [file] in the system file manager: `Desktop.open` first, `xdg-open` on other Unix
 * desktops. Returns whether a launcher accepted it; the folder of a path without a parent is not opened.
 */
fun openContainingFolder(file: String): Boolean {
    val folder = Path.of(file).parent?.toFile() ?: return false
    return try {
        java.awt.Desktop.getDesktop().open(folder); true
    } catch (_: Exception) {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (os.startsWith("windows") || os.startsWith("mac")) false
        else try { ProcessBuilder("xdg-open", folder.path).start(); true } catch (_: Exception) { false }
    }
}
