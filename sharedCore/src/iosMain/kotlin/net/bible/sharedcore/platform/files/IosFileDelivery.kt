@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedcore.platform.files

import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.setValue
import platform.UIKit.*

class IosFileDelivery(private val presenter: () -> UIViewController? = IosPresenter::topViewController) : FileDelivery {
    override suspend fun deliver(
        file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
        subject: String?, message: String?, chooserTitle: String,
    ): DeliveryResult = withContext(Dispatchers.Main) {
        var staging: Path? = null
        var dismissed: CompletableDeferred<Unit>? = null
        try {
            val source = file.toPath()
            if (!withContext(Dispatchers.IO) { FileSystem.SYSTEM.metadata(source).isRegularFile }) {
                logFileFailure("Delivery source is not a regular file")
                return@withContext DeliveryResult.FAILED
            }
            val exported = if (source.name == fileName) source else {
                // A fresh private directory avoids clobbering another concurrent export or the source.
                require(fileName.isNotBlank() && fileName != "." && fileName != ".." &&
                    '/' !in fileName && '\\' !in fileName && '\u0000' !in fileName) { "Invalid export file name" }
                val directory = NSTemporaryDirectory().toPath() / "andbible-export-${NSUUID().UUIDString}"
                staging = directory
                withContext(Dispatchers.IO) {
                    FileSystem.SYSTEM.createDirectory(directory)
                    (directory / fileName).also { FileSystem.SYSTEM.copy(source, it) }
                }
            }
            val url = NSURL.fileURLWithPath(exported.toString())
            // Copying suspends; resolve the current modal stack only after preparation is finished.
            val host = presenter() ?: return@withContext DeliveryResult.CANCELLED
            suspendCancellableCoroutine { continuation ->
                val sheet = when (mode) {
                    SaveOrShare.SAVE -> UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
                    SaveOrShare.SHARE -> UIActivityViewController(
                        activityItems = listOfNotNull(url, message), applicationActivities = null
                    ).apply { if (subject != null) setValue(subject, forKey = "subject") }
                }
                val completion = CompletableDeferred<Unit>()
                val presentation = FilePresentation(sheet, continuation, DeliveryResult.CANCELLED,
                    picked = { urls -> if (urls.isNotEmpty()) DeliveryResult.DELIVERED else DeliveryResult.CANCELLED },
                    release = { completion.complete(Unit) })
                (sheet as? UIActivityViewController)?.completionWithItemsHandler = { _, completed, _, error ->
                    val result = when {
                        completed -> DeliveryResult.DELIVERED
                        error != null -> {
                            logFileFailure("Share sheet failed: ${error.localizedDescription}")
                            DeliveryResult.FAILED
                        }
                        else -> DeliveryResult.CANCELLED
                    }
                    presentation.finish(result)
                }
                sheet.popoverPresentationController?.apply {
                    sourceView = host.view
                    sourceRect = host.view.bounds.useContents {
                        CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 0.0, 0.0)
                    }
                }
                dismissed = completion
                presentation.present(host)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logFileFailure("File delivery failed", error)
            DeliveryResult.FAILED
        } finally {
            // Keep ownership across cancellation and UIKit's asynchronous dismissal. No detached cleanup job.
            withContext(NonCancellable) {
                dismissed?.await()
                withContext(Dispatchers.IO) { deleteStaging(staging) }
            }
        }
    }
}

private fun deleteStaging(directory: Path?) {
    if (directory == null) return
    try { FileSystem.SYSTEM.deleteRecursively(directory) }
    catch (error: Exception) { logFileFailure("Export staging cleanup failed", error) }
}
