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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.cinterop.*
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.settings.TextDisplaySettingsRouteEntry
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.app.ports.TextDisplayEntry
import net.bible.sharedui.app.ports.TextDisplayPlatform
import okio.Path
import okio.FileSystem
import org.jetbrains.skia.Image
import platform.CoreFoundation.*
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.*
import platform.ImageIO.*
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/** The same route entry and label contract as Android, with ImageIO sampled decoding before Skia conversion. */
@kotlin.native.HiddenFromObjC
class IosTextDisplayPlatform(
    private val sharedService: () -> TextDisplaySettingsServiceImpl = { org.koin.mp.KoinPlatform.getKoin().get() },
    private val inventory: () -> Map<String, Path> = {
        org.koin.mp.KoinPlatform.getKoin().get<net.bible.sharedlogic.platform.BackgroundImageCatalog>()
            .backgroundImages.mapValues { it.value.path }
    },
    private val readBytes: (Path) -> ByteArray = { path -> FileSystem.SYSTEM.read(path) { readByteArray() } },
) : TextDisplayPlatform {
    private val thumbnails = mutableMapOf<String, ImageBitmap?>()

    override fun entryFor(args: TextDisplaySettingsArgs): TextDisplayEntry {
        val entry = TextDisplaySettingsRouteEntry(args, sharedService())
        return TextDisplayEntry(entry.service, entry.scope, { scope: SettingsScope ->
            ManageLabelsMapper.hideLabelsData(entry.service.currentHideLabelsIds(scope), scope is SettingsScope.Window).toJSON()
        }, entry::resultOnLeave)
    }

    override fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController) {
        val data = ManageLabelsContract.ManageLabelsData.fromJSON(resultJson)
        if (data.reset) controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
        else controller.onHideLabelsChange(data.selectedLabels.map { it.toString() })
    }

    override fun thumbnail(token: String): ImageBitmap? {
        if (thumbnails.containsKey(token)) return thumbnails[token]
        val image = try {
            inventory()[token]?.let { decodeThumbnail(readBytes(it)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        thumbnails[token] = image
        return image
    }

    private fun decodeThumbnail(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val dataRef = CFBridgingRetain(data)
        try {
            val source = CGImageSourceCreateWithData(dataRef?.reinterpret(), null) ?: return null
            try {
                val options = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
                val size = CFBridgingRetain(NSNumber(int = 240))
                try {
                    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                    CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, size)
                    CFDictionarySetValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
                    val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options) ?: return null
                    try {
                        val encoded = UIImagePNGRepresentation(UIImage.imageWithCGImage(image)) ?: return null
                        val smallBytes = ByteArray(encoded.length.toInt())
                        smallBytes.usePinned { memcpy(it.addressOf(0), encoded.bytes, encoded.length) }
                        return Image.makeFromEncoded(smallBytes).toComposeImageBitmap()
                    } finally { CGImageRelease(image) }
                } finally {
                    if (size != null) CFRelease(size)
                    CFRelease(options)
                }
            } finally { CFRelease(source) }
        } finally { if (dataRef != null) CFRelease(dataRef) }
    }
}
