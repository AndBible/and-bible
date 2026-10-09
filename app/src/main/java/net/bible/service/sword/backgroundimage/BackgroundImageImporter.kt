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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import net.bible.sharedcore.log.Log
import net.bible.android.SharedConstants
import net.bible.service.common.AndBibleAddons
import java.io.File
import java.io.FileOutputStream

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
     *  caches. Returns the written [File], or null on failure. Call on `Dispatchers.IO`. */
    fun copyAndRegister(context: Context, uri: Uri): File? {
        return try {
            val outDir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR).apply { mkdirs() }
            val outFile = uniqueFile(outDir, fileName(context, uri))
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { FileOutputStream(outFile).use { output -> it.copyTo(output) } }
            addManuallyInstalledBackgroundImageBooks()
            AndBibleAddons.clearCaches()
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import background image", e)
            null
        }
    }

    /** A safe on-disk file name for the picked image: display name with an image extension. */
    private fun fileName(context: Context, uri: Uri): String {
        val displayName = context.contentResolver.query(uri, null, null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
        }
        val base = displayName?.takeIf { it.isNotBlank() } ?: "image"
        if (base.substringAfterLast('.', "").lowercase() in imageExtensions) return base
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return "$base.$ext"
    }

    /** Avoid clobbering an existing image another workspace may reference. */
    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "${stem}_$i" else "${stem}_$i.$ext")
            i++
        }
        return candidate
    }
}
