/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.db

import okio.use
import kotlinx.coroutines.IO
import androidx.annotation.VisibleForTesting
import androidx.room3.PooledConnection
import androidx.room3.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.joinColumnNames
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.service.common.DbType
import net.bible.service.common.dosDateTime
import kotlin.time.Clock
import kotlin.random.Random
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.common.compress.ZipWriter
import org.koin.mp.KoinPlatform

private const val TAG = "ExportStudyPad"

private suspend fun copyStudyPad(
    db: PooledConnection,
    label: BookmarkEntities.Label,
) = db.run {
    val sourceSchema: String = "main"
    val targetSchema: String = "export"
    val labelCols = columnNamesJoined("Label", targetSchema)
    val bibleBookmarkCols = columnNamesJoined("BibleBookmark", targetSchema)
    val bibleBookmarkToLabelCols = columnNamesJoined("BibleBookmarkToLabel", targetSchema)
    val bibleBookmarkNotesCols = columnNames("BibleBookmarkNotes", targetSchema)

    val genericBookmarkCols = columnNamesJoined("GenericBookmark", targetSchema)
    val genericBookmarkToLabelCols = columnNamesJoined("GenericBookmarkToLabel", targetSchema)
    val genericBookmarkNotesCols = columnNames("GenericBookmarkNotes", targetSchema)

    val studyPadTextEntryCols = columnNamesJoined("StudyPadTextEntry", targetSchema)
    val studyPadTextEntryTextCols = columnNamesJoined("StudyPadTextEntryText", targetSchema)

    fun where(column: String): String {
        return run {
            val labelIdHex = label.id.toString().replace("-", "")
            "WHERE $column = x'$labelIdHex'"
        }
    }

    exec("""
            INSERT OR IGNORE INTO $targetSchema.Label ($labelCols) 
            SELECT $labelCols FROM $sourceSchema.Label 
            ${where("id")}
            """.trimIndent())
    exec("""
            INSERT OR IGNORE INTO $targetSchema.BibleBookmark ($bibleBookmarkCols) 
            SELECT $bibleBookmarkCols FROM $sourceSchema.BibleBookmark bb 
            INNER JOIN $sourceSchema.BibleBookmarkToLabel bbl ON bb.id = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    exec("""
            INSERT OR IGNORE INTO $targetSchema.BibleBookmarkNotes (${joinColumnNames(bibleBookmarkNotesCols)}) 
            SELECT ${joinColumnNames(bibleBookmarkNotesCols, "bb")} FROM $sourceSchema.BibleBookmarkNotes bb 
            INNER JOIN $sourceSchema.BibleBookmarkToLabel bbl ON bb.bookmarkId = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    exec("""
            INSERT OR IGNORE INTO $targetSchema.BibleBookmarkToLabel ($bibleBookmarkToLabelCols) 
            SELECT $bibleBookmarkToLabelCols FROM $sourceSchema.BibleBookmarkToLabel 
            ${where("labelId")}
            """.trimIndent())

    exec("""
            INSERT OR IGNORE INTO $targetSchema.GenericBookmark ($genericBookmarkCols) 
            SELECT $genericBookmarkCols FROM $sourceSchema.GenericBookmark bb 
            INNER JOIN $sourceSchema.GenericBookmarkToLabel bbl ON bb.id = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    exec("""
            INSERT OR IGNORE INTO $targetSchema.GenericBookmarkNotes (${joinColumnNames(genericBookmarkNotesCols)}) 
            SELECT ${joinColumnNames(genericBookmarkNotesCols, "bb")} FROM $sourceSchema.GenericBookmarkNotes bb 
            INNER JOIN $sourceSchema.GenericBookmarkToLabel bbl ON bb.bookmarkId = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    exec("""
            INSERT OR IGNORE INTO $targetSchema.GenericBookmarkToLabel ($genericBookmarkToLabelCols) 
            SELECT $genericBookmarkToLabelCols FROM $sourceSchema.GenericBookmarkToLabel 
            ${where("labelId")}
            """.trimIndent())

    exec("""
            INSERT OR IGNORE INTO $targetSchema.StudyPadTextEntry ($studyPadTextEntryCols) 
            SELECT $studyPadTextEntryCols FROM $sourceSchema.StudyPadTextEntry te  
            ${where("te.labelId")}
            """.trimIndent())
    exec("""
            INSERT OR IGNORE INTO $targetSchema.StudyPadTextEntryText ($studyPadTextEntryTextCols) 
            SELECT $studyPadTextEntryTextCols FROM $sourceSchema.StudyPadTextEntryText tet 
            INNER JOIN $sourceSchema.StudyPadTextEntry te ON tet.studyPadTextEntryId = te.id 
            ${where("te.labelId")}
            """.trimIndent())
}

private suspend fun fixPrimaryLabels(db: PooledConnection) = db.run {
    for (table in listOf("BibleBookmark", "GenericBookmark")) {
        val fkid = queryLong("SELECT id FROM pragma_foreign_key_list('$table') WHERE `from` = 'primaryLabelId'")
            ?: throw RuntimeException("First item not found")
        exec("""
        UPDATE $table SET primaryLabelId = NULL
        WHERE rowId in (
            SELECT rowid FROM pragma_foreign_key_check('$table') 
            WHERE parent = "Label" AND fkid = $fkid
        ) 
        """.trimIndent()
        )
    }
}

fun sanitizeFilename(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

/**
 * Creates an empty bookmark database at [exportDbFile] and copies the StudyPads of [labels] into it (the
 * labels, their bookmarks with notes and label links, and their text entries) from [source], in one transaction
 * on [source]'s writer connection with the export file attached. [source] and [openExportDb] are test seams.
 */
@VisibleForTesting
suspend fun writeStudyPadExportDb(
    exportDbFile: Path,
    labels: List<BookmarkEntities.Label>,
    source: BookmarkDatabase = DatabaseContainer.instance.bookmarkDb,
    openExportDb: (String) -> BookmarkDatabase = { DatabaseContainer.instance.getBookmarkDb(it) },
) {
    val exportDb = openExportDb(exportDbFile.toString())
    try { exportDb.useWriterConnection { } } finally { exportDb.close() }
    source.useWriterConnectionMarked { db ->
        // ATTACH inside the protected region, so DETACH always runs once it may have succeeded.
        db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE export") {
            db.exec("ATTACH DATABASE '${exportDbFile}' AS export")
            db.exec("PRAGMA foreign_keys=OFF;")
            db.inTransaction<Unit> {
                for (label in labels) {
                    copyStudyPad(this, label)
                }
                // Primary label(s) of bookmarks might not be included, so let's fix them
                fixPrimaryLabels(this)
            }
        }
    }
}

/**
 * Writes the chosen StudyPads into an export archive and hands it to [host]. Platform-free: the Android entry point
 * (`exportStudyPads(activity, ...)` in `net.bible.android.platform`) supplies the host.
 */
suspend fun exportStudyPads(
    labels: List<BookmarkEntities.Label>,
    host: net.bible.android.control.backup.BackupHost,
    strings: CoreStrings,
    environment: AppEnvironment,
    backupControl: BackupControl,
    chooseDestination: (suspend () -> SaveOrShare?)? = null,
) = withContext(Dispatchers.IO) {
    val exportDbFile = environment.tmpDir.toPath() / "andbible-${Random.nextLong().toULong().toString(36)}.tmp"
    FileSystem.SYSTEM.write(exportDbFile, mustCreate = true) { }
    writeStudyPadExportDb(exportDbFile, labels)

    val filename = if (labels.size > 1) "StudyPads$DATABASE_BACKUP_SUFFIX" else sanitizeFilename(labels.first().name) + DATABASE_BACKUP_SUFFIX
    val zipFile = backupControl.internalDbBackupDir / filename
    val manifest = AndBibleBackupManifest(
        backupType = BackupType.STUDYPAD_EXPORT,
        contains = setOf(DbType.BOOKMARKS),
        andBibleVersion = environment.versionNumber.toInt(),
    )
    val dateTimeFormats = KoinPlatform.getKoin().get<DateTimeFormats>()
    ZipWriter(FileSystem.SYSTEM.sink(zipFile)).use { zip ->
        val dosTime = dosDateTime(Clock.System.now().toEpochMilliseconds(), dateTimeFormats)
        manifest.saveToZip(zip, dosTime)
        zip.putEntry("db/${BookmarkDatabase.dbFileName}", FileSystem.SYSTEM.source(exportDbFile), dosTime)
    }
    FileSystem.SYSTEM.delete(exportDbFile, mustExist = false)
    backupControl.saveOrShare(
        host = host,
        file = zipFile,
        fileName = filename,
        subject = strings.exportedStudypadsSubject,
        message = strings.exportedStudypadsMessage(environment.applicationNameMedium),
        chooserTitle = strings.sendExportFile,
        promptTitle = strings.exportDestinationTitle,
        promptMessage = strings.exportDestinationMessage,
        chooseDestination = chooseDestination,
    )
}
