package net.bible.sharedui.app.ports

import java.lang.reflect.Proxy
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookMetaData
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DocumentDialogsCoreTest {
    private val table = StringTable("en", { it }, { key, args -> key + args.joinToString() }, { n, _ -> n }, { emptyList() })
    private val dialogs = AppDialogController()
    private fun core() = DocumentDialogsCore(dialogs, { table })
    private fun book(attempt: (String) -> Boolean = { false }): Book {
        val metadata = Proxy.newProxyInstance(BookMetaData::class.java.classLoader, arrayOf(BookMetaData::class.java)) { _, m, args ->
            when (m.name) {
                "getProperty" -> mapOf("About" to "Description\\parSecond", "Version" to "1.2", "ShortPromo" to "Promo", "UnlockInfo" to "Key instructions")[args!![0]]
                "getValues" -> if (args!![0] == "History") listOf("first", "second") else null
                else -> error(m.name)
            }
        } as BookMetaData
        return Proxy.newProxyInstance(Book::class.java.classLoader, arrayOf(Book::class.java)) { _, m, args ->
            when (m.name) {
                "getName" -> "Test Bible"
                "getInitials" -> "TEST"
                "getBookMetaData" -> metadata
                "getUnlockKey" -> ""
                "unlock" -> attempt(args!![0] as String)
                else -> error(m.name)
            }
        } as Book
    }
    @Test fun aboutHtmlContainsMetadata() {
        val html = core().aboutHtml(book(), installed = null)
        assertContains(html, "<b>Test Bible</b>")
        assertContains(html, "Description<br>Second")
        assertContains(html, "second<br>first")
        assertContains(html, "Key instructions")
    }
    @Test fun unlockLoopsUntilCorrectKeyAndStoresIt() = runTest {
        val attempts = mutableListOf<String>()
        var stored: String? = null
        val task = async { core().unlockDocument(book { attempts += it; it == "correct" }) { stored = it } }
        runCurrent()
        fun answer(value: AppDialogResult) = dialogs.respond(dialogs.pending.value!!.id, value)
        answer(AppDialogResult.Text("wrong")); runCurrent()
        assertIs<AppDialogRequest.Confirm>(dialogs.pending.value!!.request)
        answer(AppDialogResult.Ok); runCurrent()
        answer(AppDialogResult.Text("correct")); runCurrent()
        assertTrue(task.await()); assertEquals(listOf("wrong", "correct"), attempts); assertEquals("correct", stored)
    }
    @Test fun cancellingCoroutineWithdrawsUnlockAndNeverPersists() = runTest {
        var stored = false
        val task = async { core().unlockDocument(book()) { stored = true } }
        runCurrent()
        assertIs<AppDialogRequest.TextInput>(dialogs.pending.value!!.request)
        task.cancel(); runCurrent()
        assertTrue(task.isCancelled)
        assertNull(dialogs.pending.value)
        assertFalse(stored)
    }
    @Test fun upgradeRetainsWarningOrderAndButtons() = runTest {
        val task = async { core().confirmUpgrade() }
        runCurrent()
        val shown = dialogs.pending.value!!
        val request = shown.request as AppDialogRequest.Confirm
        assertEquals("bookmark_warning2<br><br>bookmark_warning4<br><br>bookmark_warning3", request.message)
        assertEquals("cancel", request.dismissText)
        assertFalse(request.cancellable)
        dialogs.respond(shown.id, AppDialogResult.Ok)
        assertTrue(task.await())
    }
    @Test fun helpNoticePreservesBlocksAndLinks() {
        val request = core().helpNotice(listOf(DocumentDialogsCore.HelpItem("Title", "first\nsecond", "https://video", "https://manual")), "Version 1")
        assertEquals(4, request.blocks.size)
        val html = (request.blocks[0] as AppDialogRequest.NoticeBlock.Html).html
        assertContains(html, "first<br>second")
        assertContains(html, "href=\"https://manual\"")
        assertContains(html, "href=\"https://video\"")
        assertIs<AppDialogRequest.NoticeBlock.IconLine>(request.blocks[2])
        // The sponsor link is the one shared constant, and its value is the shop Android opens.
        assertEquals("https://shop.andbible.org", net.bible.sharedcore.docs.SiteLinks.SHOP_URL)
        assertEquals("SourceRepository", net.bible.service.download.DownloadManager.REPOSITORY_KEY)
        assertEquals("<i>Version 1</i>", (request.blocks[3] as AppDialogRequest.NoticeBlock.Html).html)
    }
    @Test fun cancelUnlockLeavesTheBookLocked() = runTest {
        var stored = false
        val task = async { core().unlockDocument(book()) { stored = true } }
        runCurrent()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel); runCurrent()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel); runCurrent()
        assertFalse(task.await()); assertFalse(stored)
    }
    @Test fun failedTextRefreshKeepsLastValidUnlockRequest() = runTest {
        var broken = false
        val table = StringTable.of("en", { if (broken) error("Unavailable resource") else it }, { n, _ -> n }, { emptyList() })
        val core = DocumentDialogsCore(dialogs, { table }, live = true)
        val task = async { core.unlockDocument(book()) {} }
        runCurrent()
        val request = dialogs.pending.value!!.request as AppDialogRequest.Localized
        broken = true
        assertEquals("okay", (request.resolve() as AppDialogRequest.TextInput).confirmText)
        task.cancel(); runCurrent()
    }
    @Test fun localizedUnlockChangesLanguageWithoutLosingDraft() = runTest {
        var language = "en"
        val liveTable = { StringTable.of(language, { "$language:$it" }, { n, _ -> n }, { emptyList() }) }
        val core = DocumentDialogsCore(dialogs, liveTable, live = true)
        val task = async { core.unlockDocument(book()) {} }
        runCurrent()
        val shown = dialogs.pending.value!!
        val request = shown.request as AppDialogRequest.Localized
        assertEquals("en:okay", (request.resolve() as AppDialogRequest.TextInput).confirmText)
        dialogs.saveDraft(shown.id, "half typed key")
        language = "fi"
        assertEquals("fi:okay", (request.resolve() as AppDialogRequest.TextInput).confirmText)
        assertEquals("half typed key", dialogs.draft(shown.id))
        task.cancel(); runCurrent()
        assertNull(dialogs.pending.value)
    }
    @Test fun helpDialogHasReadMoreLink() {
        val request = core().helpDialog("Help", "Blurb", "https://example.org/manual")
        assertContains(request.message, "<a href=\"https://example.org/manual\">help_read_more_link</a>")
        assertTrue(request.cancellable)
    }
}
