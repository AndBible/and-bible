package net.bible.android.control.activity

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * "Open the background activity list" requests (#4012): a native notification click (and later other entry points)
 * calls [request]; the reading host collects [requests] and opens the list if the activity menu is enabled.
 *
 * One request is held until a collector takes it, so a click while the host is not composed (Settings open, say) still
 * opens the list when it appears. Each request is delivered once, not replayed to later collectors; several pending
 * requests collapse into one. [request] never suspends and works from any thread.
 */
class ActivityListRequests {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    val requests: Flow<Unit> = channel.receiveAsFlow()

    fun request() { channel.trySend(Unit) }
}
