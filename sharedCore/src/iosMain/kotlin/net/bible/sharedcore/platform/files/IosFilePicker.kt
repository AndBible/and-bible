@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedcore.platform.files

import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Source
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.*

internal fun contentTypesFor(mime: String): List<UTType> =
    listOf(pickerMimeType(mime)?.let { UTType.typeWithMIMEType(it) }?.takeUnless { it.dynamic } ?: UTTypeData)

/** Stable identifiers for formats whose MIME lookup varies between iOS runtimes. */
@kotlin.native.HiddenFromObjC
fun iosDocumentContentTypes(mimeTypes: List<String>): List<UTType> = mimeTypes.flatMap { mime ->
    when (mime.lowercase()) {
        "application/epub+zip" -> listOf(UTType.typeWithIdentifier("org.idpf.epub-container") ?: UTTypeData)
        "application/zip" -> listOf(UTTypeZIP)
        else -> contentTypesFor(mime)
    }
}.distinct().ifEmpty { listOf(UTTypeData) }

/** Host-owned document session using the same one-terminal-event ownership as backup pickers. */
@kotlin.native.HiddenFromObjC
class IosDocumentPickerSession {
    private var active: FilePresentation<List<NSURL>>? = null
    private var dismissal: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    suspend fun cancelPrevious() = withContext(Dispatchers.Main) {
        active?.finish(emptyList())
        dismissal?.await()
    }

    suspend fun pick(
        picker: UIDocumentPickerViewController,
        presenter: () -> UIViewController?,
    ): List<NSURL> = withContext(Dispatchers.Main) {
        cancelPrevious()
        val host = presenter() ?: return@withContext emptyList()
        picker.popoverPresentationController?.apply {
            sourceView = host.view
            sourceRect = host.view.bounds.useContents {
                platform.CoreGraphics.CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 0.0, 0.0)
            }
        }
        val completed = kotlinx.coroutines.CompletableDeferred<Unit>()
        try {
            suspendCancellableCoroutine { continuation ->
                lateinit var presentation: FilePresentation<List<NSURL>>
                presentation = FilePresentation(picker, continuation, emptyList(), picked = { urls ->
                    urls.filterIsInstance<NSURL>().filter { it.isFileURL() && it.absoluteString != null }
                }, release = {
                    if (active === presentation) active = null
                    completed.complete(Unit)
                })
                active = presentation
                dismissal = completed
                presentation.present(host)
            }
        } finally {
            // Retain staging in the caller until this particular sheet is gone, not a later session.
            withContext(kotlinx.coroutines.NonCancellable) { completed.await() }
        }
    }
}

class IosFilePicker(private val presenter: () -> UIViewController? = IosPresenter::topViewController) : FilePicker {
    override suspend fun pickBackupFile(mime: String): PickedSource? = withContext(Dispatchers.Main) {
        try {
            val host = presenter() ?: return@withContext null
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypesFor(mime), asCopy = true)
            suspendCancellableCoroutine { continuation ->
                val presentation = FilePresentation(picker, continuation, null, picked = { urls ->
                    val url = urls.firstOrNull() as? NSURL
                    val path = url?.path
                    if (path == null) {
                        logFileFailure("Document picker returned no local file URL")
                        null
                    } else PickedSource(path, url.lastPathComponent, mime)
                })
                presentation.present(host)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logFileFailure("Document picker failed", error)
            null
        }
    }

    override suspend fun openInput(source: PickedSource): Source = FileSystem.SYSTEM.source(source.token.toPath())
}
