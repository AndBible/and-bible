package net.bible.sharedui.app.bookmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.view.activity.nav.subscribeToBookmarkSync
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.event.EventSource
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookmarkSyncSubscriptionCommonTest {
    @Test fun bookmarkSyncDispatchesToMainAndUnsubscribeStopsFutureEvents() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        DatabaseContainer.resetBookmarksSyncedForTest()
        var unsubscribe: (() -> Unit)? = null
        try {
            var calls = 0
            unsubscribe = subscribeToBookmarkSync { calls++ }
            @Suppress("UNCHECKED_CAST")
            val events = DatabaseContainer.bookmarksSynced as EventSource<Any>
            events.emit(emptyList<Any>())
            assertEquals(0, calls, "delivery must dispatch to main rather than run synchronously")
            runCurrent()
            assertEquals(1, calls)
            unsubscribe()
            unsubscribe()
            events.emit(emptyList<Any>())
            runCurrent()
            assertEquals(1, calls)
        } finally {
            unsubscribe?.invoke()
            DatabaseContainer.resetBookmarksSyncedForTest()
            Dispatchers.resetMain()
        }
    }
}
