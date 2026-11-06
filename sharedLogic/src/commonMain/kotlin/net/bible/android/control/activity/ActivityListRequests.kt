package net.bible.android.control.activity

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "Open the background activity list" requests (#4012): a native notification click (and later other entry points)
 * calls [request]; the reading host collects [requests] and opens the list if the activity menu is enabled.
 *
 * Nothing is replayed: a request made while no host collects, or while the preference is off, is dropped. The buffer
 * is one deep and drops the oldest, so [request] never suspends and works from any thread.
 */
class ActivityListRequests {
    private val flow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val requests: SharedFlow<Unit> = flow.asSharedFlow()

    fun request() { flow.tryEmit(Unit) }
}
