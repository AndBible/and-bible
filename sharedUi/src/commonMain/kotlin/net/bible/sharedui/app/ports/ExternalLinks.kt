@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.Strings

/** Opening web links; Compose link routing asks first in discrete builds. */
@kotlin.native.HiddenFromObjC
interface ExternalLinks {
    val askBeforeOpening: Boolean
    /** Opens immediately; callers that have already confirmed must not ask again. */
    fun open(url: String)

    /** Non-Compose document links always ask, independently of the discrete-mode UI routing. */
    fun confirmAndOpen(url: String, dialogs: AppDialogController, strings: () -> Strings) {
        dialogs.post(AppDialogRequest.Localized {
            val s = strings()
            AppDialogRequest.Confirm(
                title = s.externalLink,
                message = s.externalLinkQuestion(url),
                confirmText = s.okay,
                dismissText = s.cancel,
            )
        }) { result ->
            if (result == AppDialogResult.Ok) open(url)
        }
    }
}
