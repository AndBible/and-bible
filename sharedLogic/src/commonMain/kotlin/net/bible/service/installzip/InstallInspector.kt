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

package net.bible.service.installzip

import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import okio.FileSystem
import okio.Path
import org.koin.mp.KoinPlatform

/**
 * Classifies an already-acquired local file into an [InstallPlan], mirroring the classic
 * `InstallZip.installFromFile` dispatch + `ZipHandler.checkZipFile` detection order:
 * StudyPad export -> epub mime -> font -> image (background) -> csv -> zip content (via
 * [determineFileType]; if it's actually an epub or invalid, or a genuine SWORD module zip) ->
 * sqlite content (by extension) -> else invalid.
 *
 * All effectful lookups (StudyPad stats/unzip, SWORD zip scanning, epub-upgrade confirmation,
 * and the [determineFileType] classifier itself) are injected as suspend lambdas so this class
 * stays a pure/testable classifier with no I/O of its own beyond simple [Path] existence checks
 * used to compute an optional overwrite-name.
 */
class InstallInspector(private val determineFileType: suspend (Path) -> BackupFileType) {

    /**
     * Classify [localFile] (already acquired to local storage) into an [InstallPlan].
     * [displayName]/[mimeType] come from the original URI. [existingSwordFiles] and
     * [studyPad] callbacks let tests inject filesystem/DB effects.
     */
    suspend fun inspect(
        localFile: Path,
        displayName: String,
        mimeType: String?,
        isStudyPadExport: Boolean,
        studyPadStats: suspend (Path) -> Pair<String, Path>,   // (statsText, unzipFolder)
        swordZipScan: suspend (Path) -> SwordZipScan,          // enumerate entries + existing files
        epubUpgradeCheck: suspend (displayName: String) -> Boolean,
    ): InstallPlan {
        val lowerName = displayName.lowercase()

        if (isStudyPadExport) {
            val (statsText, unzipFolder) = studyPadStats(localFile)
            return InstallPlan.StudyPad(statsText, unzipFolder)
        }

        if (mimeType == "application/epub+zip") {
            return InstallPlan.Epub(displayName, epubUpgradeCheck(displayName))
        }

        if (lowerName.endsWith(".ttf") || mimeType?.contains("font") == true) {
            return InstallPlan.Ttf(displayName, overwriteName("ttf", displayName))
        }

        val hasImageExtension = listOf(".jpg", ".jpeg", ".png", ".webp").any { lowerName.endsWith(it) }
        if (mimeType?.startsWith("image/") == true || hasImageExtension) {
            val fileName = backgroundImageFileName(displayName, lowerName, hasImageExtension, mimeType)
            return InstallPlan.BackgroundImage(fileName, overwriteName(BACKGROUND_IMAGE_DIR, fileName))
        }

        if (lowerName.endsWith(".csv") || mimeType == "text/csv") {
            return InstallPlan.Csv(displayName, overwriteName("prompts", displayName))
        }

        return when (determineFileType(localFile)) {
            BackupFileType.ZIP -> classifyZip(localFile, displayName, swordZipScan, epubUpgradeCheck)
            BackupFileType.SQLITE3 -> classifySqlite(displayName, lowerName)
            BackupFileType.UNKNOWN -> InstallPlan.Invalid(displayName)
        }
    }

    private suspend fun classifyZip(
        localFile: Path,
        displayName: String,
        swordZipScan: suspend (Path) -> SwordZipScan,
        epubUpgradeCheck: suspend (displayName: String) -> Boolean,
    ): InstallPlan {
        val scan = swordZipScan(localFile)
        return when {
            // Mirrors classic `ZipHandler.checkZipFile` throwing `EpubFile`, which the caller
            // (`installFromFile`/`ZipHandler.execute`) routes through the SAME `installEpub` used
            // for a mime-typed epub -- so this must carry the same upgrade-confirmation gate as
            // the [Epub] branch above, not commit unconditionally.
            scan.isEpub -> InstallPlan.EpubFromZip(displayName, epubUpgradeCheck(displayName))
            scan.invalid -> InstallPlan.Invalid(displayName)
            else -> InstallPlan.SwordZip(scan.existingFiles, scan.totalEntries)
        }
    }

    private fun classifySqlite(displayName: String, lowerName: String): InstallPlan {
        val type = when {
            lowerName.endsWith(".sqlite3") -> SqliteBookType.MYBIBLE
            lowerName.endsWith(".mybible") -> SqliteBookType.MYSWORD
            lowerName.endsWith(".bblx") -> SqliteBookType.ESWORD
            lowerName.endsWith(".bbli") -> SqliteBookType.ESWORD
            else -> null
        } ?: return InstallPlan.Invalid(displayName)
        val subDir = type.name.lowercase()
        return InstallPlan.Sqlite(type, displayName, overwriteName(subDir, displayName))
    }

    /**
     * Mirrors classic `installBackgroundImage`'s filename derivation: the discovery scanner
     * registers background images strictly by file extension, so guarantee the saved file
     * carries a recognized image extension. When the display name lacks one, derive it from the
     * MIME type; if unrecognized/null, default to ".jpg".
     */
    private fun backgroundImageFileName(
        displayName: String,
        lowerName: String,
        hasImageExtension: Boolean,
        mimeType: String?,
    ): String {
        if (hasImageExtension) return displayName
        val extension = when (mimeType) {
            "image/png" -> ".png"
            "image/jpeg" -> ".jpg"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        return displayName + extension
    }

    /** Null unless a file of that name already exists in `AppDirectories.modulesDir/<subDir>`. */
    private fun overwriteName(subDir: String, fileName: String): String? {
        val target = KoinPlatform.getKoin().get<AppDirectories>().modulesDir / subDir / fileName
        return if (FileSystem.SYSTEM.exists(target)) "$subDir/$fileName" else null
    }

    data class SwordZipScan(
        val existingFiles: List<String>,
        val totalEntries: Int,
        val isEpub: Boolean,      // otherFiles contained META-INF/container.xml
        val invalid: Boolean,     // otherFiles present but not epub, or no mods/modules
    )
}
