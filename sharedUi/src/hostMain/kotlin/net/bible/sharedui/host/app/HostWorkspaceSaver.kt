package net.bible.sharedui.host.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import org.koin.core.Koin

/**
 * Persists the window repository (windows, passages, history) like Android's `NavHostComposeActivity.onPause`
 * (`saveIntoDb(false)`): the current passage is only written on such a save, so an app that is killed without ever
 * saving reopens on the default passage. Runs off the main thread and waits until the queued writes are on disk.
 * A repository that has not finished loading is never saved (`saveIntoDb` itself ignores it; [HostReadingReady]
 * guards the lookup so that asking before reading is ready does not create a fallback repository).
 */
internal object HostWorkspaceSaver {
    /**
     * Process-wide: WillResignActive and DidEnterBackground fire back to back, and `saveIntoDb` is unsynchronised
     * (it mutates the saved-entity snapshots and iterates the window list), so saves never overlap; the second one
     * finds nothing modified.
     */
    private val mutex = Mutex()

    suspend fun saveNow(
        koin: Koin,
        ready: () -> Boolean = { HostReadingReady.isReady },
        save: (WindowRepository) -> Unit = { it.saveIntoDb(false) },
    ) {
        if (!ready()) return
        mutex.withLock {
            withContext(Dispatchers.Default) {
                val repository = koin.get<WindowControl>().windowRepository
                save(repository)
                repository.persistence.awaitPersisted()
            }
        }
    }
}
