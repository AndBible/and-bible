package net.bible.sharedcore.platform.desktop.dbus

import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus

/**
 * The session bus and the freedesktop portals. They exist on Linux and other Unix desktops only: on Windows (and macOS)
 * [portalAvailable] is false without touching D-Bus, [osName] is `os.name` (injectable for tests).
 */
open class DesktopDbus(private val osName: String = System.getProperty("os.name").orEmpty()) {
    /** Whether this OS can have a D-Bus session bus and portals at all. */
    val supportedOs: Boolean get() = osName.lowercase().let { !it.startsWith("windows") && !it.startsWith("mac") }

    open fun connect(): DBusConnection = DBusConnectionBuilder.forSessionBus().withShared(false).build()

    open fun portalAvailable(): Boolean = if (!supportedOs) false else try {
        connect().use { connection ->
            val bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java)
            bus.NameHasOwner("org.freedesktop.portal.Desktop") ||
                bus.ListActivatableNames().contains("org.freedesktop.portal.Desktop")
        }
    } catch (_: Exception) { false }
}
