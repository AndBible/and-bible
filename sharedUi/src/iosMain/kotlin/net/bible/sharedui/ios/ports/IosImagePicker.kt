/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.ports.ImagePicker
import kotlinx.coroutines.*
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.sharedcore.platform.files.IosSheetPresentation
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatform
import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

@kotlin.native.HiddenFromObjC
class IosImagePicker(
    private val presenter: () -> UIViewController? = IosPresenter::topViewController,
    private val makePicker: (PHPickerConfiguration) -> PHPickerViewController = { PHPickerViewController(it) },
    private val tempDirectory: () -> String = { KoinPlatform.getKoin().get<AppEnvironment>().tmpDir },
    private val imageProvider: (List<*>) -> NSItemProvider? = {
        (it.firstOrNull() as? PHPickerResult)?.itemProvider
    },
) : ImagePicker {
    override suspend fun pickImage(): String? = withContext(Dispatchers.Main) {
        val host = presenter() ?: return@withContext null
        val sheet = makePicker(PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        })
        val dismissed = CompletableDeferred<Unit>()
        var copied: String? = null
        var handedOff = false
        try {
            val result = suspendCancellableCoroutine<String?> { continuation ->
                var retainedDelegate: PHPickerViewControllerDelegateProtocol? = null
                val presentation = IosSheetPresentation(sheet, continuation, null) {
                    // Read and clear the strong reference held by this terminal cleanup closure.
                    if (sheet.delegate === retainedDelegate) sheet.delegate = null
                    retainedDelegate = null
                    dismissed.complete(Unit)
                }
                val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                    private var selected = false
                    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                        if (!presentation.isPending || selected) return
                        selected = true
                        val provider = imageProvider(didFinishPicking)
                        if (provider == null) {
                            presentation.finish(null)
                            return
                        }
                        val directory = tempDirectory().toPath() / "andbible-image-${NSUUID().UUIDString}"
                        provider.loadFileRepresentationForTypeIdentifier(UTTypeImage.identifier) { url, error ->
                            // This URL belongs to NSItemProvider and expires when this callback returns.
                            // Copy synchronously here, before dispatching any result to the main queue.
                            var destination: String? = null
                            try {
                                if (error == null && url != null && url.isFileURL()) {
                                    val name = url.lastPathComponent
                                        ?.map { if (it == '/' || it == '\\' || it < ' ' || it == '\u007f') '_' else it }
                                        ?.joinToString("")
                                        ?.takeUnless { it.isBlank() || it == "." || it == ".." }
                                    if (name != null) {
                                        FileSystem.SYSTEM.createDirectories(directory)
                                        val target = NSURL.fileURLWithPath((directory / name).toString())
                                        val scoped = url.startAccessingSecurityScopedResource()
                                        val copiedFile = try { NSFileManager.defaultManager.copyItemAtURL(url, target, null) }
                                        finally { if (scoped) url.stopAccessingSecurityScopedResource() }
                                        if (copiedFile) destination = target.absoluteString
                                    }
                                }
                            } catch (_: Exception) {
                                destination = null
                            }
                            val result = destination
                            CoroutineScope(Dispatchers.Main).launch {
                                if (result != null && presentation.isPending) {
                                    copied = directory.toString()
                                    presentation.finish(result)
                                } else {
                                    FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
                                    if (presentation.isPending) presentation.finish(null)
                                }
                            }
                        }
                    }
                }
                sheet.delegate = delegate
                // UIKit's delegate is weak. The release closure must retain it until terminal cleanup.
                retainedDelegate = delegate
                presentation.present(host)
            }
            handedOff = result != null
            result
        } finally {
            withContext(NonCancellable) {
                dismissed.await()
                if (!handedOff) copied?.let { FileSystem.SYSTEM.deleteRecursively(it.toPath(), mustExist = false) }
            }
        }
    }
}
