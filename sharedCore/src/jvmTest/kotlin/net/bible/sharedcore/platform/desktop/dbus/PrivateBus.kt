package net.bible.sharedcore.platform.desktop.dbus

import java.io.File
import java.nio.file.Files
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
class PrivateBus private constructor(private val daemon: String, private val dir: File) : AutoCloseable {
    private val connections = CopyOnWriteArrayList<DBusConnection>()
    private val socket = File(dir, "bus")

    /**
     * A minimal config with no service directories: the stock session.conf would make the real notification service
     * and portals activatable on this bus, so a test's Notify could spawn a real daemon (popups, flaky tests).
     */
    private val configFile = File(dir, "bus.conf")
    private var process: Process? = null

    /** The bus address; stable across [restart]. */
    val address: String = "unix:path=${socket.absolutePath}"

    private fun launch(): Boolean {
        socket.delete()
        configFile.writeText(
            """<!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
 "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
<busconfig>
  <type>session</type>
  <listen>$address</listen>
  <auth>EXTERNAL</auth>
  <policy context="default">
    <allow send_destination="*" eavesdrop="true"/>
    <allow eavesdrop="true"/>
    <allow own="*"/>
  </policy>
</busconfig>
""",
        )
        val p = ProcessBuilder(daemon, "--config-file=${configFile.absolutePath}", "--nofork", "--print-address")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process = p
        val line = try { p.inputStream.bufferedReader().readLine()?.trim().orEmpty() } catch (e: Exception) { "" }
        if (line.isEmpty()) { p.destroyForcibly(); process = null }
        return line.isNotEmpty()
    }

    fun newConnection(): DBusConnection =
        DBusConnectionBuilder.forAddress(address).withShared(false).build().also { connections.add(it) }

    /** A [DesktopDbus] (reported as Linux) whose [DesktopDbus.connect] joins this bus. */
    fun desktopDbus(): DesktopDbus = object : DesktopDbus("Linux") {
        override fun connect(): DBusConnection = newConnection()
    }

    /** Kills the daemon, which breaks every connection made so far. */
    fun kill() {
        process?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
        process = null
    }

    /** Starts a new daemon on the same address after [kill]. */
    fun restart() { check(launch()) { "dbus-daemon did not restart" } }

    override fun close() {
        connections.forEach { runCatching { it.close() } }
        kill()
        dir.deleteRecursively()
    }

    companion object {
        fun startOrNull(): PrivateBus? {
            val daemon = listOf("/usr/bin/dbus-daemon", "/bin/dbus-daemon").firstOrNull { File(it).canExecute() } ?: return null
            val bus = PrivateBus(daemon, Files.createTempDirectory("andbible-bus").toFile())
            if (!bus.launch()) { bus.dir.deleteRecursively(); return null }
            return bus
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
