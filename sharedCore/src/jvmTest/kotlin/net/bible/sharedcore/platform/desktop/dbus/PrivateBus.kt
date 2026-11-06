package net.bible.sharedcore.platform.desktop.dbus

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal

/**
 * A private `dbus-daemon --session` for integration tests; [startOrNull] is null when the daemon is not installed so
 * tests can skip. [close] closes every connection made through it and kills the daemon.
 */
class PrivateBus private constructor(val address: String, private val process: Process) : AutoCloseable {
    private val connections = CopyOnWriteArrayList<DBusConnection>()

    fun newConnection(): DBusConnection =
        DBusConnectionBuilder.forAddress(address).withShared(false).build().also { connections.add(it) }

    /** A [DesktopDbus] (reported as Linux) whose [DesktopDbus.connect] joins this bus. */
    fun desktopDbus(): DesktopDbus = object : DesktopDbus("Linux") {
        override fun connect(): DBusConnection = newConnection()
    }

    override fun close() {
        connections.forEach { runCatching { it.close() } }
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS)
    }

    companion object {
        fun startOrNull(): PrivateBus? {
            val daemon = listOf("/usr/bin/dbus-daemon", "/bin/dbus-daemon").firstOrNull { File(it).canExecute() } ?: return null
            val process = ProcessBuilder(daemon, "--session", "--nofork", "--print-address").redirectErrorStream(false).start()
            val address = try {
                process.inputStream.bufferedReader().readLine()?.trim().orEmpty()
            } catch (e: Exception) { "" }
            if (address.isEmpty()) { process.destroyForcibly(); return null }
            return PrivateBus(address, process)
        }
    }
}

/** A fake D-Bus service: owns [busName] and exports [implementation] at [path] on its own connection. */
class FakeService(bus: PrivateBus, private val busName: String, private val path: String, implementation: DBusInterface) {
    private val connection = bus.newConnection().also {
        it.exportObject(path, implementation)
        it.requestBusName(busName)
    }

    /** Emits a signal of [iface] from [path] with the D-Bus [signature]. */
    fun emit(iface: String, member: String, signature: String, vararg args: Any) {
        connection.sendMessage(DBusSignal(null, path, iface, member, signature, *args))
    }

    /** Releases the name and disconnects: the service is gone. */
    fun stop() {
        runCatching { connection.releaseBusName(busName) }
        runCatching { connection.close() }
    }
}

fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
    val end = System.nanoTime() + timeoutMs * 1_000_000
    while (System.nanoTime() < end) {
        if (condition()) return true
        Thread.sleep(10)
    }
    return condition()
}
