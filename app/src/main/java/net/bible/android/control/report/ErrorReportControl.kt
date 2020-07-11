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

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
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
import javax.inject.Inject

@ApplicationScope
class ErrorReportControl @Inject constructor() {
    fun sendErrorReportEmail(e: Exception? = null) {
		GlobalScope.launch {
            reportBug(exception = e)
        }
    }

    private fun createErrorText(exception: Exception?) = try {
		val text = StringBuilder()
		text.append("And Bible version: ").append(applicationVersionName).append("\n")
		text.append("Android version: ").append(Build.VERSION.RELEASE).append("\n")
		text.append("Android SDK version: ").append(Build.VERSION.SDK_INT).append("\n")
		text.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
		text.append("Model: ").append(Build.MODEL).append("\n\n")
		text.append("Storage Mb free: ").append(megabytesFree).append("\n\n")
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

    private fun getSubject(e: Exception?): String? {
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

	suspend fun reportBug(context_: Context? = null, exception: Exception? = null) {
		val context = context_?: CurrentActivityHolder.getInstance().currentActivity
        val dir = File(context.filesDir, "/log")
        val f = File(dir, "logcat.txt.gz")
        val hourglass = Hourglass(context)
        hourglass.show()
        withContext(Dispatchers.IO) {
            val log=StringBuilder()
            try {
                val process = Runtime.getRuntime().exec("logcat -d -t 10000")
                val bufferedReader = BufferedReader(
                    InputStreamReader(process.inputStream))

                var line = bufferedReader.readLine()
                while (line != null)
                {
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
        }
        hourglass.dismiss()

        withContext(Dispatchers.Main) {
            val subject = context.getString(R.string.report_bug_email_subject_2, CommonUtils.applicationNameMedium, getSubject(exception))
            val message = "\n\n" + context.getString(R.string.report_bug_email_message, createErrorText(exception))

            val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", f)
            val email = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_EMAIL, arrayOf("errors.andbible@gmail.com"))
                type = "application/x-gzip"
            }
            val chooserIntent = Intent.createChooser(email, context.getString(R.string.send_bug_report_title))
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooserIntent)
        }
	}

}
