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
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import net.bible.service.common.displayName
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

    suspend fun showMsg2(activity: ActivityBase, msg: String, isCancelable: Boolean = false, showReport: Boolean = false): Result {
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

    suspend fun simpleQuestion(context: Context, message: String? = null, title: String? = context.getString(R.string.are_you_sure)) = suspendCoroutine {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ -> it.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> it.resume(false) }
            .setOnCancelListener { _ -> it.resume(false) }
            .show()
    }

    suspend fun simpleQuestion(context: Context, message: Int? = null, title: Int? = R.string.are_you_sure) {
        val titleStr = if(title == null) null else context.getString(title)
        val messageStr = if(message == null) null else context.getString(message)
        simpleQuestion(context, message = messageStr, title = titleStr)
    }
    suspend fun simpleInfoMessage(context: Context, key: String, message: String? = context.getString(R.string.are_you_sure)) = withContext(Dispatchers.Main) {
        suspendCoroutine {
            if (CommonUtils.settings.getBoolean("skip_$key", false)) {
                it.resume(true)
                return@suspendCoroutine
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.information)
                .setMessage(message)
                .setPositiveButton(R.string.okay) { _, _ -> it.resume(true) }
                .setNeutralButton(R.string.dont_show) { _, _ ->
                    CommonUtils.settings.setBoolean("skip_$key", true)
                    it.resume(true)
                }
                .show()
        }
    }

    suspend fun simpleInfoMessage(context: Context, key: String, message: Int? = R.string.are_you_sure): Boolean {
        val messageStr = if(message == null) null else context.getString(message)
        return simpleInfoMessage(context, key, messageStr)
    }

    suspend fun <T> multiselect(context: Context, title: String, items: List<T>, itemToString: ((arg: T) -> String)? = null): List<T> = suspendCoroutine {
        val itemNames = items.map { itemToString?.let { it1 -> it1(it) }?: it.toString() }.toTypedArray()
        val checkedItems = itemNames.map { false }.toBooleanArray()
        val dialog = AlertDialog.Builder(context)
            .setPositiveButton(R.string.okay) { d, _ ->
                val selectedItems = items.filterIndexed { index, book -> checkedItems[index] }
                if (selectedItems.isEmpty()) {
                    it.resume(emptyList())
                } else {
                    it.resume(selectedItems)
                }
            }
            .setMultiChoiceItems(itemNames, checkedItems) { _, pos, value ->
                checkedItems[pos] = value
            }
            .setNeutralButton(R.string.select_all) { _, _ -> it.resume(emptyList()) }
            .setNegativeButton(R.string.cancel) { _, _ -> it.resume(emptyList()) }
            .setOnCancelListener { _ -> it.resume(emptyList()) }
            .setTitle(title)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val allSelected = checkedItems.find { !it } == null
                val newValue = !allSelected
                val v = dialog.listView
                for (i in 0 until v.count) {
                    v.setItemChecked(i, newValue)
                    checkedItems[i] = newValue
                }
                (it as Button).text =
                    context.getString(if (allSelected) R.string.select_all else R.string.select_none)
            }
        }
        dialog.show()
        CommonUtils.fixAlertDialogButtons(dialog)
    }

    suspend fun <T> multiselect(context: Context, title: Int, items: List<T>, itemToString: ((arg: T) -> String)? = null): List<T> =
        multiselect(context, context.getString(title), items, itemToString)

}
