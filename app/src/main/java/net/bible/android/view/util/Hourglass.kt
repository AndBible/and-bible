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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
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
 * C2: a never-dismissed Progress locks the whole app (a Progress no longer blocks answerable
 * requests per C1, but an orphaned one still sits there forever). Three orphan paths this guards
 * against:
 *  - `show()` twice on one instance before `dismiss()` (e.g. a `dismiss()` launched on Main racing a
 *    later `show()` from an IO thread) -- [show] dismisses any id this instance still holds first.
 *  - an exception after `show()` with no caller `finally` -- the spinner is tied to the calling
 *    coroutine's own [Job] via [currentCoroutineContext], so completion (success, exception, or
 *    cancellation) always dismisses it.
 *  - the caller's coroutine being cancelled mid-work -- same [Job] completion hook covers this too.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Hourglass(val context: Context) {
    @Volatile private var id: Long? = null
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    suspend fun show(messageId: Int = R.string.please_wait) {
        val previous = id
        if (previous != null) {
            withContext(Dispatchers.Main) { dialogs.dismiss(previous) }
        }
        val newId = withContext(Dispatchers.Main) {
            dialogs.show(AppDialogRequest.Progress(title = null, message = application.getString(messageId)))
        }
        id = newId
        // Ties this Progress to the caller's own coroutine: however it ends (normally, an exception,
        // or cancellation), the Progress it raised is dismissed. Dismissing an id no longer in the
        // queue (e.g. because dismiss() already removed it) is a no-op.
        currentCoroutineContext()[Job]?.invokeOnCompletion { dialogs.dismiss(newId) }
    }

    suspend fun dismiss() {
        val current = id
        if (current == null) {
            Log.e(TAG, "Hourglass already dismissed!")
        } else {
            withContext(Dispatchers.Main) { dialogs.dismiss(current) }
        }
        id = null
    }

    companion object {
        private const val TAG = "Hourglass"
    }
}
