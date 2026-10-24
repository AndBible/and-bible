package net.bible.sharedui.ios.report

/** Keep a recoverable report failure out of the app's uncaught-exception hook. Cancellation is not a failure. */
internal suspend fun reportSafely(failure: suspend (Exception) -> Unit, action: suspend () -> Unit) {
    try { action() }
    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (error: Exception) { failure(error) }
}

internal data class ReportDevice(val appId: String, val version: String, val os: String, val model: String)
internal data class Attachment(val name: String, val mime: String, val bytes: ByteArray)
internal enum class ReportTransport { MAIL, SHARE }
internal fun reportTransport(canSendMail: Boolean): ReportTransport = if (canSendMail) ReportTransport.MAIL else ReportTransport.SHARE

/** A retained crash file remains useful after the recovery gate resets the crash-loop timestamp. */
internal fun reportDetails(device: ReportDevice, crashTime: Long, crashStack: String, cause: Throwable?): String = buildString {
    appendLine("App id: ${device.appId}")
    appendLine("Version: ${device.version}")
    appendLine("OS version: ${device.os}")
    appendLine("Model: ${device.model}")
    if (crashStack.isNotBlank()) {
        appendLine("=== LAST APP CRASH INFORMATION ===")
        if (crashTime > 0) appendLine("Last crash time (epoch ms): $crashTime")
        appendLine("Crash stacktrace:")
        appendLine(crashStack)
        appendLine("=== END LAST CRASH INFORMATION ===")
    }
    if (cause != null) appendLine("Current Exception:\n${cause.stackTraceToString()}")
}
internal fun aiReportEligible(modelSupported: Boolean, log: ByteArray): Boolean = modelSupported && log.isNotEmpty()

/** The standard report attachments: the gzipped app log first, then [extra]. */
internal fun reportAttachments(gzippedLog: ByteArray, extra: List<Attachment>): List<Attachment> =
    listOf(Attachment("app_log.txt.gz", "application/gzip", gzippedLog)) + extra
