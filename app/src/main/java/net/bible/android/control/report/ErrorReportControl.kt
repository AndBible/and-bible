/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
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
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.applicationVersionName
import net.bible.service.common.CommonUtils.megabytesFree
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object ErrorReportControl {
    fun sendErrorReportEmail(e: Throwable? = null, source: String) {
        GlobalScope.launch {
            BugReport.reportBug(exception = e, source = source)
        }
    }


    enum class ErrorDialogResult {CANCEL, OKAY, REPORT, BACKUP}
    suspend fun showErrorDialog(context: ActivityBase, msg: String, isCancelable: Boolean = false, report: Boolean = true) {
        Log.i(TAG, "showErrorMesage message:$msg")
        withContext(Dispatchers.Main) {
            var askAgain = true
            while(askAgain) {
                askAgain = false
                val result = suspendCoroutine<ErrorDialogResult> {
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
                        dlgBuilder.setNegativeButton(R.string.backup_button) { _, _ -> it.resume(ErrorDialogResult.BACKUP) }
                        dlgBuilder.setPositiveButton(R.string.report_error) { _, _ -> it.resume(ErrorDialogResult.REPORT) }
                        dlgBuilder.setNeutralButton(R.string.error_skip) { _, _ -> it.resume(ErrorDialogResult.CANCEL) }
                    }
                    dlgBuilder.show()
                }
                when(result) {
                    ErrorDialogResult.OKAY -> null
                    ErrorDialogResult.REPORT -> BugReport.reportBug(context, useSaved = true, source = "after crash")
                    ErrorDialogResult.CANCEL -> null
                    ErrorDialogResult.BACKUP -> {
                        BackupControl.backupPopup(context)
                        askAgain = true
                    }
                }
            }
        }
    }

    suspend fun checkCrash(activity: ActivityBase) {
        val crashed = CommonUtils.realSharedPreferences.getBoolean("app-crashed", false)
        if (crashed) {
            CommonUtils.realSharedPreferences.edit().putBoolean("app-crashed", false).commit() // Yes, we want this to be flushed to file immediately
            val msg = activity.getString(R.string.error_occurred_crash_last_time)
            showErrorDialog(activity, msg)
        }
    }
}

const val SCREENSHOT_FILE = "screenshot.webp"

object BugReport {
    private fun createErrorText(exception: Throwable? = null) = try {
        val text = StringBuilder()
        text.append("And Bible version: ").append(applicationVersionName).append("\n")
        text.append("Android version: ").append(Build.VERSION.RELEASE).append("\n")
        text.append("Android SDK version: ").append(Build.VERSION.SDK_INT).append("\n")
        text.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
        text.append("Model: ").append(Build.MODEL).append("\n")
        text.append("Storage Mb free: ").append(megabytesFree).append("\n")
        text.append("WebView version: ").append(WebViewCompat.getCurrentWebViewPackage(BibleApplication.application)?.versionName).append("\n")
        text.append("SQLITE version: ").append(BibleApplication.application.sqliteVersion).append("\n")
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxHeapSizeInMB = runtime.maxMemory() / 1048576L
        text.append("Used heap memory in Mb: ").append(usedMemInMB).append("\n")
        text.append("Max heap memory in Mb: ").append(maxHeapSizeInMB).append("\n\n")
        if (exception != null) {
            val errors = StringWriter()
            exception.printStackTrace(PrintWriter(errors))
            text.append("Exception:\n").append(errors.toString())
        }
        text.toString()
    } catch (e: Exception) {
        "Exception occurred preparing error text:" + e.message
    }

    private fun getSubject(e: Throwable?): String? {
        if (e == null || e.stackTrace.size == 0) {
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

    fun saveScreenshot() {
        val activity = CurrentActivityHolder.getInstance().currentActivity?: return
        val dir = File(activity.filesDir, "/log")
        dir.mkdirs()
        val screenshotFile = File(dir, SCREENSHOT_FILE)
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

    private fun getBugReportMessage(context: Context, exception: Throwable?): String =
        context.run {
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
              - logcat.txt.gz: $logcat
              - screenshot.webp: $screenShot
            
            $line5 $line2
            
            $heading4
            
            """.trimIndent() +
                createErrorText(exception)
        }

    suspend fun reportBug(context_: ActivityBase? = null, exception: Throwable? = null, useSaved: Boolean = false, source: String) {
        val context = context_ ?: CurrentActivityHolder.getInstance().currentActivity
        val dir = File(context.filesDir, "/log")
        val f = File(dir, "logcat.txt.gz")
        val screenshotFile = File(dir, SCREENSHOT_FILE)

        val hourglass = Hourglass(context)
        hourglass.show()
        withContext(Dispatchers.IO) {
            val log = StringBuilder()
            try {
                val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
                val bufferedReader = BufferedReader(
                    InputStreamReader(process.inputStream))

                var line = bufferedReader.readLine()
                while (line != null) {
                    log.append(line + '\n');
                    line = bufferedReader.readLine()
                }
            } catch (e: IOException) {}

            dir.mkdirs()

            val fOut = FileOutputStream(f)
            val osw = GZIPOutputStream(fOut)

            osw.write(log.toString().toByteArray());
            osw.flush()
            osw.close()
            if(!useSaved) {
                delay(1000)
                saveScreenshot()
            }
        }

        hourglass.dismiss()

        withContext(Dispatchers.Main) {
            val subject = context.getString(R.string.report_bug_email_subject_3, source, CommonUtils.applicationNameMedium, getSubject(exception))
            val message = getBugReportMessage(context, exception)

            val uris = ArrayList(listOf(f, screenshotFile).filter { it.canRead() }.map {
                FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", it)
            })
            val email = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_EMAIL, arrayOf("errors.andbible@gmail.com"))
                type = "text/plain"
            }
            val chooserIntent = Intent.createChooser(email, context.getString(R.string.send_bug_report_title))
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context is ActivityBase) {
                context.awaitIntent(chooserIntent)
            } else {
                context.startActivity(chooserIntent)
            }
        }
    }

}

const val TAG = "ErrorReportControl"
