@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.NotificationPermission

@kotlin.native.HiddenFromObjC
class NotificationPermissionLogic(
    private val asked: () -> Boolean,
    private val markAsked: () -> Unit,
    private val enabled: () -> Boolean,
    private val request: suspend () -> Boolean,
) : NotificationPermission {
    private val mutex = Mutex()

    override suspend fun ensureAsked() {
        val shouldRequest = mutex.withLock {
            if (!enabled() || asked()) false else {
                markAsked()
                true
            }
        }
        if (!shouldRequest) return
        try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NotificationPermission", "Notification permission request failed", e)
        }
    }
}
