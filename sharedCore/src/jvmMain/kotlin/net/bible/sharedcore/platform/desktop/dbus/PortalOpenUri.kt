package net.bible.sharedcore.platform.desktop.dbus

import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.freedesktop.portal.OpenURI")
interface PortalOpenUriInterface : DBusInterface {
    fun OpenURI(parent: String, uri: String, options: Map<String, Variant<*>>): DBusPath
}

class PortalOpenUri(private val dbus: DesktopDbus = DesktopDbus()) {
    suspend fun open(uri: URI): Boolean = withContext(Dispatchers.IO) {
        dbus.connect().use { connection ->
            val token = "andbible_" + UUID.randomUUID().toString().replace("-", "")
            val sender = connection.uniqueName.removePrefix(":").replace('.', '_')
            val path = "/org/freedesktop/portal/desktop/request/$sender/$token"
            awaitPortalRequest<Boolean>(
                expectedPath = path,
                close = { request -> connection.getRemoteObject("org.freedesktop.portal.Desktop", request, PortalRequest::class.java).Close() },
                start = {
                    connection.getRemoteObject("org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", PortalOpenUriInterface::class.java)
                        .OpenURI("", uri.toASCIIString(), mapOf("handle_token" to Variant(token))).path
                },
                register = { complete ->
                    connection.addGenericSigHandler(DBusMatchRule("signal", "org.freedesktop.portal.Request", "Response", path)) { signal ->
                        complete(runCatching { (signal.parameters[0] as Number).toInt() == 0 })
                    }
                },
            )
        }
    }
}
