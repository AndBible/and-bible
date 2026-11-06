package net.bible.service.cloudsync.host

import kotlinx.coroutines.test.runTest
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.documents.DocumentSyncActions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Document-sync failures on desktop/iOS must go through [UserMessages] (so the presenters record them in the activity
 * list and may reroute them), not straight to a notifier. The drain is never user-initiated, so they are background.
 */
class HostDocumentSyncStarterErrorRoutingTest {
    private val errors = mutableListOf<UserMessage.ErrorNotification>()
    private val subscription = UserMessages.messages.subscribe { if (it is UserMessage.ErrorNotification) errors += it }

    @AfterTest fun unsubscribe() = subscription.cancel()

    private fun starter(scope: kotlinx.coroutines.CoroutineScope, failure: Throwable) = HostDocumentSyncStarter.forDriver(
        scope,
        object : DocumentSyncDriver {
            override val actions = object : DocumentSyncActions {
                override suspend fun push(initials: String) = throw failure
                override suspend fun download(initials: String, onProgress: (Long, Long) -> Unit) = throw failure
                override suspend fun remove(initials: String) = throw failure
                override suspend fun purge(initials: String) = throw failure
                override suspend fun uninstall(initials: String) = throw failure
            }
            override fun notifyRunning(running: Boolean) {}
            override suspend fun refreshCache() {}
            override fun dropAdapterForReconnect() {}
        },
        { "generic" }, begin = { 1uL }, end = {},
    )

    @Test fun userFacingFailureIsABackgroundSyncErrorNotificationWithoutReportButton() = runTest {
        val s = starter(backgroundScope, CloudSyncUserFacingException("bad password", true))
        s.start(push = listOf("A"), download = emptyList())
        s.awaitIdle()
        assertEquals(listOf(UserMessage.ErrorNotification("bad password", null, showReportButton = false, background = true, kind = ActivityKind.SYNC)), errors)
    }

    @Test fun genericFailureIsABackgroundSyncErrorNotification() = runTest {
        val s = starter(backgroundScope, IllegalStateException("boom"))
        s.start(push = listOf("A"), download = emptyList())
        s.awaitIdle()
        assertEquals(listOf(UserMessage.ErrorNotification("generic", null, showReportButton = true, background = true, kind = ActivityKind.SYNC)), errors)
    }
}
