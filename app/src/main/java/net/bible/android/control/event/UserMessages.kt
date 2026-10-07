/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.event

import androidx.annotation.StringRes
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

/**
 * A one-shot message to show the user. Resource ids are resolved by the presenter
 * (`BibleApplication`), so a caller on a background thread needs no `Context`.
 */
sealed interface UserMessage {
    data class Toast(val text: String?, @StringRes val textId: Int?, val long: Boolean) : UserMessage
    data class ErrorNotification(
        val text: String?,
        @StringRes val textId: Int?,
        val showReportButton: Boolean,
    ) : UserMessage
}

/**
 * Owner of user-visible one-shot messages: toasts and error notifications. Callable from any
 * thread; [messages] delivers synchronously on the caller's thread to `subscribe` handlers, and
 * `BibleApplication` presents them with `subscribeOnMain`.
 */
object UserMessages {
    private val _messages = EventSource<UserMessage>()
    val messages: Events<UserMessage> = _messages

    fun toast(@StringRes id: Int, long: Boolean = false) =
        _messages.emit(UserMessage.Toast(null, id, long))

    fun toast(text: String) = _messages.emit(UserMessage.Toast(text, null, long = false))

    fun errorNotification(@StringRes id: Int) =
        _messages.emit(UserMessage.ErrorNotification(null, id, showReportButton = true))

    fun errorNotification(text: String, showReportButton: Boolean = true) =
        _messages.emit(UserMessage.ErrorNotification(text, null, showReportButton))
}
