/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.report

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.applicationVersionName
import net.bible.service.common.CommonUtils.megabytesFree
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ONE_HOUR = 1000L * 60L * 60L

// Crash data file constants
const val LAST_CRASH_STACKTRACE_FILE = "last_crash_stacktrace.txt"
const val LAST_CRASH_LOGCAT_FILE = "last_crash_logcat.txt.gz"
const val LAST_CRASH_SCREENSHOT_FILE = "last_crash_screenshot.webp"
const val STACKTRACE_FILE = "stacktrace.txt"
const val LOGCAT_FILE = "logcat.txt.gz"
val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

object ErrorReportControl {
    fun sendErrorReportEmail(e: Throwable? = null, source: String) {
        GlobalScope.launch {
            BugReport.reportBug(exception = e, source = source)
        }
    }


    enum class ErrorDialogResult {CANCEL, OKAY, REPORT, BACKUP}
    suspend fun showErrorDialog(context: ActivityBase, msg: String, isCancelable: Boolean = false, report: Boolean = true, exception: Throwable? = null) {
        Log.i(TAG, "showErrorMesage message:$msg")
        withContext(Dispatchers.Main) {
            var askAgain = true
            while(askAgain) {
                askAgain = false
                val result = suspendCoroutine {
                    val dlgBuilder = AlertDialog.Builder(context)
                        .setMessage(msg)
                        .setCancelable(isCancelable)
                        .setOnCancelListener { _ -> it.resume(ErrorDialogResult.CANCEL) }
                        .setPositiveButton(R.string.okay) { _, _ -> it.resume(ErrorDialogResult.OKAY) }

                    if (isCancelable && !report) {
                        dlgBuilder.setNegativeButton(R.string.cancel) { _, _ ->
                            it.resume(ErrorDialogResult.CANCEL)
                        }
                    }
                    if (report) {
                        dlgBuilder.setNegativeButton(R.string.backup_and_restore) { _, _ -> it.resume(ErrorDialogResult.BACKUP) }
                        dlgBuilder.setPositiveButton(R.string.report_error) { _, _ -> it.resume(ErrorDialogResult.REPORT) }
                        dlgBuilder.setNeutralButton(R.string.error_skip) { _, _ -> it.resume(ErrorDialogResult.CANCEL) }
                    }
                    dlgBuilder.show()
                }
                when(result) {
                    ErrorDialogResult.OKAY -> null
                    ErrorDialogResult.REPORT -> BugReport.reportBug(context, exception=exception, useSaved = true, source = "after crash")
                    ErrorDialogResult.CANCEL -> null
                    ErrorDialogResult.BACKUP -> {
                        BackupControl.backupPopup(context)
                        askAgain = true
                    }
                }
            }
        }
    }

    private fun resetCrashCounts() {
        CommonUtils.realSharedPreferences.edit(commit = true) {
            putLong("app-crashed-time", 0L)
            putInt("app-crashed-count", 0)
        }
    }

    suspend fun checkCrash(activity: ActivityBase) {
        val crashedCount = CommonUtils.realSharedPreferences.getInt("app-crashed-count", 0)
        val crashedTime = CommonUtils.realSharedPreferences.getLong("app-crashed-time", 0L)

        // If crash happened more than one hour ago, forget about it
        if (crashedTime != 0L && System.currentTimeMillis() - crashedTime > ONE_HOUR) {
            resetCrashCounts()
        }
        // If more than 1 crash happened within 1 hour, then let user report it (and do db backup)
        else if (crashedCount > 1 && crashedTime != 0L && System.currentTimeMillis() - crashedTime < ONE_HOUR) {
            resetCrashCounts()
            val msg = activity.getString(R.string.error_occurred_crash_last_time)
            showErrorDialog(activity, msg)
        }
    }
}

const val SCREENSHOT_FILE = "screenshot.webp"

