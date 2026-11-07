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
 * list), not straight to a notifier. The drain runs ops the user started on the cloud documents screen ("Sync now",
 * per-document actions) mixed with automatic ones, so the failures are foreground: they keep their dialog and are recorded.
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

    @Test fun userFacingFailureIsAForegroundSyncErrorNotificationWithoutReportButton() = runTest {
        val s = starter(backgroundScope, CloudSyncUserFacingException("bad password", true))
        s.start(push = listOf("A"), download = emptyList())
        s.awaitIdle()
        assertEquals(listOf(UserMessage.ErrorNotification("bad password", null, showReportButton = false, background = false, kind = ActivityKind.SYNC)), errors)
    }

    @Test fun genericFailureIsAForegroundSyncErrorNotification() = runTest {
        val s = starter(backgroundScope, IllegalStateException("boom"))
        s.start(push = listOf("A"), download = emptyList())
        s.awaitIdle()
        assertEquals(listOf(UserMessage.ErrorNotification("generic", null, showReportButton = true, background = false, kind = ActivityKind.SYNC)), errors)
    }

    /** #4031: a failing drain of ten documents raised ten modal dialogs; it shows one, and the next failing drain none. */
    @Test fun failingDrainShowsOneErrorAndTheNextFailingDrainIsQuiet() = runTest {
        val s = starter(backgroundScope, IllegalStateException("boom"))
        s.start(push = (1..10).map { "M$it" }, download = emptyList())
        s.awaitIdle()
        assertEquals(1, errors.size, "$errors")
        assertEquals(false, errors.single().background)
        errors.clear()
        s.start(push = listOf("A", "B"), download = emptyList())
        s.awaitIdle()
        assertEquals(1, errors.size, "$errors")
        assertEquals(true, errors.single().repeated)
        assertEquals(true, errors.single().background)
    }
}
