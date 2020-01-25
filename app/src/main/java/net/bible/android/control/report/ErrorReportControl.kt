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

import android.os.Build
import net.bible.android.activity.R
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.control.ApplicationScope
import net.bible.android.control.email.Emailer
import net.bible.service.common.CommonUtils.applicationVersionName
import net.bible.service.common.CommonUtils.sdCardMegsFree
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@ApplicationScope
class ErrorReportControl @Inject constructor(private val emailer: Emailer, private val resourceProvider: ResourceProvider) {
    fun sendErrorReportEmail(e: Exception?) {
        val text = createErrorText(e)
        val title = resourceProvider.getString(R.string.report_error)
        val subject = getSubject(e, title)
        emailer.send(title, "errors.andbible@gmail.com", subject, text)
    }

    fun createErrorText(exception: Exception?): String {
        return try {
            val text = StringBuilder()
            text.append("And Bible version: ").append(applicationVersionName).append("\n")
            text.append("Android version: ").append(Build.VERSION.RELEASE).append("\n")
            text.append("Android SDK version: ").append(Build.VERSION.SDK_INT).append("\n")
            text.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
            text.append("Model: ").append(Build.MODEL).append("\n\n")
            text.append("SD card Mb free: ").append(sdCardMegsFree).append("\n\n")
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
    }

    private fun getSubject(e: Exception?, title: String): String? {
        if (e == null || e.stackTrace.size == 0) {
            return title
        }
        val stack = e.stackTrace
        for (elt in stack) {
            if (elt.className.contains("net.bible")) {
                return e.message + ":" + elt.className + "." + elt.methodName + ":" + elt.lineNumber
            }
        }
        return e.message
    }

}
