@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.epubInitials
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import platform.Foundation.*
import kotlin.native.Platform

/** Debug evidence boundary: the real queue and installer own every mutation of the document registry. */
internal object IosIncomingRuntimeHarness {
    private var enabled = false
    private var controlsVisible by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var swordVerified by mutableStateOf(false)
    private var epubVerified by mutableStateOf(false)
    private val epubId get() = epubInitials("f-runtime.epub")

    /** Cold `-incomingFixture` aliases; a comma list offers several archives to the same production queue in order. */
    private val coldFixtures = mapOf(
        "sword" to "f-rawtext.zip",
        "invalid" to "f-invalid.zip",
        "cipher" to "h2-cipher.zip",
        "genbook" to "h2-genbook.zip",
        "maps" to "h2-maps.zip",
        "dict" to "h2-dict.zip",
    )
    private val allowedOffers = coldFixtures.values.toSet() + "f-runtime.epub"

    /** Initials installed by the H2 fixtures, which must be absent before the cold offer is delivered. */
    private val h2Initials = mapOf(
        "h2-cipher.zip" to "H2Cipher", "h2-genbook.zip" to "K4GenBook",
        "h2-maps.zip" to "H2Maps", "h2-dict.zip" to "K4ZLD",
    )
    private var coldOffered = emptyList<String>()
    private var removeColdFixtures = false

    fun prepareLaunch() {
        if (!Platform.isDebugBinary) return
        val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
        enabled = "-incomingRuntimeTest" in args
        controlsVisible = enabled && "-incomingRuntimeControls" in args
        removeColdFixtures = enabled && "-incomingRemoveFixtures" in args
        if (!enabled) return
        val index = args.indexOf("-incomingFixture")
        if (index >= 0) {
            try {
                val fixtures = (args.getOrNull(index + 1) ?: error("Missing cold incoming fixture")).split(",").map {
                    coldFixtures[it] ?: error("Unknown cold incoming fixture")
                }
                coldOffered = fixtures
                fixtures.forEach { offer(it) }
            } catch (error: Exception) { fail(error) }
        }
    }

    private fun fail(error: Exception) {
        nsLog("Incoming runtime diagnostic failed: ${error.stackTraceToString()}")
        failed = true
    }

    private fun offer(name: String) {
        check(enabled && Platform.isDebugBinary)
        require(name in allowedOffers)
        nsLog("IncomingFilesHarness event=fixture-offer-start basename=$name")
        val resource = NSBundle.mainBundle.resourceURL!!.URLByAppendingPathComponent("files-port-fixtures/$name")!!
        val folder = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent("f-runtime-${NSUUID().UUIDString}", true)!!
        check(NSFileManager.defaultManager.createDirectoryAtURL(folder, true, null, null))
        val destination = folder.URLByAppendingPathComponent(name)!!
        check(NSFileManager.defaultManager.copyItemAtURL(resource, destination, null))
        // Acquire/copy through production ingress even before startup/reset or graph attachment.
        IosIncomingFiles.offer(destination.absoluteString!!)
        nsLog("IncomingFilesHarness event=fixture-offer-returned basename=$name")
    }

    /** Must run after startup reset and before the production drainer can deliver a cold queued item. */
    suspend fun preflightBeforeBind(): Boolean {
        if (!Platform.isDebugBinary || !enabled) return true
        if (failed) return false
        return try {
            withContext(Dispatchers.Default) {
                check(Books.installed().getBook("K4RawText") == null) { "SWORD fixture already registered" }
                check(Books.installed().getBook(epubId) == null) { "EPUB fixture already registered" }
                coldOffered.mapNotNull { h2Initials[it] }.forEach { initials ->
                    // Opt-in cleanup of a previous run's test-owned fixture only (never KJV): the production
                    // delete path, so the absence check below still proves a clean install.
                    if (removeColdFixtures) Books.installed().getBook(initials)?.let { SwordDocumentFacade.deleteDocument(it) }
                    check(Books.installed().getBook(initials) == null) { "$initials fixture already registered" }
                }
            }
            ready = true
            true
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { fail(error); false }
    }

    private fun verifyInstalled(): Pair<Boolean, Boolean> {
        val koin = KoinPlatform.getKoin()
        val strings = koin.get<CoreStrings>()
        val policy = koin.get<SaxTextPolicy>()
        val sword = Books.installed().getBook("K4RawText")
        val swordOk = sword?.let {
            SwordContentFacade.getCanonicalText(it, it.getKey("Gen.1.1"), strings = strings, saxTextPolicy = policy)
                .trim() == "In the beginning God created the heaven and the earth."
        } ?: false
        val epub = Books.installed().getBook(epubId)
        val epubOk = epub?.let {
            // Reading the registered backend's global key set traverses the actual installed EPUB,
            // not the bundled archive or its extracted source file.
            val text = it.getGlobalKeyList().joinToString("\n") { key ->
                SwordContentFacade.getCanonicalText(it, key, strings = strings, saxTextPolicy = policy)
            }
            text.contains("F runtime chapter one proof.") && text.contains("F runtime chapter two proof.")
        } ?: false
        return swordOk to epubOk
    }

    @Composable
    fun Overlay(strings: Strings, backupRoute: Boolean, openBackup: () -> Unit) {
        if (!Platform.isDebugBinary || !enabled) return
        val scope = rememberCoroutineScope()
        LaunchedEffect(ready) {
            if (!ready) return@LaunchedEffect
            while (true) {
                try {
                    val (sword, epub) = withContext(Dispatchers.Default) { verifyInstalled() }
                    swordVerified = sword
                    epubVerified = epub
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    // Installation/optimization can temporarily expose an unreadable book. Retry actual content.
                    nsLog("Incoming backend proof waiting: ${error.message}")
                }
                delay(250)
            }
        }
        // Backend proof remains active even when diagnostics are hidden for normal navigation.
        if (!controlsVisible) return
        Column {
            if (ready && !failed) Text(strings.okay, Modifier.testTag("incoming-runtime-ready"))
            if (failed) Text(strings.errorOccurred, Modifier.testTag("incoming-runtime-error"))
            if (swordVerified) Text(strings.okay, Modifier.testTag("incoming-sword-content-verified"))
            if (epubVerified) Text(strings.okay, Modifier.testTag("incoming-epub-content-verified"))
            if (backupRoute) Text(strings.okay, Modifier.testTag("incoming-backup-route-ready"))
            Button(enabled = ready && !failed, modifier = Modifier.testTag("incoming-offer-epub"), onClick = {
                nsLog("IncomingFilesHarness event=offer-button-click basename=f-runtime.epub")
                scope.launch { try { offer("f-runtime.epub") } catch (error: Exception) { fail(error) } }
            }) { Text(strings.okay) }
            Button(enabled = ready && !failed, modifier = Modifier.testTag("incoming-open-backup"), onClick = {
                controlsVisible = false
                openBackup()
            }) {
                Text(strings.okay)
            }
        }
    }
}
