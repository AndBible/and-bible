package net.bible.android.platform

import net.bible.android.control.event.UserMessages
import net.bible.android.view.activity.base.Dialogs
import net.bible.sharedcore.platform.UserNotifier

/** Routes domain-level errors to the existing [Dialogs] error dialog. */
class AndroidUserNotifier : UserNotifier {
    override fun showInfo(message: String, long: Boolean) {
        if (long) UserMessages.toast(message, long = true) else UserMessages.toast(message)
    }

    override fun showError(message: String, cause: Throwable?) {
        if (cause is Exception) Dialogs.showErrorMsg(message, cause) else Dialogs.showErrorMsg(message)
    }
}
