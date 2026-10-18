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
 * C7 pending (phase 2: snackbar through the shared root): [showInfo] only logs with tag `C7-pending`.
 */
class IosUserNotifier(
    private val dialogs: AppDialogController,
    private val okText: () -> String = { "OK" },
) : UserNotifier {
    override fun showInfo(message: String, long: Boolean) {
        Log.i(TAG, "info (C7-pending: snackbar in phase 2): $message")
    }

    override fun showError(message: String, cause: Throwable?) {
        Log.e(TAG, message, cause)
        dialogs.post(AppDialogRequest.Message(title = null, message = message, confirmText = okText()))
    }

    private companion object { const val TAG = "UserNotifier" }
}
