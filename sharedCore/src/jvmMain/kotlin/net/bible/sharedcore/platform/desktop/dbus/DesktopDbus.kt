package net.bible.sharedcore.platform.desktop.dbus

import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus

open class DesktopDbus {
    open fun connect(): DBusConnection = DBusConnectionBuilder.forSessionBus().withShared(false).build()

    open fun portalAvailable(): Boolean = try {
        connect().use { connection ->
            val bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java)
            bus.NameHasOwner("org.freedesktop.portal.Desktop") ||
                bus.ListActivatableNames().contains("org.freedesktop.portal.Desktop")
        }
    } catch (_: Exception) { false }
}
