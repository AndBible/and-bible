package net.bible.android.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.service.cloudsync.SyncService
import net.bible.sharedcore.log.Log

/** Owns completion and one coalesced follow-up for the service or its in-process fallback. */
class AndroidSyncScheduler(private val context: Context) : SyncScheduler {
    enum class Request { Leave, Interactive }

    private var pending: CompletableDeferred<Boolean>? = null
    override val pendingDatabaseSync: Deferred<Boolean>? get() = synchronized(this) { pending }
    private var pendingRequest: Request? = null
    override fun startDatabaseSync(): Deferred<Boolean> = start(false)
    override fun startUserInitiatedDatabaseSync(): Deferred<Boolean> = start(true)

    /** Registers leave work before waking the service; null selects the in-process fallback. */
    fun startLeave(): Deferred<Boolean>? = synchronized(this) {
        pendingRequest = pendingRequest ?: Request.Leave
        pending?.takeUnless { it.isCompleted }?.let { return@synchronized it }
        val result = CompletableDeferred<Boolean>().also { pending = it }
        val intent = Intent(context, SyncService::class.java).setAction(SyncService.ACTION_LEAVE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            result
        } catch (e: RuntimeException) {
            Log.e("DeviceSync", "Could not start sync service", e)
            complete(false)
            null
        }
    }

    private fun start(userInitiated: Boolean): Deferred<Boolean> = synchronized(this) {
        pending?.takeUnless { it.isCompleted }?.let {
            if (userInitiated) pendingRequest = Request.Interactive
            return@synchronized it
        }
        val result = CompletableDeferred<Boolean>()
        pending = result
        val intent = Intent(context, SyncService::class.java).setAction(SyncService.START_SERVICE)
            .putExtra(SyncService.USER_INITIATED, userInitiated)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: RuntimeException) {
            Log.e("DeviceSync", "Could not start sync service", e)
            complete(false)
        }
        result
    }

    /** Claims a follow-up, or finishes the owner atomically before publishing its terminal result. */
    fun consumeRequestOrComplete(ran: Boolean, finish: () -> Unit = {}): Request? {
        val result = synchronized(this) {
            pendingRequest?.let {
                pendingRequest = null
                return it
            }
            finish()
            pending.also { pending = null }
        }
        result?.complete(ran)
        return null
    }

    /** Claims queued work on initial delivery; a late delivery without demand is only a wakeup. */
    fun consumeRequest(): Request? = synchronized(this) {
        pendingRequest.also { pendingRequest = null }
    }

    /** Registers the refused-start fallback as an owner so subsequent starts join it. */
    suspend fun runFallback(block: suspend (userInitiated: Boolean) -> Boolean): Boolean {
        val result = CompletableDeferred<Boolean>()
        val owner = synchronized(this) {
            pending?.takeUnless { it.isCompleted }?.also {
                pendingRequest = pendingRequest ?: Request.Leave
            } ?: result.also { pending = it }
        }
        if (owner !== result) return owner.await()
        var succeeded = false
        var request = Request.Leave
        try {
            do {
                succeeded = false
                try {
                    succeeded = block(request == Request.Interactive)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    Log.w("DeviceSync", "In-process sync operation was cancelled", e)
                } catch (e: Exception) {
                    Log.e("DeviceSync", "In-process sync failed", e)
                }
                request = consumeRequestOrComplete(succeeded) ?: return succeeded
            } while (true)
        } finally {
            synchronized(this) {
                if (pending === result) complete(succeeded)
            }
        }
    }

    fun complete(ran: Boolean) = synchronized(this) {
        pendingRequest = null
        pending?.complete(ran)
        pending = null
    }
}
