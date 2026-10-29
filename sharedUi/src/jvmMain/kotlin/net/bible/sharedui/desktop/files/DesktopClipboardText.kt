package net.bible.sharedui.desktop.files

import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import net.bible.sharedui.app.ports.ClipboardText

class DesktopClipboardText(private val clipboard: () -> Clipboard? = {
    if (GraphicsEnvironment.isHeadless()) null else Toolkit.getDefaultToolkit().systemClipboard
}) : ClipboardText {
    override fun read(): String? = try { clipboard()?.getData(DataFlavor.stringFlavor) as? String } catch (_: Exception) { null }
    override fun write(label: String, text: String) { try { clipboard()?.setContents(StringSelection(text), null) } catch (_: Exception) { } }
}
