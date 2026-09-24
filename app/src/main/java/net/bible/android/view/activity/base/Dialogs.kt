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
package net.bible.android.view.activity.base

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl
import net.bible.service.common.htmlToSpan
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.koin.java.KoinJavaComponent

/**
 * Class to manage the display of various dialogs.
 *
 * Spec 2026-09-11 §5.3: `Dialogs` is a thin shim over the app-wide [AppDialogController] /
 * [AgentPermissionController] — it no longer builds any `android.app.AlertDialog` itself. Every
 * overload below keeps its old public signature and old-caller-visible behaviour (spec's "no content
 * changes"); only the underlying dialog mechanism changed. New callers should use
 * `AppDialogController`/`AgentPermissionController` directly, or the feature's own dialog state —
 * hence the `@Deprecated` markers, kept without `ReplaceWith` since there is no single drop-in
 * replacement expression.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
private const val TAG = "Dialogs"

object Dialogs {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) =
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showMsg(msgId: Int, isCancelable: Boolean, okayCallback: (() -> Unit)) {
        showMsg(application.getString(msgId), isCancelable, okayCallback, null)
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showMsg(msgId: Int) {
        showErrorMsg(application.getString(msgId))
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showErrorMsg(msgId: Int) {
        showErrorMsg(application.getString(msgId))
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showErrorMsg(msgId: Int, param: String?) {
        showErrorMsg(application.getString(msgId, param))
    }

    /**
     * Show error message and allow reporting of exception via e-mail to and-bible
     */
    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showErrorMsg(msgId: Int, e: Exception?) {
        showErrorMsg(application.getString(msgId), e)
    }

    /**
     * Show error message and allow reporting of exception via e-mail to and-bible
     */
    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showErrorMsg(message: String?, e: Exception?) {
        val reportCallback = { ErrorReportControl.sendErrorReportEmail(e, source = "error message") }
        showMsg(message, false, null, reportCallback)
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    fun showErrorMsg(msg: String?, okayCallback: (() -> Unit)? = null) {
        showMsg(msg, false, okayCallback, null)
    }

    // TODO: use instead ErrorReportControl.showErrorDialog coroutine for error messages.
    private fun showMsg(msg: String?, isCancelable: Boolean, okayCallback: (() -> Unit)?, reportCallback: (() -> Unit)?) {
        Log.i(TAG, "showErrorMessage message:$msg")
        if (CurrentActivityHolder.currentActivity == null) {
            // No host will ever draw it (app in background with no Activity): today's Toast fallback.
            mainHandler.post { Toast.makeText(application.applicationContext, htmlToSpan(msg), Toast.LENGTH_LONG).show() }
            return
        }
        dialogs.post(
            AppDialogRequest.Message(
                title = null,
                message = msg.orEmpty(),
                confirmText = application.getString(R.string.okay),
                dismissText = if (isCancelable) application.getString(R.string.cancel) else null,
                neutralText = if (reportCallback != null) application.getString(R.string.report_error) else null,
                cancellable = isCancelable,
            ),
        ) { result ->
            onMain {
                when (result) {
                    AppDialogResult.Ok -> okayCallback?.invoke()
                    AppDialogResult.Neutral -> reportCallback?.invoke()
                    else -> Unit
                }
            }
        }
    }

    enum class Result { OK, CANCEL, REPORT, ERROR }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    suspend fun showMsg2(activity: ActivityBase, msgId: Int, isCancelable: Boolean = false, showReport: Boolean = false): Result {
        return showMsg2(activity, application.getString(msgId), isCancelable, showReport)
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    suspend fun showMsg2(activity: ActivityBase, msg: String, isCancelable: Boolean = false, showReport: Boolean = false): Result {
        Log.i(TAG, "showErrorMesage message:$msg")
        val result = dialogs.await(
            AppDialogRequest.Message(
                title = null,
                message = msg,
                confirmText = activity.getString(R.string.okay),
                dismissText = if (isCancelable) activity.getString(R.string.cancel) else null,
                neutralText = if (showReport) activity.getString(R.string.report_error) else null,
                cancellable = isCancelable,
            ),
        )
        return when (result) {
            AppDialogResult.Ok -> Result.OK
            AppDialogResult.Cancel -> Result.CANCEL
            AppDialogResult.Neutral -> Result.REPORT
            else -> Result.ERROR
        }
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    suspend fun simpleQuestion(context: Context, message: String? = null, title: String? = context.getString(R.string.are_you_sure)): Boolean =
        dialogs.await(
            AppDialogRequest.Confirm(
                title = title,
                message = message,
                confirmText = context.getString(R.string.okay),
                dismissText = context.getString(R.string.cancel),
            ),
        ) == AppDialogResult.Ok

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    suspend fun <T> multiselect(
        context: Context,
        title: String,
        items: List<T>,
        itemToString: ((arg: T) -> String)? = null,
        preSelected: ((arg: T) -> Boolean)? = null,
    ): List<T> {
        val choices = items.mapIndexed { index, item ->
            SettingsItem.Choice(index.toString(), itemToString?.invoke(item) ?: item.toString())
        }
        val selectedIds = items.mapIndexedNotNull { index, item -> if (preSelected?.invoke(item) == true) index.toString() else null }
        val result = dialogs.await(
            AppDialogRequest.MultiChoice(
                title = title,
                options = choices,
                selectedIds = selectedIds,
                confirmText = context.getString(R.string.okay),
                dismissText = context.getString(R.string.cancel),
                selectAllText = context.getString(R.string.select_all),
                selectNoneText = context.getString(R.string.select_none),
            ),
        )
        val ids = (result as? AppDialogResult.SelectedMany)?.ids ?: return emptyList()
        return items.filterIndexed { index, _ -> index.toString() in ids }
    }

    @Deprecated("Spec 2026-09-11 §5.3: use AppDialogController or the feature's own dialog state", level = DeprecationLevel.WARNING)
    suspend fun <T> multiselect(context: Context, title: Int, items: List<T>, itemToString: ((arg: T) -> String)? = null): List<T> =
        multiselect(context, context.getString(title), items, itemToString)

    /**
     * Result of agent permission dialog.
     */
    enum class AgentPermissionResult {
        ALLOW,              // Allow this one operation
        ALLOW_FOR_SESSION,  // Allow this tool for this session
        ALLOW_ALL_SESSION,  // Allow all tools for this session
        ALLOW_ALWAYS,       // Permanently allow this tool
        DENY                // Deny this operation
    }

    /**
     * Show a dialog asking user permission for an agent tool operation.
     *
     * Always routed through the app-wide [AgentPermissionController] (Task 7): [AppDialogOverlay]
     * renders its `pending` request on every host, so there is no longer a host-specific
     * Compose-vs-native split here — [context] is unused and kept only because its one caller
     * ([net.bible.service.llm.agent.AgentExecutor]) still passes it.
     *
     * @param context unused; kept for source compatibility with the one caller
     * @param toolDisplayName User-facing translated name of the tool
     * @param toolDescription Description of what the tool does
     * @return AgentPermissionResult indicating user's choice
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun agentPermissionDialog(
        context: Context,
        toolDisplayName: String,
        toolDescription: String,
        actionDescription: String? = null,
    ): AgentPermissionResult =
        KoinJavaComponent.get<AgentPermissionController>(AgentPermissionController::class.java)
            .await(AgentPermissionRequest(toolDisplayName, toolDescription, actionDescription))
            .toResult()
}

/**
 * The one true [AgentPermissionChoice] → [Dialogs.AgentPermissionResult] mapping, extracted out of
 * [Dialogs.agentPermissionDialog] so it is a plain function `AgentPermissionHostTest` can call
 * directly and assert pair-by-pair. Kept `internal` (not private) purely so the test can see it — no
 * other caller is intended.
 */
internal fun AgentPermissionChoice.toResult(): Dialogs.AgentPermissionResult = when (this) {
    AgentPermissionChoice.ALLOW -> Dialogs.AgentPermissionResult.ALLOW
    AgentPermissionChoice.ALLOW_FOR_SESSION -> Dialogs.AgentPermissionResult.ALLOW_FOR_SESSION
    AgentPermissionChoice.ALLOW_ALL_SESSION -> Dialogs.AgentPermissionResult.ALLOW_ALL_SESSION
    AgentPermissionChoice.ALLOW_ALWAYS -> Dialogs.AgentPermissionResult.ALLOW_ALWAYS
    AgentPermissionChoice.DENY -> Dialogs.AgentPermissionResult.DENY
}
