package net.bible.android.control.event

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.UserNotifier

/**
 * App-owned subscription, installed before startup can emit messages. Registration is synchronous;
 * delivery waits for [attach] without needing Koin or a UI host. The notifier's dialog queue and
 * toast state retain messages until the UI mounts. [close] belongs to app teardown, not composition.
 */
@kotlin.native.HiddenFromObjC
class UserMessagesPresenter(scope: CoroutineScope, messages: Events<UserMessage> = UserMessages.messages) : AutoCloseable {
    private val pending = Channel<UserMessage>(Channel.UNLIMITED)
    private val notifier = CompletableDeferred<UserNotifier>()
    private val subscription = messages.subscribe { pending.trySend(it) }
    private val job = scope.launch(Dispatchers.Main.immediate) {
        val target = notifier.await()
        for (message in pending) {
            try {
                when (message) {
                    is UserMessage.Toast -> message.text?.let { target.showInfo(it, message.long) } ?: dropped(message)
                    is UserMessage.ErrorNotification -> message.text?.let { target.showError(it) } ?: dropped(message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Presenting a user message failed", e)
            }
        }
    }

    /** Publishes the first notifier once its dependencies exist; repeated startup is harmless. */
    fun attach(notifier: UserNotifier) { this.notifier.complete(notifier) }

    override fun close() {
        subscription.cancel()
        job.cancel()
        pending.cancel()
    }

    private fun dropped(message: UserMessage) {
        Log.w(TAG, "User message without text dropped (Android resource id only): $message")
    }

    private companion object { const val TAG = "UserMessagesPresenter" }
}
