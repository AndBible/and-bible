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
package net.bible.service.cloudsync.desktop

import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription

/**
 * Presents [UserMessages] on desktop. CloudSync and other shared code report user-visible problems (for example a category
 * disabled for an incompatible patch version) through `UserMessages`, which only the Android application subscribes to.
 * Toasts go to [UserNotifier.showInfo], error notifications to [UserNotifier.showError]; a message that carries only an
 * Android resource id cannot be resolved here and is logged and dropped. The subscription lives until [close].
 */
class DesktopUserMessagesPresenter(
    private val notifier: () -> UserNotifier,
    messages: Events<UserMessage> = UserMessages.messages,
) : AutoCloseable {
    private val subscription: Subscription = messages.subscribe(::present)

    private fun present(message: UserMessage) {
        try {
            when (message) {
                is UserMessage.Toast -> message.text?.let { notifier().showInfo(it, message.long) } ?: dropped(message)
                is UserMessage.ErrorNotification -> message.text?.let { notifier().showError(it) } ?: dropped(message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Presenting a user message failed", e)
        }
    }

    private fun dropped(message: UserMessage) { Log.w(TAG, "User message without text dropped (Android resource id only): $message") }

    override fun close() = subscription.cancel()

    private companion object { const val TAG = "DesktopUserMessages" }
}
