@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.uikit.LocalUIViewController
import net.bible.sharedui.ios.app.canRenderIosAppDialogs
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.koin.mp.KoinPlatform
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIDocumentPickerViewController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.sharedui.ios.ports.IosClipboardText
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.strings.Strings
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPasteboard
import kotlin.native.Platform

/** App-hosted boundary diagnostics only; never a replacement for normal feature navigation. */
internal object IosFilesRuntimeHarness {
    private var selectedPort: String? = null
    private var filename: String? = null
    private var dialogToken: String? = null

    fun prepareLaunch() {
        if (!Platform.isDebugBinary) return
        val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
        val index = args.indexOf("-filesPortTest")
        selectedPort = if (index >= 0) args.getOrNull(index + 1)?.takeIf {
            it in setOf("document", "share", "image", "create-writeback", "delivery-save", "image-select", "dialog-native", "dialog-background", "os-routing-zip", "os-routing-epub")
        } else null
        IosOsRoutingReceipt.prepareLaunch(selectedPort == "os-routing-zip" || selectedPort == "os-routing-epub")
        val tokenIndex = args.indexOf("-filesDialogToken")
        dialogToken = args.getOrNull(tokenIndex + 1)?.takeIf { tokenIndex >= 0 &&
            Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(it) }
        val nameIndex = args.indexOf("-filesPortFilename")
        filename = if (nameIndex >= 0) args.getOrNull(nameIndex + 1)?.takeIf {
            it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '/' || c == '\\' || c < ' ' }
        } else null
    }

    fun clipboardSelfTest(): String? {
        if (!Platform.isDebugBinary) return "Unsupported: files port diagnostics require a debug binary"
        if (!NSThread.isMainThread) return "Clipboard diagnostic must run on the main thread"
        val pasteboard = UIPasteboard.generalPasteboard
        val previous = pasteboard.items
        return try {
            val clipboard = IosClipboardText()
            val first = "AndBible clipboard port roundtrip 1"
            val second = "AndBible clipboard port roundtrip 2"
            clipboard.write("files-port-test", first)
            check(clipboard.read() == first) { "Production clipboard read did not return the written text" }
            check(pasteboard.string == first) { "Production write did not update the system pasteboard" }
            pasteboard.string = second
            check(clipboard.read() == second) { "Production read did not observe the system pasteboard" }
            null
        } catch (error: Throwable) {
            "Clipboard port roundtrip failed: $error"
        } finally {
            pasteboard.items = previous
        }
    }

    private class DialogProof(val dialogs: AppDialogController, val token: String) {
        var armed = false
        var picker: UIDocumentPickerViewController? = null
        var id: Long? = null
        val results = mutableListOf<AppDialogResult>()
        fun log(event: String) {
            check(NSThread.isMainThread)
            net.bible.sharedcore.log.nsLog("FilesDialog event=$event token=$token id=$id pending=${dialogs.pending.value?.id} progress=${dialogs.progress.value?.id} results=$results nativeAttached=${picker?.view?.window != null}")
        }
        fun post(message: String, okay: String, event: String) {
            if (!armed || id != null) return
            check(picker?.view?.window != null) { "Retention enqueue requires actual attached picker" }
            id = dialogs.post(AppDialogRequest.Message(title = null, message = message, confirmText = okay)) {
                results.add(it)
                log("response")
            }
            log(event)
        }
        fun answeredOnce(): Boolean = id != null && results == listOf(AppDialogResult.Ok) &&
            dialogs.pending.value?.id != id && dialogs.progress.value?.id != id
    }

    @Composable
    fun Overlay(strings: Strings, awaitGraph: suspend () -> Unit) {
        if (!Platform.isDebugBinary) return
        IosOsRoutingReceipt.Overlay(strings)
        val port = selectedPort ?: return
        val scope = rememberCoroutineScope()
        val document = remember { IosDocumentPicker() }
        val image = remember { IosImagePicker() }
        val share = remember { IosTextShare() }
        var ready by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var verified by remember { mutableStateOf(false) }
        var selectedFolder by remember { mutableStateOf<net.bible.sharedui.app.ports.DocumentRef?>(null) }
        val routing = port == "os-routing-zip" || port == "os-routing-epub"
        val needsFolder = port == "create-writeback" || port == "delivery-save" || routing
        val retention = port == "dialog-native" || port == "dialog-background"
        val proof = remember(port) {
            if (retention) DialogProof(KoinPlatform.getKoin().get(), requireNotNull(dialogToken)) else null
        }
        val baseController = LocalUIViewController.current
        var answeredOnce by remember { mutableStateOf(false) }
        val message = "${strings.errorOccurred}: ${dialogToken.orEmpty()}"
        DisposableEffect(proof) {
            val center = NSNotificationCenter.defaultCenter
            val observer = if (proof != null && port == "dialog-background") {
                center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
                    if (proof.armed && proof.id == null) proof.post(message, strings.okay, "enqueue-background")
                }
            } else null
            onDispose { if (observer != null) center.removeObserver(observer) }
        }
        LaunchedEffect(proof) {
            val tracked = proof ?: return@LaunchedEffect
            var previous: String? = null
            while (true) {
                val safe = canRenderIosAppDialogs(baseController)
                val state = "${tracked.id}/${tracked.dialogs.pending.value?.id}/${tracked.results}/$safe"
                if (tracked.id != null && state != previous) {
                    tracked.log(if (safe) "before-render-safe" else "deferred")
                    previous = state
                }
                answeredOnce = tracked.answeredOnce()
                if (tracked.results.isNotEmpty() && !answeredOnce) failed = true
                delay(50)
            }
        }
        LaunchedEffect(Unit) {
            awaitGraph()
            // Graph attachment alone does not guarantee an active, attached UIKit host.
            while (IosPresenter.topViewController()?.view?.window == null) delay(50)
            try {
                if (routing) IosFilesPayloadProof.routingPreflight(requireNotNull(filename), port == "os-routing-epub")
                ready = true
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                net.bible.sharedcore.log.nsLog("Files routing preflight failed: ${error.stackTraceToString()}")
                failed = true
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface {
                Column(Modifier.padding(16.dp)) {
                    if (ready && !busy && !failed) Text(strings.okay, Modifier.testTag("files-port-idle"))
                    if (failed) Text(strings.errorOccurred, Modifier.testTag("files-port-error"))
                    if (verified && !busy) Text(strings.okay, Modifier.testTag("files-port-verified"))
                    if (answeredOnce && !busy) Text(strings.okay, Modifier.testTag("files-dialog-answered-once"))
                    if (retention && failed) Text(strings.errorOccurred, Modifier.testTag("files-dialog-error"))
                    if (needsFolder) {
                        if (selectedFolder != null && !busy) Text(strings.okay, Modifier.testTag("files-port-folder-ready"))
                        Button(
                            enabled = ready && !busy && !failed,
                            modifier = Modifier.testTag("files-port-folder-select"),
                            onClick = {
                                selectedFolder = null
                                verified = false
                                busy = true
                                scope.launch {
                                    try { selectedFolder = document.openDocumentTree() }
                                    catch (error: CancellationException) { throw error }
                                    catch (error: Exception) {
                                        net.bible.sharedcore.log.nsLog("Files folder diagnostic failed: ${error.stackTraceToString()}")
                                        failed = true
                                    } finally { busy = false }
                                }
                            },
                        ) { Text(strings.okay) }
                    }
                    Button(
                        enabled = ready && !busy && !failed && (!needsFolder || selectedFolder != null),
                        modifier = Modifier.testTag("files-port-open"),
                        onClick = {
                            busy = true
                            verified = false
                            scope.launch {
                                try {
                                    when (port) {
                                        "dialog-native", "dialog-background" -> {
                                            val tracked = requireNotNull(proof)
                                            check(!tracked.armed) { "Retention diagnostic is one shot" }
                                            tracked.armed = true
                                            val observer = launch {
                                                withTimeout(20_000) {
                                                    while (tracked.picker == null) {
                                                        val attached = IosPresenter.topViewController() as? UIDocumentPickerViewController
                                                        if (attached?.view?.window != null) tracked.picker = attached else delay(25)
                                                    }
                                                }
                                                tracked.log("native-attached")
                                                if (port == "dialog-native") tracked.post(message, strings.okay, "enqueue-native")
                                            }
                                            try {
                                                check(document.openDocuments(listOf("application/zip"), false).isEmpty()) {
                                                    "Retention proof unexpectedly selected a file"
                                                }
                                            } finally { observer.cancel() }
                                            tracked.log("native-dismissed")
                                            check(tracked.id != null) { "No request was enqueued under actual coverage" }
                                        }
                                        "create-writeback" -> {
                                            IosFilesPayloadProof.create(document, requireNotNull(filename), requireNotNull(selectedFolder))
                                            verified = true
                                        }
                                        "delivery-save" -> {
                                            IosFilesPayloadProof.deliver(requireNotNull(filename), strings.okay, requireNotNull(selectedFolder))
                                            verified = true
                                        }
                                        "os-routing-zip", "os-routing-epub" -> {
                                            IosFilesPayloadProof.deliverRoutingFixture(requireNotNull(filename),
                                                port == "os-routing-epub", strings.okay, requireNotNull(selectedFolder))
                                            verified = true
                                        }
                                        "image-select" -> {
                                            IosFilesPayloadProof.image(image)
                                            verified = true
                                        }
                                        "document" -> check(document.openDocuments(listOf("application/zip"), false).isEmpty()) {
                                            "Cancel proof unexpectedly selected a document"
                                        }
                                        "image" -> check(image.pickImage() == null) {
                                            "Cancel proof unexpectedly selected an image"
                                        }
                                        "share" -> {
                                            share.shareText(strings.okay, strings.shareLabel)
                                            // TextShare is fire-and-forget: observe the real attached activity controller,
                                            // then its disappearance. Never synthesize a completion callback.
                                            val sheet = withTimeout(20_000) {
                                                var visible: UIActivityViewController? = null
                                                while (visible == null) {
                                                    visible = IosPresenter.topViewController() as? UIActivityViewController
                                                    if (visible?.view?.window == null) { visible = null; delay(50) }
                                                }
                                                requireNotNull(visible)
                                            }
                                            withTimeout(120_000) {
                                                while (sheet.presentingViewController != null || sheet.view.window != null ||
                                                    IosPresenter.topViewController() == null) delay(50)
                                            }
                                        }
                                    }
                                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                                    failed = true
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    net.bible.sharedcore.log.nsLog("Files port diagnostic failed: ${error.stackTraceToString()}")
                                    failed = true
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text(strings.okay) }
                }
            }
        }
    }
}