object BugReport {
    private fun createErrorText(exception: Throwable? = null, stackTrace: String? = null) = try {
        StringBuilder().run {
            append("App id: ").append(BibleApplication.application.packageName).append("\n")
            append("Version: ").append(applicationVersionName).append("\n")
            append("Android version: ").append(Build.VERSION.RELEASE).append("\n")
            append("Android SDK version: ").append(Build.VERSION.SDK_INT).append("\n")
            append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
            append("Hardware: ").append(Build.HARDWARE).append("\n")
            append("Product: ").append(Build.PRODUCT).append("\n")
            append("Device: ").append(Build.DEVICE).append("\n")
            append("Brand: ").append(Build.BRAND).append("\n")
            append("Model: ").append(Build.MODEL).append("\n")
            append("Storage Mb free: ").append(megabytesFree).append("\n")
            append("WebView version: ").append(WebViewCompat.getCurrentWebViewPackage(BibleApplication.application)?.versionName).append("\n")
            append("SQLITE version: ").append(BibleApplication.application.sqliteVersion).append("\n")
            val runtime = Runtime.getRuntime()
            val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
            val maxHeapSizeInMB = runtime.maxMemory() / 1048576L
            append("Used heap memory in Mb: ").append(usedMemInMB).append("\n")
            append("Max heap memory in Mb: ").append(maxHeapSizeInMB).append("\n\n")
            
            // Add last crash information if available
            val lastCrashFile = File(logDir, LAST_CRASH_STACKTRACE_FILE)
            val crashTime = CommonUtils.realSharedPreferences.getLong("app-crashed-time", 0L)
            
            if (lastCrashFile.exists() && crashTime > 0) {
                try {
                    val crashData = lastCrashFile.readText()

                    if (crashData.isNotEmpty()) {
                        val formattedTime = TIMESTAMP_FORMAT.format(Date(crashTime))
                        append("=== LAST APP CRASH INFORMATION ===\n")
                        append("Last crash occurred at: ").append(formattedTime).append("\n")
                        append("Crash stacktrace:\n").append(crashData).append("\n")
                        append("=== END LAST CRASH INFORMATION ===\n\n")
                    }
                } catch (e: Exception) {
                    append("Error reading last crash information: ").append(e.message).append("\n\n")
                }
            }
            
            if (exception != null) {
                val errors = StringWriter()
                exception.printStackTrace(PrintWriter(errors))
                append("Current Exception:\n").append(errors.toString())
            }
            if (stackTrace != null) {
                append("Current Exception:\n").append(stackTrace)
            }
            toString()
        }
    } catch (e: Exception) {
        "Exception occurred preparing error text:" + e.message
    }

    private fun getSubject(e: Throwable?): String? {
        if (e == null || e.stackTrace.isEmpty()) {
            return applicationVersionName
        }
        val stack = e.stackTrace
        for (elt in stack) {
            if (elt.className.contains("net.bible")) {
                return "$applicationVersionName ${e.message}:${elt.className}.${elt.methodName}:${elt.lineNumber}"
            }
        }
        return e.message
    }

