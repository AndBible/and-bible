package net.bible.android.control.event

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.bible.sharedcore.event.Events
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.rerouteOrNull
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.UserNotifier

/**
 * App-owned subscription, installed before startup can emit messages. Registration is synchronous;
 * delivery waits for [attach] without needing Koin or a UI host. The notifier's dialog queue and
 * toast state retain messages until the UI mounts. [close] belongs to app teardown, not composition.
 *
 * [reroute] takes an error notification's text, background flag and kind, records it in the activity list (#4007) and
 * answers the toast text to show instead of the dialog (background work only); null keeps the dialog. It runs on Main, after [attach].
 */
@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
@kotlin.native.HiddenFromObjC
class UserMessagesPresenter(scope: CoroutineScope, messages: Events<UserMessage> = UserMessages.messages,
    private val reroute: (String, Boolean, ActivityKind) -> String? = { _, _, _ -> null },
) : AutoCloseable {
    private val pending = Channel<UserMessage>(Channel.UNLIMITED)
    private val notifier = CompletableDeferred<UserNotifier>()
    private val subscription = messages.subscribe { pending.trySend(it) }
    private val job = scope.launch(Dispatchers.Main.immediate) {
        val target = notifier.await()
        for (message in pending) {
            try {
                when (message) {
                    is UserMessage.Toast -> message.text?.let { target.showInfo(it, message.long) } ?: dropped(message)
                    is UserMessage.ErrorNotification -> message.text?.let { text -> reroute.rerouteOrNull(text, message.background, message.kind)?.let { target.showInfo(it, false) } ?: target.showError(text) } ?: dropped(message)
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
