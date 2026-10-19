@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.iosRecentLogSnapshot
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.ios.startup.IosCrashTracker
import net.bible.sharedui.strings.keyed.StringTable
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.sharedcore.platform.files.FilePresentation
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.service.llm.agent.RawLlmLog

internal class IosBugReport(
    private val table: StringTable,
    private val confirm: suspend (Boolean) -> Boolean = { ai -> confirmReport(table, ai) },
    private val send: suspend (String, String, String, List<Attachment>) -> Unit = IosMailOrShare()::send,
    private val failure: suspend (Exception) -> Unit = { showReportFailure(table, it) },
) {
    suspend fun report(cause: Throwable? = null) = reportSafely(failure) {
        if (!confirm(false)) return@reportSafely
        val (body, attachments) = withContext(Dispatchers.Default) {
            val details = details(cause)
            val log = iosRecentLogSnapshot()
            (table.text("report_bug_line_1") + "\n\n" + details) to listOf(
                Attachment("app_log.txt.gz", "application/gzip", RawLlmLog.gzipCompress(log)),
            )
        }
        send(RECIPIENT, "Bug Report v${device().version}", body, attachments)
    }

    suspend fun ai(model: String, summary: String, log: ByteArray, supported: Boolean) = reportSafely(failure) {
        if (!aiReportEligible(supported, log) || !confirm(true)) return@reportSafely
        val body = withContext(Dispatchers.Default) {
            table.text("report_bug_line_1") + "\n\n" + summary + "\n\n" + details(null)
        }
        send(RECIPIENT, "AI Bug Report v${device().version}: $model", body,
            listOf(Attachment("ai_raw_log.txt.gz", "application/gzip", log)))
    }

    private fun details(cause: Throwable?): String {
        val path = IosCrashTracker.crashFile.toPath()
        val stack = runCatching {
            if (FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.read(path) { readUtf8() } else ""
        }.getOrElse { "Crash file could not be read: ${it.message}" }
        // Recovery clears app-crashed-time. The retained file's date still identifies the captured crash.
        val savedTime = IosDeviceSettings().getLong("app-crashed-time", 0L)
        val fileTime = (NSFileManager.defaultManager.attributesOfItemAtPath(path.toString(), null)
            ?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970?.times(1000)?.toLong() ?: 0L
        return reportDetails(device(), savedTime.takeIf { it > 0 } ?: fileTime, stack, cause)
    }

    companion object { const val RECIPIENT = "errors.andbible@gmail.com" }
}

internal fun device(): ReportDevice {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
    return ReportDevice(bundle.bundleIdentifier ?: "", version, "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}", UIDevice.currentDevice.model)
}

/** Reporting must remain usable even when its staging directory or outward presenter fails. No databases needed. */
private suspend fun showReportFailure(table: StringTable, error: Exception) {
    net.bible.sharedcore.log.Log.w("IosBugReport", "Report preparation/presentation failed", error)
    withContext(Dispatchers.Main) {
        val host = IosPresenter.topViewController() ?: return@withContext
        suspendCancellableCoroutine<Unit> { continuation ->
            val alert = UIAlertController.alertControllerWithTitle(table.text("error"),
                table.text("bug_report_prepare_failed"), UIAlertControllerStyleAlert)
            val presentation = FilePresentation(alert, continuation, Unit)
            alert.addAction(UIAlertAction.actionWithTitle(table.text("okay"), UIAlertActionStyleDefault) { presentation.finish(Unit) })
            presentation.present(host)
        }
    }
}

/** UIKit confirmation also works before Koin, AppSettings and every database have been opened. */
private suspend fun confirmReport(table: StringTable, ai: Boolean): Boolean = withContext(Dispatchers.Main) {
    val host = IosPresenter.topViewController() ?: return@withContext false
    suspendCancellableCoroutine { continuation ->
        val alert = UIAlertController.alertControllerWithTitle(
            table.text(if (ai) "send_ai_bug_report_title" else "bug_report_email_title"),
            table.text("bug_report_email_text"), UIAlertControllerStyleAlert)
        val presentation = FilePresentation(alert, continuation, false)
        alert.addAction(UIAlertAction.actionWithTitle(table.text("okay"), UIAlertActionStyleDefault) { presentation.finish(true) })
        alert.addAction(UIAlertAction.actionWithTitle(table.text("cancel"), UIAlertActionStyleCancel) { presentation.finish(false) })
        presentation.present(host)
    }
}
