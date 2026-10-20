@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedcore.platform.files

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.IOException
import okio.Source
import okio.Timeout
import platform.Foundation.NSInputStream
import platform.Foundation.NSURL
import platform.Foundation.inputStreamWithURL
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

    suspend fun cancelPrevious(isCurrent: () -> Boolean = { true }): Boolean = withContext(Dispatchers.Main) {
        do {
            if (!isCurrent()) return@withContext false
            active?.finish(emptyList())
            dismissal?.await()
            // A stale replacement must not sweep a newer sheet after this suspension.
            if (!isCurrent()) return@withContext false
        } while (active != null)
        true
    }

    suspend fun pick(
        picker: UIDocumentPickerViewController,
        isCurrent: () -> Boolean = { true },
        presenter: () -> UIViewController?,
    ): List<NSURL> = withContext(Dispatchers.Main) {
        if (!cancelPrevious(isCurrent)) return@withContext emptyList()
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

    /** Stream the picked path exactly, without normalizing valid POSIX backslashes. */
    override suspend fun openInput(source: PickedSource): Source {
        if (!source.token.startsWith("/") || '\u0000' in source.token) throw IOException("Invalid picked file path")
        val url = NSURL.fileURLWithPath(source.token)
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val stream = NSInputStream.inputStreamWithURL(url) ?: throw IOException("Cannot open backup file")
            stream.open()
            return object : Source {
                private var closed = false
                override fun read(sink: Buffer, byteCount: Long): Long {
                    check(!closed) { "Source is closed" }
                    require(byteCount >= 0) { "byteCount must not be negative" }
                    if (byteCount == 0L) return 0L
                    val bytes = ByteArray(minOf(byteCount, 8192L).toInt())
                    val count = bytes.usePinned { stream.read(it.addressOf(0).reinterpret(), bytes.size.toULong()) }
                    if (count < 0) throw IOException("Cannot read backup file")
                    if (count == 0L) return -1L
                    sink.write(bytes, 0, count.toInt())
                    return count
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {
                    if (!closed) {
                        closed = true
                        try { stream.close() } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
                    }
                }
            }
        } catch (failure: Throwable) {
            if (scoped) url.stopAccessingSecurityScopedResource()
            throw failure
        }
    }
}
