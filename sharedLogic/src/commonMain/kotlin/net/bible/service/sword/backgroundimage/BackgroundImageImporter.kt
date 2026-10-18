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
package net.bible.service.sword.backgroundimage

import okio.use
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.log.Log
import net.bible.sharedlogic.platform.AddonCatalog
import okio.FileSystem
import okio.Path
import okio.buffer
import org.crosswire.common.util.KFiles
import org.koin.mp.KoinPlatform

private const val TAG = "BackgroundImageImporter"
private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

/**
 * Copy-into-`modulesDir/background` + SWORD-registration logic for a user-picked background
 * image, lifted VERBATIM (`copyAndRegister`/`fileName`/`uniqueFile`) from the classic
 * BackgroundImageChooserActivity (deleted in Z-late slice S12). Used by
 * [net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl.importBackgroundImage]
 * for the Compose colours/background-image screens.
 */
object BackgroundImageImporter {
    /** Copy [uri] into `modulesDir/background` (unique name), register all image modules, clear
     *  caches. Returns the written [Path], or null on failure. Call on `Dispatchers.IO`. */
    fun copyAndRegister(source: ContentSource, uri: String): Path? {
        return try {
            val outDir = backgroundImageModulesDir().also { KFiles.createDirectories(it) }
            val outFile = uniqueFile(outDir, fileName(source, uri))
            val input = source.openSource(uri) ?: return null
            input.buffer().use { FileSystem.SYSTEM.sink(outFile).use { output -> it.readAll(output) } }
            addManuallyInstalledBackgroundImageBooks()
            KoinPlatform.getKoin().get<AddonCatalog>().clearCaches()
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import background image", e)
            null
        }
    }

    /** A safe on-disk file name for the picked image: display name with an image extension. */
    private fun fileName(source: ContentSource, uri: String): String {
        val displayName = source.displayName(uri)
        val base = displayName?.takeIf { it.isNotBlank() } ?: "image"
        if (base.substringAfterLast('.', "").lowercase() in imageExtensions) return base
        val ext = when (source.mimeType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return "$base.$ext"
    }

    /** Avoid clobbering an existing image another workspace may reference. */
    private fun uniqueFile(dir: Path, name: String): Path {
        var candidate = dir / name
        if (!KFiles.exists(candidate)) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (KFiles.exists(candidate)) {
            candidate = dir / (if (ext.isEmpty()) "${stem}_$i" else "${stem}_$i.$ext")
            i++
        }
        return candidate
    }
}
