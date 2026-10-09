package net.bible.android.platform

import net.bible.android.view.activity.base.Dialogs
import net.bible.sharedcore.platform.UserNotifier

/** Routes domain-level errors to the existing [Dialogs] error dialog. */
class AndroidUserNotifier : UserNotifier {
    override fun showError(message: String, cause: Throwable?) {
        if (cause is Exception) Dialogs.showErrorMsg(message, cause) else Dialogs.showErrorMsg(message)
    }
}
