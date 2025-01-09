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

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.getColumnNames
import net.bible.android.database.migrations.getColumnNamesJoined
import net.bible.android.database.migrations.joinColumnNames
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.common.CommonUtils
import net.bible.service.common.DbType
import net.bible.service.common.getFirst
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "ExportStudyPad"

private fun copyStudyPad(
    db: SupportSQLiteDatabase,
    label: BookmarkEntities.Label,
) = db.run {
    val sourceSchema: String = "main"
    val targetSchema: String = "export"
    val labelCols = getColumnNamesJoined(db, "Label", targetSchema)
    val bibleBookmarkCols = getColumnNamesJoined(db, "BibleBookmark", targetSchema)
    val bibleBookmarkToLabelCols = getColumnNamesJoined(db, "BibleBookmarkToLabel", targetSchema)
    val bibleBookmarkNotesCols = getColumnNames(db, "BibleBookmarkNotes", targetSchema)

    val genericBookmarkCols = getColumnNamesJoined(db, "GenericBookmark", targetSchema)
    val genericBookmarkToLabelCols = getColumnNamesJoined(db, "GenericBookmarkToLabel", targetSchema)
    val genericBookmarkNotesCols = getColumnNames(db, "GenericBookmarkNotes", targetSchema)

    val studyPadTextEntryCols = getColumnNamesJoined(db, "StudyPadTextEntry", targetSchema)
    val studyPadTextEntryTextCols = getColumnNamesJoined(db, "StudyPadTextEntryText", targetSchema)

    fun where(column: String): String {
        return run {
            val labelIdHex = label.id.toString().replace("-", "")
            "WHERE $column = x'$labelIdHex'"
        }
    }

    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.Label ($labelCols) 
            SELECT $labelCols FROM $sourceSchema.Label 
            ${where("id")}
            """.trimIndent())
    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.BibleBookmark ($bibleBookmarkCols) 
            SELECT $bibleBookmarkCols FROM $sourceSchema.BibleBookmark bb 
            INNER JOIN $sourceSchema.BibleBookmarkToLabel bbl ON bb.id = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.BibleBookmarkNotes (${joinColumnNames(bibleBookmarkNotesCols)}) 
            SELECT ${joinColumnNames(bibleBookmarkNotesCols, "bb")} FROM $sourceSchema.BibleBookmarkNotes bb 
            INNER JOIN $sourceSchema.BibleBookmarkToLabel bbl ON bb.bookmarkId = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.BibleBookmarkToLabel ($bibleBookmarkToLabelCols) 
            SELECT $bibleBookmarkToLabelCols FROM $sourceSchema.BibleBookmarkToLabel 
            ${where("labelId")}
            """.trimIndent())

    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.GenericBookmark ($genericBookmarkCols) 
            SELECT $genericBookmarkCols FROM $sourceSchema.GenericBookmark bb 
            INNER JOIN $sourceSchema.GenericBookmarkToLabel bbl ON bb.id = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.GenericBookmarkNotes (${joinColumnNames(genericBookmarkNotesCols)}) 
            SELECT ${joinColumnNames(genericBookmarkNotesCols, "bb")} FROM $sourceSchema.GenericBookmarkNotes bb 
            INNER JOIN $sourceSchema.GenericBookmarkToLabel bbl ON bb.bookmarkId = bbl.bookmarkId 
            ${where("bbl.labelId")}
            """.trimIndent())
    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.GenericBookmarkToLabel ($genericBookmarkToLabelCols) 
            SELECT $genericBookmarkToLabelCols FROM $sourceSchema.GenericBookmarkToLabel 
            ${where("labelId")}
            """.trimIndent())

    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.StudyPadTextEntry ($studyPadTextEntryCols) 
            SELECT $studyPadTextEntryCols FROM $sourceSchema.StudyPadTextEntry te  
            ${where("te.labelId")}
            """.trimIndent())
    execSQL("""
            INSERT OR IGNORE INTO $targetSchema.StudyPadTextEntryText ($studyPadTextEntryTextCols) 
            SELECT $studyPadTextEntryTextCols FROM $sourceSchema.StudyPadTextEntryText tet 
            INNER JOIN $sourceSchema.StudyPadTextEntry te ON tet.studyPadTextEntryId = te.id 
            ${where("te.labelId")}
            """.trimIndent())
}

private fun fixPrimaryLabels(db: SupportSQLiteDatabase) = db.run {
    for (table in listOf("BibleBookmark", "GenericBookmark")) {
        val fkid = query("SELECT id FROM pragma_foreign_key_list('$table') WHERE `from` = 'primaryLabelId'")
            .getFirst { it.getLong(0) }
        execSQL("""
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

suspend fun exportStudyPads(activity: ActivityBase, vararg labels: BookmarkEntities.Label) = withContext(Dispatchers.IO) {
    val exportDbFile = CommonUtils.tmpFile
    val exportDb = DatabaseContainer.instance.getBookmarkDb(exportDbFile.absolutePath)
    exportDb.openHelper.writableDatabase.use {}
    DatabaseContainer.instance.bookmarkDb.openHelper.writableDatabase.run {
        execSQL("ATTACH DATABASE '${exportDbFile.absolutePath}' AS export")
        execSQL("PRAGMA foreign_keys=OFF;")
        beginTransaction()
        for (label in labels) {
            copyStudyPad(this, label)
        }
        // Primary label(s) of bookmarks might not be included, so let's fix them
        fixPrimaryLabels(this)
        setTransactionSuccessful()
        endTransaction()
        execSQL("PRAGMA foreign_keys=ON;")
        execSQL("DETACH DATABASE export")
    }

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
        chooserTitle = activity.getString(R.string.send_backup_file),
    )
}
