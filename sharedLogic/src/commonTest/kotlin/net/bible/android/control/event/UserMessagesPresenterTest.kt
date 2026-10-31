package net.bible.android.control.event

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class UserMessagesPresenterTest {
    @Test
    fun messagesBeforeTheNotifierAndUiExistAreDeliveredInOrderExactlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = EventSource<UserMessage>()
        val presenter = UserMessagesPresenter(backgroundScope, events)
        try {
            // Synchronous registration must precede even the collector coroutine's first turn.
            events.emit(UserMessage.ErrorNotification("incompatible patch version", null, false))
            events.emit(UserMessage.ErrorNotification("WebDAV failed", null, true))
            runCurrent()
            val dialogs = AppDialogController()
            val calls = mutableListOf<String>()
            val notifier = object : UserNotifier {
                override fun showInfo(message: String, long: Boolean) { calls += "info:$message:$long" }
                override fun showError(message: String, cause: Throwable?) {
                    calls += "error:$message"
                    dialogs.post(AppDialogRequest.Message(null, message, "OK"))
                }
            }
            presenter.attach(notifier)
            presenter.attach(notifier)
            events.emit(UserMessage.Toast("synced", null, true))
            runCurrent()
            assertEquals(listOf("error:incompatible patch version", "error:WebDAV failed", "info:synced:true"), calls)
            val first = dialogs.pending.value!!
            assertEquals("incompatible patch version", assertIs<AppDialogRequest.Message>(first.request).message)
            dialogs.respond(first.id, AppDialogResult.Ok)
            val second = dialogs.pending.value!!
            assertEquals("WebDAV failed", assertIs<AppDialogRequest.Message>(second.request).message)
            dialogs.respond(second.id, AppDialogResult.Ok)
            assertNull(dialogs.pending.value)
        } finally {
            presenter.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFailedPresentationAndAndroidResourceIdsDoNotStopLaterMessages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = EventSource<UserMessage>()
        val presenter = UserMessagesPresenter(backgroundScope, events)
        val calls = mutableListOf<String>()
        try {
            presenter.attach(object : UserNotifier {
                override fun showInfo(message: String, long: Boolean) { calls += message }
                override fun showError(message: String, cause: Throwable?) { error("presentation failed") }
            })
            events.emit(UserMessage.ErrorNotification("broken presenter", null, true))
            events.emit(UserMessage.Toast(null, 123, false))
            events.emit(UserMessage.ErrorNotification(null, 456, false))
            events.emit(UserMessage.Toast("still subscribed", null, false))
            runCurrent()
            assertEquals(listOf("still subscribed"), calls)
        } finally {
            presenter.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun closeDropsQueuedMessagesAndUnsubscribes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = EventSource<UserMessage>()
        val presenter = UserMessagesPresenter(backgroundScope, events)
        val calls = mutableListOf<String>()
        try {
            events.emit(UserMessage.Toast("queued", null, false))
            presenter.close()
            presenter.attach(object : UserNotifier {
                override fun showInfo(message: String, long: Boolean) { calls += message }
                override fun showError(message: String, cause: Throwable?) { calls += message }
            })
            events.emit(UserMessage.ErrorNotification("after close", null, true))
            runCurrent()
            assertEquals(emptyList(), calls)
        } finally {
            presenter.close()
            Dispatchers.resetMain()
        }
    }
}
