package net.bible.android.control.event

import net.bible.sharedcore.event.Subscription
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessagesTest {
    private val received = mutableListOf<UserMessage>()
    private val subscriptions = mutableListOf<Subscription>()

    private fun record(into: MutableList<UserMessage> = received) =
        UserMessages.messages.subscribe { into += it }.also { subscriptions += it }

    @After
    fun tearDown() = subscriptions.forEach { it.cancel() }

    @Test
    fun toastByIdIsShortAndDeliveredBeforeTheCallReturns() {
        record()
        UserMessages.toast(42)
        assertEquals(listOf<UserMessage>(UserMessage.Toast(null, 42, long = false)), received)
    }

    @Test
    fun toastByIdCanBeLong() {
        record()
        UserMessages.toast(42, long = true)
        assertEquals(listOf<UserMessage>(UserMessage.Toast(null, 42, long = true)), received)
    }

    @Test
    fun toastByTextIsShort() {
        record()
        UserMessages.toast("hello")
        assertEquals(listOf<UserMessage>(UserMessage.Toast("hello", null, long = false)), received)
    }

    @Test
    fun errorNotificationByIdShowsTheReportButton() {
        record()
        UserMessages.errorNotification(7)
        assertEquals(listOf<UserMessage>(UserMessage.ErrorNotification(null, 7, showReportButton = true)), received)
    }

    @Test
    fun errorNotificationByTextDefaultsToTheReportButtonAndCanOmitIt() {
        record()
        UserMessages.errorNotification("a")
        UserMessages.errorNotification("b", showReportButton = false)
        assertEquals(
            listOf<UserMessage>(
                UserMessage.ErrorNotification("a", null, showReportButton = true),
                UserMessage.ErrorNotification("b", null, showReportButton = false),
            ),
            received,
        )
    }

    @Test
    fun everySubscriberReceivesAMessageAndACancelledOneDoesNot() {
        val second = mutableListOf<UserMessage>()
        val third = mutableListOf<UserMessage>()
        record()
        record(second)
        record(third).cancel()
        UserMessages.toast("x")
        assertEquals(1, received.size)
        assertEquals(1, second.size)
        assertEquals(0, third.size)
    }
}
