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
package net.bible.android.view.activity.base

import android.app.AlertDialog
import android.util.Log
import android.widget.Toast
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl

/**
 * Class to manage the display of various dialogs
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Dialogs private constructor() {
    private val errorReportControl: ErrorReportControl = application.applicationComponent.errorReportControl()
    private val doNothingCallback = Callback {
        // by default do nothing when user clicks okay
    }

    fun showMsg(msgId: Int, param: String?) {
        showErrorMsg(application.getString(msgId, param))
    }

    fun showMsg(msgId: Int, isCancelable: Boolean, okayCallback: Callback) {
        showMsg(application.getString(msgId), isCancelable, okayCallback, null)
    }

    fun showMsg(msgId: Int, isCancelable: Boolean, okayCallback: () -> Unit) {
        showMsg(application.getString(msgId), isCancelable, Callback(okayCallback), null)
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

    fun showErrorMsg(msgId: Int, okayCallback: Callback) {
        showErrorMsg(application.getString(msgId), okayCallback)
    }

    fun showErrorMsg(msgId: Int, okayCallback: () -> Unit) {
        showErrorMsg(application.getString(msgId), Callback(okayCallback))
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
        val reportCallback = Callback { errorReportControl.sendErrorReportEmail(e) }
        showMsg(message, false, doNothingCallback, reportCallback)
    }

    @JvmOverloads
    fun showErrorMsg(msg: String?, okayCallback: Callback = doNothingCallback) {
        showMsg(msg, false, okayCallback, null)
    }

    fun showErrorMsg(msg: String?, okayCallback: () -> Unit) {
        showErrorMsg(msg, Callback(okayCallback))
    }

    private fun showMsg(msg: String?, isCancelable: Boolean, okayCallback: Callback, reportCallback: Callback?) {
        Log.d(TAG, "showErrorMesage message:$msg")
        try {
            val activity = CurrentActivityHolder.getInstance().currentActivity
            if (activity != null) {
                activity.runOnUiThread {
                    val dlgBuilder = AlertDialog.Builder(activity)
                        .setMessage(msg)
                        .setCancelable(isCancelable)
                        .setPositiveButton(R.string.okay) { dialog, buttonId -> okayCallback.okay() }

                    // if cancelable then show a Cancel button
                    if (isCancelable) {
                        dlgBuilder.setNegativeButton(R.string.cancel) { dialog, buttonId ->
                            // do nothing
                        }
                    }

                    // enable report to andbible errors email list
                    if (reportCallback != null) {
                        dlgBuilder.setNeutralButton(R.string.report_error) { dialog, buttonId -> reportCallback.okay() }
                    }
                    dlgBuilder.show()
                }
            } else {
                Toast.makeText(application.applicationContext, msg, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message.  Original error msg:$msg", e)
        }
    }

    companion object {
        private const val TAG = "Dialogs"
        val instance = Dialogs()
    }

}
