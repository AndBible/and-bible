@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedcore.platform.files

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
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData

internal fun contentTypesFor(mime: String): List<UTType> =
    listOf(pickerMimeType(mime)?.let { UTType.typeWithMIMEType(it) } ?: UTTypeData)

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
