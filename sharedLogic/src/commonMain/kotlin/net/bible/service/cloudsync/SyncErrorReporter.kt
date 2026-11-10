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

package net.bible.service.cloudsync

import kotlinx.atomicfu.atomic
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages

/**
 * Coalesces cloud-sync error notifications (#4031). A failing pass used to raise one modal error per failing step and
 * database category (and a document drain one per document), and on iOS/Mac those modals queue: about ten dialogs in a
 * row, the app unusable meanwhile.
 *
 * - A [Run] (one sync pass, one document drain) shows at most ONE error, when it [ends][Run.end]: the first SPECIFIC
 *   one (a user-facing message such as a wrong password or "update the app", reported with `showReportButton = false`)
 *   if any, else the first generic one, with its cause. Callers log every failure themselves.
 * - A run that begins while the previous completed run also showed an error (no clean run since) marks its error
 *   [UserMessage.ErrorNotification.repeated] (and background) when `quietRepeats` is set: presenters record it in the
 *   activity list and show a toast instead of popping the same dialog again. The first error of a streak keeps its
 *   dialog and report button, so the user can still report it.
 * - A failure that is silent by design ([Run.noteSilentFailure], e.g. a network error of an automatic pass) neither
 *   continues nor ends the streak; a run that was cancelled ([Run.end] with `completed = false`) leaves it too.
 */
class SyncErrorReporter(private val emit: (UserMessage.ErrorNotification) -> Unit = { UserMessages.errorNotification(it) }) {
    private val previousRunFailed = atomic(false)

    /** Starts a run. [quietRepeats] false keeps the dialog even inside a failing streak (the user asked for this run). */
    fun beginRun(quietRepeats: Boolean = true): Run = Run(quietRepeats && previousRunFailed.value)

    inner class Run internal constructor(private val repeat: Boolean) {
        private val specific = atomic<UserMessage.ErrorNotification?>(null)
        private val generic = atomic<UserMessage.ErrorNotification?>(null)
        private val silentFailure = atomic(false)
        private val ended = atomic(false)

        /**
         * Records [text] as this run's error unless one of its class came first: a message without the report button
         * is specific and wins over a generic one. Shown when the run ends.
         */
        fun report(text: String, showReportButton: Boolean = true, background: Boolean, cause: Throwable? = null) {
            val message = UserMessage.ErrorNotification(text, null, showReportButton, background = background || repeat,
                kind = ActivityKind.SYNC, cause = cause, repeated = repeat)
            (if (showReportButton) generic else specific).compareAndSet(null, message)
        }

        /** A failure that shows nothing by design: it must not end a failing streak. */
        fun noteSilentFailure() { silentFailure.value = true }

        /**
         * Shows the run's one error, if any (also for a cancelled run: the failure happened). [completed] false (the run
         * was cancelled) leaves the streak as it was; otherwise a run that showed an error continues it, a clean one
         * ends it. Only the first call counts.
         */
        fun end(completed: Boolean = true) {
            prepareEnd(completed)?.invoke()
        }

        /**
         * Closes the streak without invoking consumers, so a queue can accept its next run atomically.
         * The returned delivery belongs outside the producer lock, before terminal observers.
         */
        internal fun prepareEnd(completed: Boolean = true): (() -> Unit)? {
            if (!ended.compareAndSet(false, true)) return null
            val shown = specific.value ?: generic.value
            if (completed) {
                if (shown != null) previousRunFailed.value = true
                else if (!silentFailure.value) previousRunFailed.value = false
            }
            return shown?.let { message -> { emit(message) } }
        }
    }
}
