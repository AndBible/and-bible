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
        val path = if (ref.uri.startsWith("/")) Paths.get(ref.uri) else {
            val uri = URI(ref.uri)
            require(uri.scheme == "file")
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
