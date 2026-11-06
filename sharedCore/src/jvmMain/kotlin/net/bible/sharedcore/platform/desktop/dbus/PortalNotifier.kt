package net.bible.sharedcore.platform.desktop.dbus

import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.platform.NativeNotification
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

@JvmSuppressWildcards
@DBusInterfaceName("org.freedesktop.portal.Notification")
interface PortalNotificationInterface : DBusInterface {
    fun AddNotification(id: String, notification: Map<String, Variant<*>>)
    fun RemoveNotification(id: String)
}

/**
 * Notifications through `org.freedesktop.portal.Notification`, used inside Flatpak (#4012). The portal takes our id
 * directly (the same id replaces), so no id mapping is kept. It has no progress bar: [supportsProgress] is false.
 */
class PortalNotifier(
    dbus: DesktopDbus,
    scope: CoroutineScope,
    retryAfterFailureMs: Long = DEFAULT_RETRY_AFTER_FAILURE_MS,
) : DbusNotifierBase(dbus, scope, "PortalNotifier", retryAfterFailureMs) {
    override val supportsProgress: Boolean get() = false

    override fun onConnected(connection: DBusConnection) {
        listenTo(connection, INTERFACE, "ActionInvoked") { signal -> activated(signal.parameters[0] as String) }
    }

    private fun proxy(connection: DBusConnection) =
        connection.getRemoteObject(SERVICE, PATH, PortalNotificationInterface::class.java)

    override fun show(n: NativeNotification) = submit(n.id) { connection ->
        proxy(connection).AddNotification(
            n.id,
            mapOf(
                "title" to Variant(n.title),
                "body" to Variant(n.body),
                "priority" to Variant(if (n.urgent) "urgent" else "normal"),
                "default-action" to Variant("app.activate"),
            ),
        )
    }

    override fun remove(id: String) = submit(id) { connection -> proxy(connection).RemoveNotification(id) }

    private companion object {
        const val SERVICE = "org.freedesktop.portal.Desktop"
        const val PATH = "/org/freedesktop/portal/desktop"
        const val INTERFACE = "org.freedesktop.portal.Notification"
    }
}
