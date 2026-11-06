package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.platform.NativeNotification
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

@JvmSuppressWildcards
@DBusInterfaceName("org.freedesktop.Notifications")
interface FreedesktopNotificationsInterface : DBusInterface {
    fun Notify(
        appName: String, replacesId: UInt32, appIcon: String, summary: String, body: String,
        actions: List<String>, hints: Map<String, Variant<*>>, expireTimeout: Int,
    ): UInt32

    fun CloseNotification(id: UInt32)
    fun GetCapabilities(): List<String>
}

/**
 * Notifications through the freedesktop `org.freedesktop.Notifications` service (outside Flatpak, #4012).
 *
 * Our notification ids are mapped to the server's ids so a later [show] replaces in place. [supportsProgress] is true
 * from the moment `GetCapabilities` has answered; that probe runs on the IO dispatcher right after construction (which
 * also opens the connection). It is false before the first answer, and goes false again while the notification service
 * has no owner (it left the bus) until the probe answers anew after it returns.
 *
 * A progress popup the user dismissed (close reason 2) is muted: further progress shows for that id are ignored until a
 * non-progress show (final or failed) or [remove] for it.
 *
 * The service's owner is tracked through `NameOwnerChanged`: on any owner change the server-side ids are forgotten (a
 * restarted daemon knows none of them), capabilities are probed again, and signals from any other sender than the
 * current owner are ignored.
 */
class FreedesktopNotifier(
    dbus: DesktopDbus,
    scope: CoroutineScope,
    private val appIcon: String = "org.andbible.AndBible",
    retryAfterFailureMs: Long = DEFAULT_RETRY_AFTER_FAILURE_MS,
) : DbusNotifierBase(dbus, scope, "FreedesktopNotifier", retryAfterFailureMs) {
    private val serverIds = ConcurrentHashMap<String, UInt32>()
    private val progressKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mutedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val notifyInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val ownerChangeCount = AtomicInteger()

    @Volatile private var owner: String? = null
    @Volatile private var capabilitiesAnswered = false

    override val supportsProgress: Boolean get() = capabilitiesAnswered

    /** Test hook: number of `NameOwnerChanged` events for the service handled so far. */
    internal val ownerChanges: Int get() = ownerChangeCount.get()

    /** Test hook: whether a server id is currently mapped for [id]. */
    internal fun hasServerId(id: String): Boolean = serverIds.containsKey(id)

    init { submit { } }

    private fun fromOwner(signal: DBusSignal): Boolean = owner.let { it == null || it == signal.source }

    override fun onConnected(connection: DBusConnection) {
        owner = runCatching {
            connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java).GetNameOwner(SERVICE)
        }.getOrNull()
        listenTo(connection, "org.freedesktop.DBus", "NameOwnerChanged", "/org/freedesktop/DBus", SERVICE) { signal ->
            if (signal.parameters[0] == SERVICE) {
                val newOwner = (signal.parameters[2] as String).ifEmpty { null }
                owner = newOwner
                serverIds.clear()
                mutedKeys.clear()
                capabilitiesAnswered = false
                if (newOwner != null) submit { c -> proxy(c).GetCapabilities(); capabilitiesAnswered = true }
                ownerChangeCount.incrementAndGet()
            }
        }
        listenTo(connection, INTERFACE, "ActionInvoked") { signal ->
            val serverId = (signal.parameters[0] as Number).toLong()
            if (fromOwner(signal) && signal.parameters[1] == "default") {
                serverIds.entries.firstOrNull { it.value.toLong() == serverId }?.let { activated(it.key) }
            }
        }
        listenTo(connection, INTERFACE, "NotificationClosed") { signal ->
            val serverId = (signal.parameters[0] as Number).toLong()
            // A server that echoes replaces_id may close the old notification while our Notify for it is in flight.
            val reason = (signal.parameters[1] as Number).toLong()
            if (fromOwner(signal)) {
                serverIds.entries.filter { it.value.toLong() == serverId && it.key !in notifyInFlight }.forEach { (key, _) ->
                    serverIds.remove(key)
                    // The user dismissed a progress popup: do not bring it back on the next tick.
                    if (reason == REASON_DISMISSED && key in progressKeys) mutedKeys.add(key)
                }
            }
        }
        proxy(connection).GetCapabilities()
        capabilitiesAnswered = true
    }

    override fun onDisconnected() {
        serverIds.clear()
        mutedKeys.clear()
        capabilitiesAnswered = false
        owner = null
    }

    private fun proxy(connection: DBusConnection) =
        connection.getRemoteObject(SERVICE, PATH, FreedesktopNotificationsInterface::class.java)

    override fun show(n: NativeNotification) = submit(n.id) { connection ->
        val isProgress = n.progress != null || n.indeterminate
        if (isProgress) {
            if (n.id in mutedKeys) return@submit
            progressKeys.add(n.id)
        } else {
            progressKeys.remove(n.id)
            mutedKeys.remove(n.id)
        }
        val percent = n.progress?.coerceIn(0, 100)
        val hints = buildMap<String, Variant<*>> {
            put("urgency", Variant(if (n.urgent) 2.toByte() else 1.toByte()))
            if (percent != null) put("value", Variant(percent))
            if (percent != null || n.indeterminate) put("transient", Variant(true))
        }
        // Servers without progress bars still show the percentage in the body; the caller's body must not repeat it.
        val body = listOfNotNull(n.body.ifBlank { null }, percent?.let { "$it %" }).joinToString("\n")
        val expire = if (n.progress != null || n.indeterminate) 120_000 else -1
        notifyInFlight.add(n.id)
        try {
            val serverId = proxy(connection).Notify(
                "AndBible", serverIds[n.id] ?: UInt32(0), appIcon, n.title, body, listOf("default", "Open"), hints, expire,
            )
            serverIds[n.id] = serverId
        } finally {
            notifyInFlight.remove(n.id)
        }
    }

    override fun remove(id: String) = submit(id) { connection ->
        progressKeys.remove(id)
        mutedKeys.remove(id)
        serverIds[id]?.let { serverId ->
            proxy(connection).CloseNotification(serverId)
            serverIds.remove(id)
        }
    }

    private companion object {
        const val REASON_DISMISSED = 2L
        const val SERVICE = "org.freedesktop.Notifications"
        const val PATH = "/org/freedesktop/Notifications"
        const val INTERFACE = "org.freedesktop.Notifications"
    }
}
