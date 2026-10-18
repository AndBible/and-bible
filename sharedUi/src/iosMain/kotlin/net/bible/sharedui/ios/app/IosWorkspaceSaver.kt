package net.bible.sharedui.ios.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import org.koin.core.Koin

/**
 * Persists the window repository (windows, passages, history) like Android's `NavHostComposeActivity.onPause`
 * (`saveIntoDb(false)`): the current passage is only written on such a save, so an app that is killed without ever
 * saving reopens on the default passage. Runs off the main thread and waits until the queued writes are on disk.
 * A repository that has not finished loading is never saved (`saveIntoDb` itself ignores it; [IosReadingReady]
 * guards the lookup so that asking before reading is ready does not create a fallback repository).
 */
internal object IosWorkspaceSaver {
    suspend fun saveNow(koin: Koin, ready: () -> Boolean = { IosReadingReady.isReady }) {
        if (!ready()) return
        withContext(Dispatchers.Default) {
            val repository = koin.get<WindowControl>().windowRepository
            repository.saveIntoDb(false)
            repository.persistence.awaitPersisted()
        }
    }
}
