package net.bible.android.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.bible.service.cloudsync.SyncScheduler
import net.bible.service.cloudsync.SyncService
import net.bible.sharedcore.log.Log

/** Owns the service hand-off completion, shared by the engine and its Android service. */
class AndroidSyncScheduler(private val context: Context) : SyncScheduler {
    private var pending: CompletableDeferred<Boolean>? = null
    override fun startDatabaseSync(): Deferred<Boolean> = synchronized(this) {
        pending?.takeUnless { it.isCompleted }?.let { return@synchronized it }
        val result = CompletableDeferred<Boolean>()
        pending = result
        val intent = Intent(context, SyncService::class.java).setAction(SyncService.START_SERVICE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: IllegalStateException) {
            Log.e("DeviceSync", "Could not start sync service", e)
            result.complete(false)
            pending = null
        }
        result
    }
    fun complete(ran: Boolean) = synchronized(this) {
        pending?.complete(ran)
        pending = null
    }
}
