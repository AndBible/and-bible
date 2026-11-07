@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.iosRecentLogSnapshot
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.ios.startup.IosCrashTracker
import net.bible.sharedui.strings.keyed.StringTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okio.ByteString.Companion.decodeBase64
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSProcessInfo
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.isiOSAppOnMac
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.sharedcore.platform.files.FilePresentation
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedui.host.report.Attachment
import net.bible.sharedui.host.report.ReportDevice
import net.bible.sharedui.host.report.aiReportEligible
import net.bible.sharedui.host.report.reportAttachments
import net.bible.sharedui.host.report.reportDetails
import net.bible.sharedui.host.report.reportSafely

internal class IosBugReport(
    private val table: StringTable,
    private val confirm: suspend (Boolean) -> Boolean = { ai -> confirmReport(table, ai) },
    private val send: suspend (String, String, String, List<Attachment>) -> Unit = IosMailOrShare()::send,
    private val failure: suspend (Exception) -> Unit = { showReportFailure(table, it) },
    private val crashDiagnostic: () -> Attachment? = ::recentCrashDiagnostic,
) {
    /** [extraAttachments] are sent after the app log, e.g. the legacy migration report. */
    suspend fun report(cause: Throwable? = null, extraAttachments: List<Attachment> = emptyList()) = reportSafely(failure) {
        if (!confirm(false)) return@reportSafely
        val (body, attachments) = withContext(Dispatchers.Default) {
            val details = details(cause)
            val log = iosRecentLogSnapshot()
            val crash = crashDiagnostic()
            (table.text("report_bug_line_1") + "\n\n" + details) to reportAttachments(
                RawLlmLog.gzipCompress(log), extraAttachments + listOfNotNull(crash))
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

    companion object { const val RECIPIENT = net.bible.sharedui.host.report.BUG_REPORT_EMAIL }
}

/** File name of the attached MetricKit crash diagnostic (gzipped like the app log). */
internal const val CRASH_DIAGNOSTIC_ATTACHMENT = "recent_crash_diagnostic.json.gz"

private fun recentCrashDiagnostic(): Attachment? {
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    // Path shared with RecentCrashDiagnosticStore.swift (iosApp); keep both in step.
    return readCrashDiagnostic("$support/AndBible/recent-crash-diagnostic.json".toPath(), NSDate().timeIntervalSince1970)
}

/**
 * Reads the envelope `RecentCrashDiagnosticStore.swift` writes (`{"occurrence":<epoch seconds>,"payload":"<base64>"}`,
 * produced by Swift's `JSONEncoder`: `Data` is base64 and `/` is escaped as `\/`, which the JSON parser undoes).
 * Returns the payload gzipped, or null when there is nothing fresh and valid to attach; a read or parse failure is
 * logged as a warning and treated the same way, so a damaged file never blocks the report.
 * The checked-in sample `iosApp/test-fixtures/crash-envelope.json` pins the format on both sides.
 */
internal fun readCrashDiagnostic(path: okio.Path, now: Double): Attachment? = try {
    parseCrashDiagnostic(path, now)
} catch (e: Exception) {
    net.bible.sharedcore.log.Log.w("IosBugReport", "Recent crash diagnostic could not be read", e)
    null
}

private fun parseCrashDiagnostic(path: okio.Path, now: Double): Attachment? {
    if (!FileSystem.SYSTEM.exists(path)) return null
    // Bound the encoded envelope before reading it. The payload is capped at 2 MiB (maximumPayloadBytes in the Swift
    // store); base64 makes that 2.67 MiB, escaped slashes add up to ~1.6 %, so 3 MiB covers the largest valid file.
    if ((FileSystem.SYSTEM.metadata(path).size ?: return null) > 3 * 1024 * 1024) return null
    val envelope = Json.parseToJsonElement(FileSystem.SYSTEM.read(path) { readUtf8() }).jsonObject
    val occurrence = envelope["occurrence"]?.jsonPrimitive?.doubleOrNull ?: return null
    val age = now - occurrence
    if (age < 0 || age > 86400) return null
    val bytes = envelope["payload"]?.jsonPrimitive?.content?.decodeBase64()?.toByteArray() ?: return null
    if (bytes.isEmpty() || bytes.size > 2 * 1024 * 1024) return null
    val crashArray = Json.parseToJsonElement(bytes.decodeToString())
    if (crashArray !is kotlinx.serialization.json.JsonArray || crashArray.isEmpty()) return null
    return Attachment(CRASH_DIAGNOSTIC_ATTACHMENT, "application/gzip", RawLlmLog.gzipCompress(bytes.decodeToString()))
}

internal fun device(): ReportDevice {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
    return ReportDevice(bundle.bundleIdentifier ?: "", version, "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
        UIDevice.currentDevice.model, iosReportFacts())
}

/** Build number, whether the iOS app runs on a Mac, and the locale: the report header lines beyond [ReportDevice]'s basics. */
internal fun iosReportFacts(): List<Pair<String, String>> {
    val bundle = NSBundle.mainBundle
    return buildList {
        (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.takeIf { it.isNotBlank() }?.let { add("Build" to it) }
        (bundle.objectForInfoDictionaryKey("AndBibleCommit") as? String)?.takeIf { it.isNotBlank() }?.let { add("Commit" to it) }
        if (NSProcessInfo.processInfo.isiOSAppOnMac()) add("Platform" to "iOS app on Mac")
        add("Locale" to (NSLocale.preferredLanguages.firstOrNull() as? String ?: NSLocale.currentLocale.toString()))
    }
}

/** Shortens the container path in logs and writes the version/build/OS/locale lines first in the log; called once at launch. */
internal fun startIosReportLog() {
    net.bible.sharedcore.log.registerIosLogPathAliases()
    val d = device()
    val facts = net.bible.sharedcore.log.ReportFacts.global
    facts.set("Version", d.version)
    d.extras.forEach { (k, v) -> facts.set(k, v) }
    facts.set("OS", d.os)
    facts.set("Model", d.model)
    facts.logAll("Startup")
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
