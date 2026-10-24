package net.bible.service.llm

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.platform.AppCoroutineScope

/** Registers synchronously, buffering events until the application owner can process them. */
internal fun <T : Any> Events<T>.collectOwned(
    scope: AppCoroutineScope,
    handler: suspend (T) -> Unit,
): Job {
    val queue = Channel<T>(Channel.UNLIMITED)
    val subscription = subscribe { queue.trySend(it) }
    return scope.launch {
        for (event in queue) handler(event)
    }.also { job ->
        job.invokeOnCompletion {
            subscription.cancel()
            queue.cancel()
        }
    }
}
