package net.bible.android.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.service.cloudsync.SyncService
import net.bible.sharedcore.log.Log

/** Owns the service hand-off completion, shared by the engine and its Android service. */
class AndroidSyncScheduler(private val context: Context) : SyncScheduler {
    private var pending: CompletableDeferred<Boolean>? = null
    private var leavePending = false
    override fun startDatabaseSync(): Deferred<Boolean> = start(false)
    override fun startUserInitiatedDatabaseSync(): Deferred<Boolean> = start(true)

    /** Registers leave work before waking the service; null means no owner and in-process fallback is required. */
    fun startLeave(): Deferred<Boolean>? = synchronized(this) {
        val existing = pending?.takeUnless { it.isCompleted }
        val result = existing ?: CompletableDeferred<Boolean>().also { pending = it }
        leavePending = true
        val intent = Intent(context, SyncService::class.java).setAction(SyncService.ACTION_LEAVE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            result
        } catch (e: IllegalStateException) {
            Log.e("DeviceSync", "Could not start sync service", e)
            if (existing == null) {
                result.complete(false)
                pending = null
                leavePending = false
                null
            } else result
        }
    }

    private fun start(userInitiated: Boolean): Deferred<Boolean> = synchronized(this) {
        pending?.takeUnless { it.isCompleted }?.let { return@synchronized it }
        val result = CompletableDeferred<Boolean>()
        pending = result
        val intent = Intent(context, SyncService::class.java).setAction(SyncService.START_SERVICE)
            .putExtra(SyncService.USER_INITIATED, userInitiated)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: IllegalStateException) {
            Log.e("DeviceSync", "Could not start sync service", e)
            result.complete(false)
            pending = null
        }
        result
    }
    /** Claims a pending leave, or runs non-suspending [finish] under the scheduler lock before completing the waiter. */
    fun consumeLeaveOrComplete(ran: Boolean, finish: () -> Unit = {}): Boolean {
        val result = synchronized(this) {
            if (leavePending) {
                leavePending = false
                return true
            }
            finish()
            pending.also { pending = null }
        }
        result?.complete(ran)
        return false
    }

    /** Claims leave work on initial service delivery; a late delivery with no demand is only a wakeup. */
    fun consumeLeave(): Boolean = synchronized(this) {
        leavePending.also { leavePending = false }
    }

    fun complete(ran: Boolean) = synchronized(this) {
        leavePending = false
        pending?.complete(ran)
        pending = null
    }
}
