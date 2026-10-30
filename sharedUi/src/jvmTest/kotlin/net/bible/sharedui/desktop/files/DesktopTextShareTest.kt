@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.desktop.files

import net.bible.sharedcore.platform.files.*
import net.bible.sharedcore.ui.dialog.*
import net.bible.sharedui.app.ports.ClipboardText
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Files
import java.nio.file.Path

class DesktopTextShareTest {
    @Test fun saveSelectionDeliversTextWithoutCopying() = runTest {
        var copied = false
        var delivered: String? = null
        val clipboard = object : ClipboardText {
            override fun read(): String? = null
            override fun write(label: String, text: String) { copied = true }
        }
        val delivery = object : FileDelivery {
            override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String,
                saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
                assertEquals(SaveOrShare.SAVE, mode)
                assertEquals("text/plain", saveMimeType)
                delivered = Files.readString(Path.of(file))
                return DeliveryResult.DELIVERED
            }
        }
        val dialogs = AppDialogController()
        DesktopTextShare(clipboard, delivery, { dialogs }, { it }, this).shareText("ä content", "Share")
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected("save"))
        runCurrent()
        assertEquals("ä content", delivered)
        assertFalse(copied)
    }

    @Test fun fileShareIsBusyUntilDeliveryCompletes() = runTest {
        val release = CompletableDeferred<Unit>()
        var count = 0
        val delivery = object : FileDelivery {
            override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String,
                saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
                count++
                assertEquals("page.html", fileName)
                assertEquals(SaveOrShare.SHARE, mode)
                release.await()
                return DeliveryResult.DELIVERED
            }
        }
        val share = DesktopFileShareImpl(delivery, this)
        share.shareFile("/tmp/page.html")
        assertFalse(share.canShare)
        share.shareFile("/tmp/page.html")
        runCurrent()
        assertEquals(1, count)
        release.complete(Unit)
        runCurrent()
        assertTrue(share.canShare)
    }

    @Test fun copyRequiresChoiceAndCopiesOnlyOnSelection() {
        var copied: String? = null
        val clipboard = object : ClipboardText {
            override fun read() = copied
            override fun write(label: String, text: String) { copied = text }
        }
        val delivery = object : FileDelivery {
            override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String,
                saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult = error("unexpected save")
        }
        val dialogs = AppDialogController()
        DesktopTextShare(clipboard, delivery, { dialogs }, { it }).shareText("hello", "Share")
        val shown = assertNotNull(dialogs.pending.value)
        val request = assertIs<AppDialogRequest.SingleChoice>(shown.request)
        assertEquals(listOf("copy", "save"), request.choices.map { it.value })
        assertNull(copied)
        dialogs.respond(shown.id, AppDialogResult.Selected("copy"))
        assertEquals("hello", copied)
    }
}
