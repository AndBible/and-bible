@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.epubInitials
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import platform.Foundation.*
import kotlin.native.Platform

/** Records observations only; never offers files or changes installation lifecycle. */
internal class OsRoutingReceiptState(private val process: String) {
    var expectedName: String? = null
        private set
    var exportProcess: String? = null
        private set
    private var count = 0
    val receiptValue: String? get() = expectedName?.takeIf { count > 0 }?.let { "$it|count=$count|process=$process" }

    fun clear() { expectedName = null; exportProcess = null; count = 0 }
    fun expect(name: String, exportedBy: String): Boolean {
        clear()
        if (!Regex("F-OS-[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}[.](zip|epub)").matches(name)) return false
        expectedName = name
        exportProcess = exportedBy
        return true
    }
    fun observe(value: String) {
        val url = NSURL.URLWithString(value) ?: return
        if (url.isFileURL() && expectedName != null && url.lastPathComponent == expectedName) count++
    }
}

internal object IosOsRoutingReceipt {
    private const val NAME = "andbible.debug.osRouting.name"
    private const val PROCESS = "andbible.debug.osRouting.exportProcess"
    private val process = NSUUID().UUIDString
    private val state = OsRoutingReceiptState(process)
    private var revision by mutableStateOf(0)

    fun prepareLaunch(provisioning: Boolean) {
        if (!Platform.isDebugBinary) return
        if (provisioning) { clear(); return }
        val defaults = NSUserDefaults.standardUserDefaults
        val name = defaults.stringForKey(NAME) ?: return
        val exportedBy = defaults.stringForKey(PROCESS) ?: return
        if (Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(exportedBy)) {
            state.expect(name, exportedBy)
        }
        revision++
    }

    private fun clear() {
        state.clear()
        NSUserDefaults.standardUserDefaults.removeObjectForKey(NAME)
        NSUserDefaults.standardUserDefaults.removeObjectForKey(PROCESS)
        revision++
    }

    /** Called only after real native Save and independent external binary comparison. */
    fun exported(name: String) {
        if (!Platform.isDebugBinary) return
        check(state.expect(name, process))
        NSUserDefaults.standardUserDefaults.setObject(name, forKey = NAME)
        NSUserDefaults.standardUserDefaults.setObject(process, forKey = PROCESS)
        revision++
    }

    /** Called solely by the genuine bootstrap URL boundary, on Main, before its unchanged offer. */
    fun observe(url: String) {
        if (!Platform.isDebugBinary) return
        state.observe(url)
        revision++
        state.receiptValue?.let { nsLog("OsRouting receipt=$it") }
    }

    private fun contentMatches(name: String): Boolean {
        val koin = KoinPlatform.getKoin()
        val strings = koin.get<CoreStrings>()
        val policy = koin.get<SaxTextPolicy>()
        val book = Books.installed().getBook(if (name.endsWith(".zip")) "K4RawText" else epubInitials(name)) ?: return false
        if (name.endsWith(".zip")) return SwordContentFacade.getCanonicalText(book, book.getKey("Gen.1.1"),
            strings = strings, saxTextPolicy = policy).trim() == "In the beginning God created the heaven and the earth."
        val text = book.getGlobalKeyList().joinToString("\n") {
            SwordContentFacade.getCanonicalText(book, it, strings = strings, saxTextPolicy = policy)
        }
        return text.contains("F runtime chapter one proof.") && text.contains("F runtime chapter two proof.")
    }

    @Composable
    fun Overlay(strings: Strings) {
        if (!Platform.isDebugBinary) return
        val currentRevision = revision
        val receipt = state.receiptValue
        val name = state.expectedName
        var verified by remember(name, receipt) { mutableStateOf(false) }
        LaunchedEffect(name, receipt) {
            if (name == null || receipt == null) return@LaunchedEffect
            while (!verified) {
                try { verified = withContext(Dispatchers.Default) { contentMatches(name) } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* Backend may be temporarily unreadable during registration. */ }
                if (!verified) delay(250)
            }
        }
        Column {
            state.exportProcess?.let { value ->
                Text(strings.okay, Modifier.testTag("os-routing-export-process").semantics { stateDescription = value })
            }
            if (receipt != null) Text(strings.okay, Modifier.testTag("os-routing-receipt").semantics { stateDescription = receipt })
            if (verified && name != null) Text(strings.okay,
                Modifier.testTag("os-routing-content-verified").semantics { stateDescription = name })
        }
    }
}
