package net.bible.android.control.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import net.bible.android.control.event.UserMessage
import net.bible.sharedcore.event.EventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecordErrorNotificationTest {
    @Test
    fun recordsWithTheMessageKindAndResolvedTextRegardlessOfPreference() = runTest {
        val center = ActivityCenter(backgroundScope) { currentTime }
        val source = SyncActivitySource(center, EventSource<Boolean>()) { "Syncing" }
        recordErrorNotification({ source }, UserMessage.ErrorNotification("plain text", null, true, kind = ActivityKind.DOWNLOAD)) { "unused" }
        recordErrorNotification({ source }, UserMessage.ErrorNotification(null, 42, true, background = true, kind = ActivityKind.SYNC)) { "resource $it" }
        assertEquals(
            mapOf<String?, ActivityKind>("plain text" to ActivityKind.DOWNLOAD, "resource 42" to ActivityKind.SYNC),
            center.items.value.associate { it.message to it.kind },
        )
        assertEquals(2, center.unreadFailures.value)
    }

    @Test
    fun noSourceOrAThrowingResolverRecordsNothingAndDoesNotThrow() = runTest {
        recordErrorNotification({ null }, UserMessage.ErrorNotification("x", null, true)) { "y" }
        val center = ActivityCenter(backgroundScope) { currentTime }
        val source = SyncActivitySource(center, EventSource<Boolean>()) { "Syncing" }
        recordErrorNotification({ source }, UserMessage.ErrorNotification(null, 7, true)) { error("no such resource") }
        recordErrorNotification({ error("koin broke") }, UserMessage.ErrorNotification("x", null, true)) { "y" }
        assertTrue(center.items.value.isEmpty())
    }
}
