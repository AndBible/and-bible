@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.download

import kotlinx.serialization.builtins.ListSerializer
import net.bible.android.control.download.PendingRepoInstall
import net.bible.android.database.SwordDocumentInfo
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.Strings

/**
 * Reports, once per launch, the repository installs an earlier run requested but could not finish because the app
 * was suspended or terminated mid-transfer (#3973), and offers to download them again.
 *
 * "Download again" opens the Download screen with exactly those documents requested (the same `documentIds` route
 * the Welcome flow uses); the requests stay recorded until that download settles, so another interruption is
 * reported again. The explicit decline (the dialog's neutral button) forgets them. A dialog dropped without an
 * answer ([AppDialogResult.Cancel], e.g. `AppDialogController.cancelAll`) keeps them for the next launch.
 */
@kotlin.native.HiddenFromObjC
object InterruptedInstallsNotice {
    suspend fun show(
        interrupted: suspend () -> List<PendingRepoInstall>,
        forget: suspend (List<PendingRepoInstall>) -> Unit,
        ask: suspend (AppDialogRequest) -> AppDialogResult,
        strings: Strings,
        openDownload: (documentIdsJson: String) -> Unit,
    ) {
        val requests = interrupted()
        if (requests.isEmpty()) return
        val request = AppDialogRequest.Message(
            title = strings.interruptedDownloadsTitle,
            // Built here, one document per line: a resource's own line breaks do not survive every platform's table.
            message = strings.interruptedDownloadsMessage + "\n\n" + requests.joinToString("\n") { it.initials } +
                "\n\n" + strings.interruptedDownloadsQuestion,
            confirmText = strings.downloadAgain,
            neutralText = strings.cancel,
        )
        when (ask(request)) {
            AppDialogResult.Ok -> openDownload(documentIdsJson(requests))
            AppDialogResult.Neutral -> forget(requests)
            else -> Unit
        }
    }

    /** The Download route's `documentIds` payload: initials plus the repository each was requested from. */
    fun documentIdsJson(requests: List<PendingRepoInstall>): String = AppJson.encodeToString(
        ListSerializer(SwordDocumentInfo.serializer()),
        requests.map { SwordDocumentInfo(initials = it.initials, name = "", abbreviation = "", language = "", repository = it.repository) },
    )
}
