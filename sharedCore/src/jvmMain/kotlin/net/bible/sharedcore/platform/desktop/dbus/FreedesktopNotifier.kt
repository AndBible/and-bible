package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.platform.NativeNotification
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.interfaces.DBusInterface
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
 * Our notification ids are mapped to the server's ids so a later [show] replaces in place. [supportsProgress] becomes
 * true when `GetCapabilities` answers; that probe runs on the IO dispatcher right after construction (which also opens
 * the connection), so it is normally known before the first running item could be shown. Until then it is false.
 */
class FreedesktopNotifier(
    dbus: DesktopDbus,
    scope: CoroutineScope,
    private val appIcon: String = "org.andbible.AndBible",
    retryAfterFailureMs: Long = DEFAULT_RETRY_AFTER_FAILURE_MS,
) : DbusNotifierBase(dbus, scope, "FreedesktopNotifier", retryAfterFailureMs) {
    private val serverIds = ConcurrentHashMap<String, UInt32>()

    @Volatile private var capabilitiesAnswered = false

    override val supportsProgress: Boolean get() = capabilitiesAnswered

    init { submit { } }

    override fun onConnected(connection: DBusConnection) {
        listenTo(connection, INTERFACE, "ActionInvoked") { signal ->
            val serverId = (signal.parameters[0] as Number).toLong()
            if (signal.parameters[1] == "default") serverIds.entries.firstOrNull { it.value.toLong() == serverId }?.let { activated(it.key) }
        }
        listenTo(connection, INTERFACE, "NotificationClosed") { signal ->
            val serverId = (signal.parameters[0] as Number).toLong()
            serverIds.entries.removeIf { it.value.toLong() == serverId }
        }
        proxy(connection).GetCapabilities()
        capabilitiesAnswered = true
    }

    override fun onDisconnected() { serverIds.clear() }

    private fun proxy(connection: DBusConnection) =
        connection.getRemoteObject(SERVICE, PATH, FreedesktopNotificationsInterface::class.java)

    override fun show(n: NativeNotification) = submit { connection ->
        val percent = n.progress?.coerceIn(0, 100)
        val hints = buildMap<String, Variant<*>> {
            put("urgency", Variant(if (n.urgent) 2.toByte() else 1.toByte()))
            if (percent != null) put("value", Variant(percent))
            if (percent != null || n.indeterminate) put("transient", Variant(true))
        }
        // Servers without progress bars still show the percentage in the body.
        val body = listOfNotNull(n.body.ifBlank { null }, percent?.let { "$it %" }).joinToString("\n")
        val expire = if (n.progress != null || n.indeterminate) 0 else -1
        val serverId = proxy(connection).Notify(
            "AndBible", serverIds[n.id] ?: UInt32(0), appIcon, n.title, body, listOf("default", "Open"), hints, expire,
        )
        serverIds[n.id] = serverId
    }

    override fun remove(id: String) = submit { connection ->
        serverIds.remove(id)?.let { proxy(connection).CloseNotification(it) }
    }

    private companion object {
        const val SERVICE = "org.freedesktop.Notifications"
        const val PATH = "/org/freedesktop/Notifications"
        const val INTERFACE = "org.freedesktop.Notifications"
    }
}
