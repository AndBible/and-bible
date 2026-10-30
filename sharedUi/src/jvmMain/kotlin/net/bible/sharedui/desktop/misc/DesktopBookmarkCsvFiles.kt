package net.bible.sharedui.desktop.misc

import kotlinx.coroutines.CancellationException
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.desktop.assembly.DesktopBookmarkCsvFiles as BookmarkCsvFilesPort
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import java.net.URI
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DesktopBookmarkCsvFiles : BookmarkCsvFilesPort {
    override fun openSink(ref: DocumentRef): Sink? = try {
        val value = ref.uri
        val windowsDrive = Regex("^[A-Za-z]:[\\\\/].*").matches(value)
        val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").find(value)?.value
        val path = if (windowsDrive || scheme == null) Paths.get(value) else {
            val uri = URI(value)
            require(uri.scheme.equals("file", ignoreCase = true))
            Paths.get(uri)
        }
        FileSystem.SYSTEM.sink(path.toString().toPath())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
}
