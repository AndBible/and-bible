package net.bible.sharedcore.platform.desktop.dbus

import java.net.URI
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.desktop.files.FileChooserBackend
import net.bible.sharedcore.platform.desktop.files.FileFilter
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@DBusInterfaceName("org.freedesktop.portal.FileChooser")
interface PortalChooser : DBusInterface {
    fun OpenFile(parent: String, title: String, options: Map<String, Variant<*>>): DBusPath
    fun SaveFile(parent: String, title: String, options: Map<String, Variant<*>>): DBusPath
}

@DBusInterfaceName("org.freedesktop.portal.Request")
interface PortalRequest : DBusInterface { fun Close() }

class PortalFilterEntry(@Position(0) @JvmField val kind: UInt32, @Position(1) @JvmField val value: String) : Struct()
class PortalFilter(@Position(0) @JvmField val name: String, @Position(1) @JvmField val entries: List<PortalFilterEntry>) : Struct()

class PortalFileChooserBackend(private val dbus: DesktopDbus) : FileChooserBackend {
    override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? =
        request(false, null, filters, multiple, directory)

    override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? =
        request(true, suggestedName, filters, false, false)?.firstOrNull()

    private suspend fun request(save: Boolean, name: String?, filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? =
        withContext(Dispatchers.IO) {
            dbus.connect().use { connection ->
                val token = "andbible_" + UUID.randomUUID().toString().replace("-", "")
                val sender = connection.uniqueName.removePrefix(":").replace('.', '_')
                val path = "/org/freedesktop/portal/desktop/request/$sender/$token"
                val options = mutableMapOf<String, Variant<*>>("handle_token" to Variant(token))
                if (save) options["current_name"] = Variant(name ?: "") else {
                    options["multiple"] = Variant(multiple)
                    options["directory"] = Variant(directory)
                }
                val portalFilters = filters.map { filter -> PortalFilter(filter.name,
                    filter.globs.map { PortalFilterEntry(UInt32(0), it) } +
                        filter.mimeTypes.filterNot { '*' in it }.map { PortalFilterEntry(UInt32(1), it) })
                }.filter { it.entries.isNotEmpty() }
                if (portalFilters.isNotEmpty()) options["filters"] = Variant(portalFilters, "a(sa(us))")
                suspendCancellableCoroutine { continuation ->
                    val handler = connection.addGenericSigHandler(
                        DBusMatchRule("signal", "org.freedesktop.portal.Request", "Response", path)
                    ) { signal ->
                        try {
                            val args = signal.parameters
                            val status = (args[0] as Number).toInt()
                            val results = args[1] as Map<*, *>
                            val uris = (results["uris"] as? Variant<*>)?.value
                            val paths = when (uris) {
                                is Array<*> -> uris.toList()
                                is List<*> -> uris
                                else -> emptyList()
                            }.map { Path.of(URI(it as String)) }
                            if (continuation.isActive) continuation.resume(if (status == 0) paths else null)
                        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                    }
                    continuation.invokeOnCancellation {
                        try { connection.getRemoteObject("org.freedesktop.portal.Desktop", path, PortalRequest::class.java).Close() } catch (_: Exception) { }
                        try { handler.close() } catch (_: Exception) { }
                    }
                    try {
                        val chooser = connection.getRemoteObject("org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", PortalChooser::class.java)
                        val returned = if (save) chooser.SaveFile("", "", options) else chooser.OpenFile("", "", options)
                        check(returned.path == path) { "Unexpected portal request path" }
                    } catch (e: Exception) {
                        try { handler.close() } catch (_: Exception) { }
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        }
}
