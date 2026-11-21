package net.bible.sharedcore.cloud

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** How often the cloud documents list re-reads the listing cache while a document sync runs. */
const val CLOUD_DOCUMENTS_LIVE_REFRESH_INTERVAL_MS = 1_500L

/**
 * Keeps the cloud documents list current WHILE a document sync runs (#4180): whenever [running] is
 * true, calls [reload] (a cache-only re-read, never the network) every [intervalMs]; going false
 * stops the loop (the caller's end-of-sync network rescan takes over). Never returns; collect it in
 * a lifecycle-bound coroutine.
 */
suspend fun runCloudDocumentsLiveRefresh(
    running: Flow<Boolean>,
    intervalMs: Long = CLOUD_DOCUMENTS_LIVE_REFRESH_INTERVAL_MS,
    reload: suspend () -> Unit,
) {
    running.collectLatest { isRunning ->
        if (!isRunning) return@collectLatest
        while (true) {
            delay(intervalMs)
            reload()
        }
    }
}
