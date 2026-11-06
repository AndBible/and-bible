package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.NativeNotification
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeNotifications : FreedesktopNotificationsInterface {
    data class Notify(
        val appName: String, val replacesId: Long, val appIcon: String, val summary: String, val body: String,
        val actions: List<String>, val hints: Map<String, Variant<*>>, val expire: Int,
    )

    val notifies = CopyOnWriteArrayList<Notify>()
    val closed = CopyOnWriteArrayList<Long>()
    private val nextId = AtomicInteger(100)

    override fun Notify(
        appName: String, replacesId: UInt32, appIcon: String, summary: String, body: String,
        actions: List<String>, hints: Map<String, Variant<*>>, expireTimeout: Int,
    ): UInt32 {
        notifies.add(Notify(appName, replacesId.toLong(), appIcon, summary, body, actions, hints, expireTimeout))
        return UInt32(if (replacesId.toLong() != 0L) replacesId.toLong() else nextId.incrementAndGet().toLong())
    }

    override fun CloseNotification(id: UInt32) { closed.add(id.toLong()) }
    override fun GetCapabilities(): List<String> = listOf("body", "actions", "progress")
    override fun getObjectPath(): String = "/org/freedesktop/Notifications"
}

class FreedesktopNotifierTest {
    private var bus: PrivateBus? = null
    private var service: FakeService? = null
    private var fake = FakeNotifications()
    private val scope = CoroutineScope(SupervisorJob())
    private val warnings = CopyOnWriteArrayList<String>()
    private var notifier: FreedesktopNotifier? = null
    private val note = NativeNotification("a", "Title", "Body", progress = null, indeterminate = false, urgent = false)

    @Before
    fun setUp() {
        bus = PrivateBus.startOrNull()
        assumeTrue("dbus-daemon not available", bus != null)
        Log.sinkOverride = { level, tag, msg, _ -> if (level == LogLevel.WARN && tag == "FreedesktopNotifier") warnings.add(msg) }
        startService()
        notifier = FreedesktopNotifier(bus!!.desktopDbus(), scope, appIcon = "test-icon", retryAfterFailureMs = 200)
    }

    @After
    fun tearDown() {
        Log.sinkOverride = null
        runCatching { notifier?.close() }
        scope.cancel()
        bus?.close()
    }

    private fun startService() {
        fake = FakeNotifications()
        service = FakeService(bus!!, "org.freedesktop.Notifications", "/org/freedesktop/Notifications", fake)
    }

    private fun flushed() = assertTrue(notifier!!.flush(), "notifier queue did not drain")

    @Test
    fun notifyMapsFieldsAndHints() {
        notifier!!.show(NativeNotification("job", "Sync", "Working", progress = 42, indeterminate = false, urgent = true))
        flushed()
        val n = fake.notifies.single()
        assertEquals("AndBible", n.appName)
        assertEquals("test-icon", n.appIcon)
        assertEquals("Sync", n.summary)
        assertTrue(n.body.contains("42 %") && n.body.contains("Working"), n.body)
        assertEquals(listOf("default", "Open"), n.actions)
        assertEquals(2.toByte(), n.hints.getValue("urgency").value)
        assertEquals(42, n.hints.getValue("value").value)
        assertEquals(true, n.hints.getValue("transient").value)
        assertEquals(0, n.expire)
        assertEquals(0L, n.replacesId)
    }

    @Test
    fun finalNotificationUsesNormalUrgencyServerDefaultExpiryAndNoValueHint() {
        notifier!!.show(note)
        flushed()
        val n = fake.notifies.single()
        assertEquals(1.toByte(), n.hints.getValue("urgency").value)
        assertFalse("value" in n.hints)
        assertFalse("transient" in n.hints)
        assertEquals(-1, n.expire)
        assertEquals("Body", n.body)
    }

    @Test
    fun supportsProgressOnceCapabilitiesAnswered() {
        assertTrue(waitUntil { notifier!!.supportsProgress })
    }

    @Test
    fun secondShowReusesReplacesId() {
        notifier!!.show(note)
        notifier!!.show(note.copy(body = "Again"))
        flushed()
        val ids = fake.notifies.map { it.replacesId }
        assertEquals(0L, ids[0])
        assertEquals(101L, ids[1])
    }

    @Test
    fun removeCallsCloseNotificationAndToleratesUnknownIds() {
        notifier!!.show(note)
        notifier!!.remove("a")
        notifier!!.remove("a")
        notifier!!.remove("never-shown")
        flushed()
        assertEquals(listOf(101L), fake.closed.toList())
    }

    @Test
    fun actionInvokedReachesHandlerWithOurId() {
        val got = CopyOnWriteArrayList<String>()
        notifier!!.setOnActivated { got.add(it) }
        notifier!!.show(note)
        flushed()
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(101), "default")
        assertTrue(waitUntil { got.isNotEmpty() })
        assertEquals(listOf("a"), got.toList())
        // Other actions and unknown notifications are ignored.
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(101), "other")
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(999), "default")
        flushed()
        Thread.sleep(200)
        assertEquals(1, got.size)
    }

    @Test
    fun notificationClosedForgetsMapping() {
        notifier!!.show(note)
        flushed()
        service!!.emit("org.freedesktop.Notifications", "NotificationClosed", "uu", UInt32(101), UInt32(2))
        // The signal travels on another connection; a round trip through the bus after it orders the two.
        Thread.sleep(300)
        notifier!!.show(note)
        flushed()
        assertEquals(listOf(0L, 0L), fake.notifies.map { it.replacesId })
    }

    @Test
    fun serverGoneMidSessionWarnsOnce() {
        notifier!!.show(note)
        flushed()
        assertTrue(warnings.isEmpty())
        service!!.stop()
        repeat(20) { notifier!!.show(note.copy(body = "n$it")) }
        flushed()
        assertEquals(1, warnings.size, warnings.toString())
        startService()
        Thread.sleep(250) // past the reconnect back-off
        notifier!!.show(note.copy(body = "back"))
        flushed()
        assertEquals("back", fake.notifies.single().body)
        assertEquals(0L, fake.notifies.single().replacesId, "stale server ids are not reused after a reconnect")
        assertEquals(1, warnings.size)
    }

    @Test
    fun closeFlushesQueuedRemovesBeforeDisconnecting() {
        notifier!!.show(note)
        notifier!!.remove("a")
        notifier!!.close()
        assertEquals(listOf(101L), fake.closed.toList())
        notifier!!.show(note) // after close: ignored, no throw
        assertEquals(1, fake.notifies.size)
    }
}
