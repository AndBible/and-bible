/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedlogic.platform.ios

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest

/**
 * iOS [UserNotifier]. [showError] raises an owner-less [AppDialogRequest.Message] on [dialogs] (fire-and-forget; the
 * shared dialog host draws it, there is no activity to own it). [okText] is the confirm button label, read per call
 * so a language change is picked up; the iOS binding passes the localized "OK".
 *
 * U7 / ADR 0007: this adapter posts NO system notifications today. [showError] is an in-app dialog,
 * not lock-screen text, so it remains useful in discrete mode. Any future system-notification posting
 * path must check the live device-local `discrete_mode` immediately before posting and hide content
 * before the PIN; it must not fake the installed app name. Do not reuse the in-app message as a
 * system notification payload without that check.
 *
 * [showInfo] logs and, when [onInfo] is set (the iOS binding passes the in-app toast), shows the message.
 */
class IosUserNotifier(
    private val dialogs: AppDialogController,
    /** Where an info message is shown to the user (the in-app toast); null only logs. */
    private val onInfo: ((message: String, long: Boolean) -> Unit)? = null,
    private val okText: () -> String = { "OK" },
) : UserNotifier {
    override fun showInfo(message: String, long: Boolean) {
        Log.i(TAG, "info: $message")
        onInfo?.invoke(message, long)
    }

    override fun showError(message: String, cause: Throwable?) {
        Log.e(TAG, message, cause)
        dialogs.post(AppDialogRequest.Message(title = null, message = message, confirmText = okText()))
    }

    private companion object { const val TAG = "UserNotifier" }
}
