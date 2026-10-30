package net.bible.sharedui.desktop.files

import kotlinx.coroutines.CancellationException
import net.bible.sharedcore.platform.desktop.files.FileChooserBackend
import net.bible.sharedcore.platform.desktop.files.fileFiltersFor
import net.bible.sharedui.app.ports.ImagePicker

class DesktopImagePicker(private val chooser: FileChooserBackend) : ImagePicker {
    override suspend fun pickImage(): String? = try {
        chooser.open(fileFiltersFor(listOf("image/*")), false, false)?.firstOrNull()?.toUri()?.toString()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
