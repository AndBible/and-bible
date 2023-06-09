/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.base

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl
import net.bible.service.common.htmlToSpan
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Class to manage the display of various dialogs
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
private const val TAG = "Dialogs"

object Dialogs {
    fun showMsg(msgId: Int, param: String?) {
        showErrorMsg(application.getString(msgId, param))
    }

    fun showMsg(msgId: Int, isCancelable: Boolean, okayCallback: (() -> Unit)) {
        showMsg(application.getString(msgId), isCancelable, okayCallback, null)
    }

    fun showMsg(msgId: Int) {
        showErrorMsg(application.getString(msgId))
    }

    fun showErrorMsg(msgId: Int) {
        showErrorMsg(application.getString(msgId))
    }

    fun showErrorMsg(msgId: Int, param: String?) {
        showErrorMsg(application.getString(msgId, param))
    }

    fun showErrorMsg(msgId: Int, okayCallback: () -> Unit) {
        showErrorMsg(application.getString(msgId), okayCallback)
    }

    /**
     * Show error message and allow reporting of exception via e-mail to and-bible
     */
    fun showErrorMsg(msgId: Int, e: Exception?) {
        showErrorMsg(application.getString(msgId), e)
    }

    /**
     * Show error message and allow reporting of exception via e-mail to and-bible
     */
    fun showErrorMsg(message: String?, e: Exception?) {
        val reportCallback = { ErrorReportControl.sendErrorReportEmail(e, source = "error message") }
        showMsg(message, false, null, reportCallback)
    }

    fun showErrorMsg(msg: String?, okayCallback: (() -> Unit)? = null) {
        showMsg(msg, false, okayCallback, null)
    }

    // TODO: use instead ErrorReportControl.showErrorDialog coroutine for error messages.
    private fun showMsg(msg: String?, isCancelable: Boolean, okayCallback: (() -> Unit)?, reportCallback: (() -> Unit)?) {
        Log.i(TAG, "showErrorMessage message:$msg")
        try {
            val activity = CurrentActivityHolder.currentActivity
            if (activity != null) {
                activity.runOnUiThread {
                    val spanned = htmlToSpan(msg)

                    val dlgBuilder = AlertDialog.Builder(activity)
                        .setMessage(spanned)
                        .setCancelable(isCancelable)
                        .setPositiveButton(R.string.okay) { _, _ -> okayCallback?.invoke() }

                    // if cancelable then show a Cancel button
                    if (isCancelable) {
                        dlgBuilder.setNegativeButton(R.string.cancel, null)
                    }

                    // enable report to andbible errors email list
                    if (reportCallback != null) {
                        dlgBuilder.setNeutralButton(R.string.report_error) { dialog, buttonId -> reportCallback.invoke() }
                    }
                    val d = dlgBuilder.show()
                    d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
                }
            } else {
                Toast.makeText(application.applicationContext, msg, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message.  Original error msg:$msg", e)
        }
    }

    suspend fun showMsg2(activity: ActivityBase, msgId: Int, isCancelable: Boolean = false, showReport: Boolean = false): Result {
        return showMsg2(activity, application.getString(msgId), isCancelable, showReport)
    }

    enum class Result {OK, CANCEL, REPORT, ERROR}

    private suspend fun showMsg2(activity: ActivityBase, msg: String, isCancelable: Boolean = false, showReport: Boolean = false): Result {
        Log.i(TAG, "showErrorMesage message:$msg")
        var result = Result.ERROR
        try {
            withContext(Dispatchers.Main) {
                val spanned = htmlToSpan(msg)

                result = suspendCoroutine {
                    val dlgBuilder = AlertDialog.Builder(activity)
                        .setMessage(spanned)
                        .setCancelable(isCancelable)
                        .setPositiveButton(R.string.okay) { _, _ -> it.resume(Result.OK) }

                    if (isCancelable) {
                        dlgBuilder.setNegativeButton(R.string.cancel) { _, _ ->
                            it.resume(Result.CANCEL)
                        }
                    }

                    if (showReport) {
                        dlgBuilder.setNeutralButton(R.string.report_error) { _, _ -> it.resume(Result.REPORT) }
                    }
                    val d = dlgBuilder.show()
                    d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message.  Original error msg:$msg", e)
        }
        return result
    }

    suspend fun simpleQuestion(context: Context, title: String? = null, message: String? = null) = suspendCoroutine {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ -> it.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> it.resume(false) }
            .setOnCancelListener { _ -> it.resume(false) }
            .show()
    }
}