    private fun getScreenShot(activity: Activity?): Bitmap? {
        val view = activity?.window?.decorView?.rootView?: return null
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) bgDrawable.draw(canvas)
        else canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return returnedBitmap
    }

    private val logDir get() = File(SharedConstants.internalFilesDir, "/log")

    private fun logBasicInfo() {
        Log.i(TAG, "logBasicInfo")
        Log.i(TAG, "OS:" + System.getProperty("os.name") + " ver " + System.getProperty("os.version"))
        Log.i(TAG, "Java:" + System.getProperty("java.vendor") + " ver " + System.getProperty("java.version"))
        Log.i(TAG, "Java home:" + System.getProperty("java.home")!!)
        Log.i(TAG, "User dir:" + System.getProperty("user.dir") + " Timezone:" + System.getProperty("user.timezone"))
        Log.i(TAG, createErrorText())
    }

    fun saveLogcat() {
        Log.i(TAG, "Trying to save logcat")
        logBasicInfo()
        // Let's give log buffers a little time to flush themselves
        Thread.sleep(1000)
        val f = File(logDir, LOGCAT_FILE)
        logDir.mkdirs()

        try {
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            FileOutputStream(f).use { fOut ->
                GZIPOutputStream(fOut).use { gzOut -> process.inputStream.use { s -> s.copyTo(gzOut)}}
            }
        } catch (_: Throwable) {}
    }

    fun saveStackTrace(e: Throwable) {
        logDir.mkdirs()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        val s = sw.toString()
        val f = File(logDir, STACKTRACE_FILE)
        f.delete()
        f.outputStream().use {
            it.write(s.toByteArray())
        }
    }

    fun saveCrashData() {
        cleanupOldCrashData()
        // Save crash-specific stack trace, logcat and screenshot
        val crashStackTraceFile = File(logDir, LAST_CRASH_STACKTRACE_FILE)
        val crashLogcatFile = File(logDir, LAST_CRASH_LOGCAT_FILE)
        val crashScreenshotFile = File(logDir, LAST_CRASH_SCREENSHOT_FILE)

        val stackTraceFile = File(logDir, STACKTRACE_FILE)
        if (stackTraceFile.exists()) {
            stackTraceFile.copyTo(crashStackTraceFile, overwrite = true)
        }

        // Copy current logcat to crash-specific file
        val logcatFile = File(logDir, LOGCAT_FILE)
        if (logcatFile.exists()) {
            logcatFile.copyTo(crashLogcatFile, overwrite = true)
        }
        
        // Copy current screenshot to crash-specific file
        val screenshotFile = File(logDir, SCREENSHOT_FILE)
        if (screenshotFile.exists()) {
            screenshotFile.copyTo(crashScreenshotFile, overwrite = true)
        }
    }

    fun cleanupOldCrashData() {
        try {
            val crashTime = CommonUtils.realSharedPreferences.getLong("app-crashed-time", 0L)
            
            if (crashTime > 0 && System.currentTimeMillis() - crashTime < ONE_HOUR * 24) {
                // Remove old crash files
                listOf(LAST_CRASH_STACKTRACE_FILE, LAST_CRASH_LOGCAT_FILE, LAST_CRASH_SCREENSHOT_FILE).forEach { fileName ->
                    File(logDir, fileName).delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up old crash data", e)
        }
    }
    fun saveScreenshot() {
        Log.i(TAG, "Trying to save screenshot")
        val activity = CurrentActivityHolder.currentActivity?: return
        logDir.mkdirs()
        val screenshotFile = File(logDir, SCREENSHOT_FILE)
        try {
            val screenShot = getScreenShot(activity) ?: return
            val screenshotOutputStream = FileOutputStream(screenshotFile)
            screenShot.compress(Bitmap.CompressFormat.WEBP, 0, screenshotOutputStream)
            screenshotOutputStream.flush()
            screenshotOutputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Saving screenshot failed to exception", e)
            // Delete earlier stored screenshot file, so we don't send unrelated screenshot.
            screenshotFile.delete()
            return
        }
    }

    private fun getBugReportMessage(context: Context, exception: Throwable?, stackTrace: String? = null): String = context.run {
        val bigHeading = getString(R.string.report_bug_big_heading)
        val heading1 = getString(R.string.report_bug_heading1)
        val heading2 = getString(R.string.report_bug_heading2)
        val heading3 = getString(R.string.report_bug_heading_3)
        val heading4 = getString(R.string.report_bug_heading_4)
        val instruction1 = getString(R.string.report_bug_instructions1)
        val instruction2 = getString(R.string.report_bug_instructions2)
        val instruction3 = getString(R.string.report_bug_instructions3)
        val line1 = getString(R.string.report_bug_line_1)
        val line2 = getString(R.string.report_bug_line_2)
        val line3 = getString(R.string.report_bug_line_3)
        val line4 = getString(R.string.report_bug_line_4)
        val line5 = getString(R.string.bug_report_attachment_line_1)
        val logcat = getString(R.string.bug_report_logcat)
        val screenShot = getString(R.string.bug_report_screenshot)

        // Check if crash files exist
        val lastCrashFile = File(BugReport.logDir, LAST_CRASH_STACKTRACE_FILE)
        val hasCrashData = lastCrashFile.exists()
        val crashAttachments = if (hasCrashData) {
            "              - $LAST_CRASH_LOGCAT_FILE: Logcat from the last app crash\n" +
            "              - $LAST_CRASH_SCREENSHOT_FILE: Screenshot from the last app crash\n"
        } else ""

        "\n\n" +
            """
            --- $bigHeading ---
            
            $heading1
            $line1
            
            $heading2
              $instruction1
              $instruction2
              $instruction3
              
            $line3 $line4
            
            $heading3
              - $LOGCAT_FILE: $logcat
              - $SCREENSHOT_FILE: $screenShot
$crashAttachments            
            $line5 $line2
            
            $heading4
            
            """.trimIndent() +
            createErrorText(exception, stackTrace)
    }

    suspend fun reportBug(context_: ActivityBase? = null, exception: Throwable? = null, useSaved: Boolean = false, source: String) {
        val activity = context_ ?: CurrentActivityHolder.currentActivity!!
        val screenshotFile = File(logDir, SCREENSHOT_FILE)
        val logcatFile = File(logDir, LOGCAT_FILE)
        val stackTraceFile = File(logDir, STACKTRACE_FILE)
        val lastCrashStackTraceFile = File(logDir, LAST_CRASH_STACKTRACE_FILE)
        val lastCrashLogcatFile = File(logDir, LAST_CRASH_LOGCAT_FILE)
        val lastCrashScreenshotFile = File(logDir, LAST_CRASH_SCREENSHOT_FILE)
        
        if(source != "after crash") {
            stackTraceFile.delete()
        }
        val stackTrace = if(stackTraceFile.canRead()) String(stackTraceFile.readBytes()) else null

        val hourglass = Hourglass(activity)
        hourglass.show()
        withContext(Dispatchers.IO) {
            if(!useSaved) {
                delay(1000)
                saveLogcat()
                saveScreenshot()
            }
        }

        hourglass.dismiss()

        withContext(Dispatchers.Main) {
            val result = Dialogs.simpleQuestion(
                activity,
                message = activity.getString(R.string.bug_report_email_text),
                title = activity.getString(R.string.bug_report_email_title),
            )
            if(!result) return@withContext
            val subject = activity.getString(
                R.string.report_bug_email_subject_3,
                "${CommonUtils.applicationVersionNumber} $source",
                CommonUtils.applicationNameMedium, getSubject(exception
                )
            )
            val message = getBugReportMessage(activity, exception, stackTrace)

            // Include crash-specific files if they exist and are recent
            val attachmentFiles = mutableListOf<File>()
            
            // Always include current logcat and screenshot
            if (logcatFile.canRead()) attachmentFiles.add(logcatFile)
            if (screenshotFile.canRead()) attachmentFiles.add(screenshotFile)
            
            // Include crash-specific files if they exist and are recent (within 24 hours)
            if (lastCrashStackTraceFile.exists()) {
                val crashTime = CommonUtils.realSharedPreferences.getLong("app-crashed-time", 0L)
                if (crashTime > 0 && System.currentTimeMillis() - crashTime < 24 * ONE_HOUR) {
                    if (lastCrashLogcatFile.canRead()) attachmentFiles.add(lastCrashLogcatFile)
                    if (lastCrashScreenshotFile.canRead()) attachmentFiles.add(lastCrashScreenshotFile)
                }
            }

            val uris = ArrayList(attachmentFiles.map {
                FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", it)
            })
            
            val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_EMAIL, arrayOf("errors.andbible@gmail.com"))
                type = "application/octet-stream"
            }

            val chooserIntent = Intent.createChooser(emailIntent, activity.getString(R.string.send_bug_report_title))
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.awaitIntent(chooserIntent)
        }
    }
}

const val TAG = "ErrorReportControl"
