package net.bible.sharedui.desktop.files

import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.desktop.assembly.DesktopFileShare
import org.koin.mp.KoinPlatform
import java.util.concurrent.atomic.AtomicBoolean

class DesktopTextShare(private val clipboard: ClipboardText, private val delivery: FileDelivery,
    private val dialogs: () -> AppDialogController = { KoinPlatform.getKoin().get() },
    private val text: (String) -> String = { KoinPlatform.getKoin().get<HostStringTableHolder>().live.text(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TextShare {
    override fun shareText(text: String, chooserTitle: String) {
        dialogs().post(AppDialogRequest.SingleChoice(chooserTitle, listOf(
            SettingsItem.Choice("copy", this.text("copy")), SettingsItem.Choice("save", this.text("save_and_exit")),
        ), null)) { result ->
            when ((result as? AppDialogResult.Selected)?.value) {
                "copy" -> clipboard.write(chooserTitle, text)
                "save" -> scope.launch {
                    val file = Files.createTempFile("andbible-text-", ".txt")
                    try {
                        Files.writeString(file, text)
                        delivery.deliver(file.toString(), "text.txt", SaveOrShare.SAVE, "text/plain", "text/plain", null, null, chooserTitle)
                    } finally { Files.deleteIfExists(file) }
                }
            }
        }
    }
}

class DesktopFileShareImpl(private val delivery: FileDelivery,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DesktopFileShare {
    private val busy = AtomicBoolean(false)
    override val canShare: Boolean get() = !busy.get()
    override fun shareFile(path: String) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try { delivery.deliver(path, java.nio.file.Path.of(path).fileName.toString(), SaveOrShare.SHARE,
                "text/html", "text/html", null, null, "") } finally { busy.set(false) }
        }
    }
}
