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

import androidx.annotation.VisibleForTesting
import androidx.room.PooledConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.android.control.backup.SaveOrShare
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.joinColumnNames
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.common.CommonUtils
import net.bible.service.common.DbType
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
internal suspend fun writeStudyPadExportDb(
    exportDbFile: File,
    labels: List<BookmarkEntities.Label>,
    source: BookmarkDatabase = DatabaseContainer.instance.bookmarkDb,
    openExportDb: (String) -> BookmarkDatabase = { DatabaseContainer.instance.getBookmarkDb(it) },
) {
    val exportDb = openExportDb(exportDbFile.absolutePath)
    try { exportDb.useWriterConnection { } } finally { exportDb.close() }
    source.useWriterConnection { db ->
        db.exec("ATTACH DATABASE '${exportDbFile.absolutePath}' AS export")
        db.exec("PRAGMA foreign_keys=OFF;")
        db.withCleanup("PRAGMA foreign_keys=ON;", "DETACH DATABASE export") {
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

suspend fun exportStudyPads(
    activity: ActivityBase,
    vararg labels: BookmarkEntities.Label,
    chooseDestination: (suspend () -> SaveOrShare?)? = null,
) = withContext(Dispatchers.IO) {
    val exportDbFile = CommonUtils.tmpFile
    writeStudyPadExportDb(exportDbFile, labels.toList())

    val filename = if (labels.size > 1) "StudyPads$DATABASE_BACKUP_SUFFIX" else sanitizeFilename(labels.first().name) + DATABASE_BACKUP_SUFFIX
    val zipFile = File(BackupControl.internalDbBackupDir, filename)
    val manifest = AndBibleBackupManifest(
        backupType = BackupType.STUDYPAD_EXPORT,
        contains = setOf(DbType.BOOKMARKS),
    )
    ZipOutputStream(FileOutputStream(zipFile)).use { outFile ->
        manifest.saveToZip(outFile)
        FileInputStream(exportDbFile).use { inFile ->
            BufferedInputStream(inFile).use { origin ->
                val entry = ZipEntry("db/${BookmarkDatabase.dbFileName}")
                outFile.putNextEntry(entry)
                origin.copyTo(outFile)
            }
        }
    }
    exportDbFile.delete()
    val subject = activity.getString(R.string.exported_studypads_subject)
    val message = activity.getString(R.string.exported_studypads_message, CommonUtils.applicationNameMedium)
    BackupControl.saveOrShare(
        activity = activity,
        file = zipFile,
        fileName = filename,
        subject = subject,
        message = message,
        chooserTitle = activity.getString(R.string.send_export_file),
        promptTitle = R.string.export_destination_title,
        promptMessage = R.string.export_destination_message,
        chooseDestination = chooseDestination,
    )
}
