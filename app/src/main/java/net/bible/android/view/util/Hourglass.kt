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
package net.bible.android.view.util

import android.content.Context
import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import org.koin.java.KoinJavaComponent

/**
 * Helper class to show a modal wait indicator.
 *
 * Spec D7: raises an [AppDialogRequest.Progress] in the app-wide [AppDialogController] queue rather
 * than building a `ProgressDialog` — the platform type is removed (spec §5, §8).
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Hourglass(val context: Context) {
    private var id: Long? = null
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    suspend fun show(messageId: Int = R.string.please_wait) {
        id = dialogs.show(AppDialogRequest.Progress(title = null, message = application.getString(messageId)))
    }

    suspend fun dismiss() {
        val current = id
        if (current == null) {
            Log.e(TAG, "Hourglass already dismissed!")
        } else {
            dialogs.dismiss(current)
        }
        id = null
    }

    companion object {
        private const val TAG = "Hourglass"
    }
}
