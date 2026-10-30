package net.bible.sharedcore.platform.desktop.files

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.PortalFileChooserBackend
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.files.PickedSource
import okio.Source
import okio.source

data class FileFilter(val name: String, val globs: List<String>, val mimeTypes: List<String>)

interface FileChooserBackend {
    suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>?
    suspend fun save(suggestedName: String, filters: List<FileFilter>): Path?
}

fun desktopFileChooser(dbus: DesktopDbus): FileChooserBackend =
    if (try { dbus.portalAvailable() } catch (_: LinkageError) { false }) PortalFileChooserBackend(dbus) else AwtFileChooserBackend()

fun fileFiltersFor(mimeTypes: List<String>): List<FileFilter> = mimeTypes.distinct().mapNotNull { mime ->
    val globs = when (mime.lowercase()) {
        "*/*", "application/*" -> return@mapNotNull null
        "application/zip" -> listOf("*.zip", "*.abk")
        "application/epub+zip" -> listOf("*.epub")
        "text/csv", "text/comma-separated-values" -> listOf("*.csv")
        "text/plain" -> listOf("*.txt")
        "image/*" -> listOf("*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp")
        "image/png" -> listOf("*.png")
        "image/jpeg" -> listOf("*.jpg", "*.jpeg")
        else -> emptyList()
    }
    FileFilter(mime, globs, listOf(mime))
}

class DesktopFilePicker(private val chooser: FileChooserBackend) : FilePicker {
    override suspend fun pickBackupFile(mime: String): PickedSource? = try {
        chooser.open(fileFiltersFor(listOf(mime)), false, false)?.firstOrNull()?.let {
            PickedSource(it.toString(), it.fileName?.toString(), mime)
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    override suspend fun openInput(source: PickedSource): Source {
        val path = Path.of(source.token)
        require(path.isAbsolute) { "Invalid picked file path" }
        return Files.newInputStream(path).source()
    }
}
