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

class FakeNotifications(firstId: Int = 100) : FreedesktopNotificationsInterface {
    data class Notify(
        val appName: String, val replacesId: Long, val appIcon: String, val summary: String, val body: String,
        val actions: List<String>, val hints: Map<String, Variant<*>>, val expire: Int,
    )

    val notifies = CopyOnWriteArrayList<Notify>()
    val closed = CopyOnWriteArrayList<Long>()
    private val nextId = AtomicInteger(firstId)

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

    private fun startService(firstId: Int = 100) {
        fake = FakeNotifications(firstId)
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
        // Other actions and unknown notifications are ignored; signals of one sender arrive in order, so once the
        // later valid one has been handled the ignored ones were too.
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(101), "other")
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(999), "default")
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(101), "default")
        assertTrue(waitUntil { got.size >= 2 })
        assertEquals(listOf("a", "a"), got.toList())
    }

    @Test
    fun progressBodyShowsThePercentOnlyOnce() {
        notifier!!.show(NativeNotification("job", "Sync", "", progress = 74, indeterminate = false, urgent = false))
        flushed()
        assertEquals("74 %", fake.notifies.single().body)
    }

    @Test
    fun userDismissedProgressStaysGoneUntilFinalShowOrRemove() {
        val p = NativeNotification("job", "Dl", "", progress = 10, indeterminate = false, urgent = false)
        notifier!!.show(p)
        flushed()
        service!!.emit("org.freedesktop.Notifications", "NotificationClosed", "uu", UInt32(101), UInt32(2))
        assertTrue(waitUntil { !notifier!!.hasServerId("job") })
        notifier!!.show(p.copy(progress = 20))
        notifier!!.show(p.copy(progress = 30))
        flushed()
        assertEquals(1, fake.notifies.size)
        notifier!!.show(p.copy(progress = null, body = "Finished"))
        flushed()
        assertEquals(2, fake.notifies.size)
        assertEquals("Finished", fake.notifies.last().body)
        // remove() also un-mutes
        notifier!!.show(p.copy(id = "other"))
        flushed()
        val serverId = 100L + fake.notifies.size
        service!!.emit("org.freedesktop.Notifications", "NotificationClosed", "uu", UInt32(serverId), UInt32(2))
        assertTrue(waitUntil { !notifier!!.hasServerId("other") })
        notifier!!.remove("other")
        notifier!!.show(p.copy(id = "other", progress = 50))
        flushed()
        assertEquals(4, fake.notifies.size)
    }

    @Test
    fun expiredOrClosedByCallProgressIsNotMuted() {
        val p = NativeNotification("job", "Dl", "", progress = 10, indeterminate = false, urgent = false)
        notifier!!.show(p)
        flushed()
        service!!.emit("org.freedesktop.Notifications", "NotificationClosed", "uu", UInt32(101), UInt32(1))
        assertTrue(waitUntil { !notifier!!.hasServerId("job") })
        notifier!!.show(p.copy(progress = 20))
        flushed()
        assertEquals(2, fake.notifies.size)
    }

    @Test
    fun privateBusHasNoActivatableNotificationOrPortalServices() {
        val names = bus!!.newConnection().getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", org.freedesktop.dbus.interfaces.DBus::class.java)
            .ListActivatableNames().toList()
        assertTrue(names.none { it.contains("Notifications") || it.contains("portal") }, names.toString())
    }

    @Test
    fun notificationClosedForgetsMapping() {
        notifier!!.show(note)
        flushed()
        service!!.emit("org.freedesktop.Notifications", "NotificationClosed", "uu", UInt32(101), UInt32(2))
        assertTrue(waitUntil { !notifier!!.hasServerId("a") })
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
        // The last show (n19) is replayed after the back-off without any further show call.
        assertTrue(waitUntil { fake.notifies.isNotEmpty() })
        assertEquals("n19", fake.notifies.single().body)
        assertEquals(0L, fake.notifies.single().replacesId, "stale server ids are not reused after a reconnect")
        assertEquals(1, warnings.size)
    }

    @Test
    fun finalShowDuringOutageArrivesAfterServiceReturnsWithoutFurtherCalls() {
        notifier!!.show(note.copy(body = "progress", progress = 10))
        flushed()
        service!!.stop()
        notifier!!.show(note.copy(title = "Failed", body = "boom", urgent = true))
        flushed() // the failed attempt has run and is parked
        assertTrue(fake.notifies.size == 1)
        startService()
        assertTrue(waitUntil { fake.notifies.isNotEmpty() })
        val n = fake.notifies.single()
        assertEquals("Failed", n.summary)
        assertEquals(2.toByte(), n.hints.getValue("urgency").value)
        assertEquals(1, warnings.size)
    }

    @Test
    fun brokenBusConnectionRecoversAndReplaysAfterDaemonRestart() {
        notifier!!.show(note)
        flushed()
        bus!!.kill()
        notifier!!.show(note.copy(body = "final"))
        flushed()
        assertTrue(warnings.size <= 1)
        bus!!.restart()
        startService()
        assertTrue(waitUntil(8_000) { fake.notifies.isNotEmpty() })
        assertEquals("final", fake.notifies.single().body)
        assertEquals(0L, fake.notifies.single().replacesId)
        assertEquals(1, warnings.size)
    }

    @Test
    fun ownerChangeWithoutFailingCallForgetsServerIdsAndIgnoresStaleActions() {
        val got = CopyOnWriteArrayList<String>()
        notifier!!.setOnActivated { got.add(it) }
        notifier!!.show(note)
        flushed()
        assertTrue(waitUntil { notifier!!.supportsProgress })
        service!!.stop()
        startService(firstId = 200)
        assertTrue(waitUntil { notifier!!.ownerChanges >= 2 && notifier!!.supportsProgress })
        notifier!!.show(note.copy(body = "b"))
        flushed()
        assertEquals(0L, fake.notifies.single().replacesId)
        // An action for the old notification's server id from the new owner must not fire; the later valid one does.
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(101), "default")
        service!!.emit("org.freedesktop.Notifications", "ActionInvoked", "us", UInt32(201), "default")
        assertTrue(waitUntil { got.isNotEmpty() })
        assertEquals(listOf("a"), got.toList())
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
