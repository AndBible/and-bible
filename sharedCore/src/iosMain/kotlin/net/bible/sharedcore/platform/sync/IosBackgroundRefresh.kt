package net.bible.sharedcore.platform.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.log.Log
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow

/** Translates BGTaskScheduler requests; the consumer chooses the work and scheduling policy. */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosBackgroundRefresh(
    private val scope: CoroutineScope,
    private val identifier: String = "org.andbible.ios.remote-sync-refresh",
) : BackgroundRefresh {
    override fun register(handler: suspend () -> Boolean) {
        val registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(identifier, null) { task ->
            if (task != null) {
                IosBackgroundWork(scope, handler) { ok ->
                    task.expirationHandler = null
                    task.setTaskCompletedWithSuccess(ok)
                }.start { expire -> task.expirationHandler = expire }
            }
        }
        if (!registered) Log.w(TAG, "Background refresh registration refused: $identifier")
    }

    override fun schedule(earliestSeconds: Long) {
        val request = BGAppRefreshTaskRequest(identifier).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(earliestSeconds.toDouble())
        }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                Log.w(TAG, "Background refresh submission failed: ${error.value?.localizedDescription}")
            }
        }
    }

    override fun cancel() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(identifier)
    }

    private companion object { const val TAG = "IosBackgroundRefresh" }
}
