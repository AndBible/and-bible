package net.bible.sharedcore.platform.desktop.files

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.nio.file.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AwtFileChooserBackend : FileChooserBackend {
    override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? =
        dialog(false, null, filters, multiple, directory)

    override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? =
        dialog(true, suggestedName, filters, false, false)?.firstOrNull()

    private suspend fun dialog(save: Boolean, name: String?, filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? {
        if (GraphicsEnvironment.isHeadless()) return null
        return suspendCancellableCoroutine { continuation ->
            var dialog: FileDialog? = null
            var directoryDialog: javax.swing.JDialog? = null
            continuation.invokeOnCancellation { EventQueue.invokeLater { dialog?.dispose(); directoryDialog?.dispose() } }
            EventQueue.invokeLater {
                if (!continuation.isActive) return@invokeLater
                val owner = Frame()
                try {
                    dialog = FileDialog(owner, "", if (save) FileDialog.SAVE else FileDialog.LOAD).apply {
                        isMultipleMode = multiple
                        file = name
                        val patterns = filters.flatMap { it.globs }.map { java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$it") }
                        if (patterns.isNotEmpty()) setFilenameFilter { _, file -> patterns.any { it.matches(Path.of(file)) } }
                    }
                    // AWT has no portable directory-selection mode; Swing supplies it instead.
                    val result = if (directory) {
                        val picker = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
                        var accepted = false
                        directoryDialog = javax.swing.JDialog(owner, true).apply {
                            contentPane.add(picker)
                            pack()
                            setLocationRelativeTo(owner)
                        }
                        picker.addActionListener { event ->
                            accepted = event.actionCommand == javax.swing.JFileChooser.APPROVE_SELECTION
                            directoryDialog?.dispose()
                        }
                        directoryDialog!!.isVisible = true
                        if (accepted) listOf(picker.selectedFile.toPath()) else null
                    } else {
                        dialog!!.isVisible = true
                        dialog!!.files.map { it.toPath() }.takeIf { it.isNotEmpty() }
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Throwable) {
                    if (continuation.isActive) continuation.resume(null)
                } finally { dialog?.dispose(); owner.dispose() }
            }
        }
    }
}
