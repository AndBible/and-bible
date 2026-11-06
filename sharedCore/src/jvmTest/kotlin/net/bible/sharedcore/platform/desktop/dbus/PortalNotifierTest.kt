package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.platform.NativeNotification
import org.freedesktop.dbus.types.Variant
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakePortalNotification : PortalNotificationInterface {
    val added = CopyOnWriteArrayList<Pair<String, Map<String, Variant<*>>>>()
    val removed = CopyOnWriteArrayList<String>()
    override fun AddNotification(id: String, notification: Map<String, Variant<*>>) { added.add(id to notification) }
    override fun RemoveNotification(id: String) { removed.add(id) }
    override fun getObjectPath(): String = "/org/freedesktop/portal/desktop"
}

class PortalNotifierTest {
    private var bus: PrivateBus? = null
    private val fake = FakePortalNotification()
    private var service: FakeService? = null
    private val scope = CoroutineScope(SupervisorJob())
    private var notifier: PortalNotifier? = null

    @Before
    fun setUp() {
        bus = PrivateBus.startOrNull()
        assumeTrue("dbus-daemon not available", bus != null)
        service = FakeService(bus!!, "org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", fake)
        notifier = PortalNotifier(bus!!.desktopDbus(), scope)
    }

    @After
    fun tearDown() {
        runCatching { notifier?.close() }
        scope.cancel()
        bus?.close()
    }

    @Test
    fun portalAddAndRemoveUseOurId() {
        val n = notifier!!
        n.show(NativeNotification("job-1", "Failed", "Oops", progress = null, indeterminate = false, urgent = true))
        n.show(NativeNotification("job-1", "Failed again", "Oops", progress = null, indeterminate = false, urgent = false))
        n.remove("job-1")
        n.remove("unknown")
        assertTrue(n.flush())
        assertEquals(listOf("job-1", "job-1"), fake.added.map { it.first })
        val first = fake.added[0].second
        assertEquals("Failed", first.getValue("title").value)
        assertEquals("Oops", first.getValue("body").value)
        assertEquals("urgent", first.getValue("priority").value)
        assertEquals("app.activate", first.getValue("default-action").value)
        assertEquals("normal", fake.added[1].second.getValue("priority").value)
        assertEquals(listOf("job-1", "unknown"), fake.removed.toList())
    }

    @Test
    fun portalReportsNoProgress() {
        assertFalse(notifier!!.supportsProgress)
    }

    @Test
    fun actionInvokedSignalReachesHandlerWithOurId() {
        val got = CopyOnWriteArrayList<String>()
        notifier!!.setOnActivated { got.add(it) }
        notifier!!.show(NativeNotification("job-2", "t", "b", null, false, false))
        assertTrue(notifier!!.flush())
        service!!.emit("org.freedesktop.portal.Notification", "ActionInvoked", "ssav", "job-2", "app.activate", emptyList<Variant<*>>())
        assertTrue(waitUntil { got.isNotEmpty() })
        assertEquals(listOf("job-2"), got.toList())
    }
}
