package net.bible.sharedcore.platform

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.sharedcore.log.Log
import kotlin.coroutines.CoroutineContext

/**
 * Application-lifetime scope: writes that must outlive a screen. SupervisorJob + Dispatchers.Default.
 * A failed fire-and-forget child is logged, not a crash.
 */
class AppCoroutineScope(
    context: CoroutineContext = SupervisorJob() + Dispatchers.Default +
        CoroutineExceptionHandler { _, e -> Log.e("AppCoroutineScope", "Uncaught", e) },
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = context
}
