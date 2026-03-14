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

package net.bible.android.control.bookmark

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.defaultLabelColor
import net.bible.android.view.activity.page.application
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "BookmarkCsvUtils"

/**
 * CSV Import/Export utilities for Bible bookmarks
 * Uses semicolon (;) as separator to handle commas in notes
 */
object BookmarkCsvUtils {

    // CSV column headers
    private const val HEADER_OSIS_REF = "osisRef"
    private const val HEADER_BIBLE_REF = "bibleRef"
    private const val HEADER_DOCUMENT = "document"
    private const val HEADER_BOOK = "book"
    private const val HEADER_CHAPTER_START = "chapterStart"
    private const val HEADER_VERSE_START = "verseStart"
    private const val HEADER_CHAPTER_END = "chapterEnd"
    private const val HEADER_VERSE_END = "verseEnd"
    private const val HEADER_ID = "id"
    private const val HEADER_ORDINAL_START = "ordinalStart"
    private const val HEADER_ORDINAL_END = "ordinalEnd"
    private const val HEADER_CREATED_AT = "createdAt"
    private const val HEADER_LAST_UPDATED = "lastUpdatedOn"
    private const val HEADER_START_OFFSET = "startOffset"
    private const val HEADER_END_OFFSET = "endOffset"
    private const val HEADER_LABELS = "labels"
    private const val HEADER_NOTES = "notes"
    private const val HEADER_CUSTOM_ICON = "customIcon"

    data class CsvColumn(
        val key: String,
        val displayNameResId: Int,
        val defaultSelected: Boolean = true
    ) {
        val header get() = key
        val displayName get() = application.getString(displayNameResId)
    }

    val availableColumns = listOf(
        CsvColumn(HEADER_OSIS_REF, R.string.osis_reference),
        CsvColumn(HEADER_BIBLE_REF, R.string.bible_reference),
        CsvColumn(HEADER_DOCUMENT, R.string.document),
        CsvColumn(HEADER_BOOK, R.string.book),
        CsvColumn(HEADER_CHAPTER_START, R.string.chapter_start),
        CsvColumn(HEADER_VERSE_START, R.string.verse_start),
        CsvColumn(HEADER_CHAPTER_END, R.string.chapter_end),
        CsvColumn(HEADER_VERSE_END, R.string.verse_end),
        CsvColumn(HEADER_ID, R.string.id),
        CsvColumn(HEADER_ORDINAL_START, R.string.ordinal_start),
        CsvColumn(HEADER_ORDINAL_END, R.string.ordinal_end),
        CsvColumn(HEADER_CREATED_AT, R.string.created_at),
        CsvColumn(HEADER_LAST_UPDATED, R.string.last_updated_at),
        CsvColumn(HEADER_START_OFFSET, R.string.start_offset),
        CsvColumn(HEADER_END_OFFSET, R.string.end_offset),
        CsvColumn(HEADER_LABELS, R.string.labels),
        CsvColumn(HEADER_NOTES, R.string.bookmark_notes),
        CsvColumn(HEADER_CUSTOM_ICON, R.string.custom_icon)
    )

    private const val CSV_SEPARATOR = ";"
    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Export Bible bookmarks to CSV format
     */
    suspend fun exportBookmarksToCsv(
        outputStream: OutputStream,
        bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>,
        bookmarkControl: BookmarkControl,
        selectedColumns: List<String> = availableColumns.map { it.key }
    ) = withContext(Dispatchers.IO) {
        try {
            OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                // Write header - only selected columns
                val headers = availableColumns.filter { it.key in selectedColumns }.map { it.header }
                writer.write(headers.joinToString(CSV_SEPARATOR))
                writer.write("\n")

                // Write bookmark data
                for (bookmark in bookmarks) {
                    val verseRange = bookmark.verseRange
                    val start = verseRange.start
                    val end = verseRange.end

                    // Get labels for this bookmark
                    val labels = bookmarkControl.labelsForBookmark(bookmark)
                    val labelNames = labels.joinToString(";") { it.name }

                    // Create map of all possible values
                    val allValues = mapOf(
                        HEADER_OSIS_REF to verseRange.osisRef,
                        HEADER_BIBLE_REF to escapeField(verseRange.name),
                        HEADER_DOCUMENT to escapeField(bookmark.book?.initials ?: ""),
                        HEADER_BOOK to escapeField(start.book.osis),
                        HEADER_CHAPTER_START to start.chapter.toString(),
                        HEADER_VERSE_START to start.verse.toString(),
                        HEADER_CHAPTER_END to end.chapter.toString(),
                        HEADER_VERSE_END to end.verse.toString(),
                        HEADER_ID to bookmark.id.toString(),
                        HEADER_ORDINAL_START to bookmark.kjvOrdinalStart.toString(),
                        HEADER_ORDINAL_END to bookmark.kjvOrdinalEnd.toString(),
                        HEADER_CREATED_AT to bookmark.createdAt.let { ISO_DATE_FORMAT.format(it) },
                        HEADER_LAST_UPDATED to bookmark.lastUpdatedOn.let { ISO_DATE_FORMAT.format(it) },
                        HEADER_START_OFFSET to (bookmark.startOffset?.toString() ?: ""),
                        HEADER_END_OFFSET to (bookmark.endOffset?.toString() ?: ""),
                        HEADER_LABELS to escapeField(labelNames),
                        HEADER_NOTES to escapeField(bookmark.notes ?: ""),
                        HEADER_CUSTOM_ICON to (bookmark.customIcon ?: "")
                    )

                    // Write only selected columns
                    val values = availableColumns.filter { it.key in selectedColumns }.map { allValues[it.key] ?: "" }
                    writer.write(values.joinToString(CSV_SEPARATOR))
                    writer.write("\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting bookmarks to CSV", e)
            throw e
        }
    }

    /**
     * Import Bible bookmarks from CSV format
     */
    suspend fun importBookmarksFromCsv(
        inputStream: InputStream,
        bookmarkControl: BookmarkControl
    ): ImportResult = withContext(Dispatchers.IO) {
        var created = 0
        var updated = 0
        val errorMessages = mutableListOf<String>()

        try {
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                val headerRecord = readCsvRecord(reader) ?: throw IOException("Empty CSV file")
                val headers = headerRecord
                val headerMap = headers.withIndex().associate { it.value to it.index }

                var recordNumber = 2 // Start from 2 since header is 1
                while (true) {
                    val record = readCsvRecord(reader) ?: break
                    
                    try {
                        if (record.all { it.trim().isEmpty() }) {
                            recordNumber++
                            continue
                        }
                        
                        val bookmarkData = parseCsvRowToBookmark(record, headerMap, recordNumber)
                        
                        if (bookmarkData != null) {
                            val (bookmark, labelNames) = bookmarkData
                            
                            // Check if bookmark exists (by ID if provided, or by verse range)
                            val existingBookmark = if (bookmark.id.toString().isNotEmpty()) {
                                bookmarkControl.bibleBookmarkById(bookmark.id)
                            } else {
                                null
                            }

                            val savedBookmark = if (existingBookmark != null) {
                                // Update existing bookmark
                                bookmark.new = false
                                bookmarkControl.addOrUpdateBibleBookmark(bookmark, updateNotes = true)
                                updated++
                                bookmark
                            } else {
                                // Create new bookmark
                                bookmark.new = true
                                if (bookmark.id.toString().isEmpty()) {
                                    bookmark.id = IdType()
                                }
                                bookmarkControl.addOrUpdateBibleBookmark(bookmark)
                                created++
                                bookmark
                            }

                            // Handle labels if provided
                            if (labelNames.isNotEmpty()) {
                                assignLabelsToBookmark(savedBookmark, labelNames, bookmarkControl)
                            }
                        } else {
                            errorMessages.add("Record $recordNumber: Invalid bookmark data")
                        }
                    } catch (e: Exception) {
                        errorMessages.add("Record $recordNumber: ${e.message}")
                        Log.w(TAG, "Error importing bookmark from record $recordNumber", e)
                    }
                    recordNumber++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing bookmarks from CSV", e)
            throw e
        }

        ImportResult(created, updated, errorMessages)
    }

    private fun assignLabelsToBookmark(
        bookmark: BookmarkEntities.BibleBookmarkWithNotes,
        labelNames: List<String>,
        bookmarkControl: BookmarkControl
    ) {
        try {
            val allLabels = bookmarkControl.allLabels.associateBy { it.name.trim() }
            val labelIds = mutableListOf<IdType>()

            for (labelName in labelNames) {
                if (labelName.trim().isEmpty()) continue
                
                // Find existing label or create new one
                val existingLabel = allLabels.get(labelName.trim())
                if (existingLabel != null) {
                    labelIds.add(existingLabel.id)
                } else {
                    // Create new label
                    val newLabel = BookmarkEntities.Label(
                        name = labelName.trim(),
                        color = defaultLabelColor
                    ).apply {
                        new = true
                    }
                    bookmarkControl.insertOrUpdateLabel(newLabel)
                    labelIds.add(newLabel.id)
                }
            }
            if (labelIds.isNotEmpty()) {
                bookmark.primaryLabelId = labelIds.first()
                bookmarkControl.changeLabelsForBookmark(bookmark, labelIds.toSet().toList())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error assigning labels to bookmark: ${e.message}")
        }
    }

    private fun parseCsvRowToBookmark(
        values: List<String>,
        headerMap: Map<String, Int>,
        lineNumber: Int
    ): Pair<BookmarkEntities.BibleBookmarkWithNotes, List<String>>? {
        try {
            // Try to get verse range from different sources
            val verseRange = getVerseRange(values, headerMap) 
                ?: throw IllegalArgumentException("Could not determine verse range from available data")

            val id = getValueOrNull(values, headerMap, HEADER_ID)?.let { 
                if (it.isNotEmpty()) IdType(it) else IdType()
            } ?: IdType()

            val createdAt = getValueOrNull(values, headerMap, HEADER_CREATED_AT)?.let {
                if (it.isNotEmpty()) ISO_DATE_FORMAT.parse(it) else Date()
            } ?: Date()

            val lastUpdatedOn = getValueOrNull(values, headerMap, HEADER_LAST_UPDATED)?.let {
                if (it.isNotEmpty()) ISO_DATE_FORMAT.parse(it) else Date()
            } ?: Date()

            val startOffset = getValueOrNull(values, headerMap, HEADER_START_OFFSET)?.let {
                if (it.isNotEmpty()) it.toIntOrNull() else null
            }

            val endOffset = getValueOrNull(values, headerMap, HEADER_END_OFFSET)?.let {
                if (it.isNotEmpty()) it.toIntOrNull() else null
            }

            val notes = getValueOrNull(values, headerMap, HEADER_NOTES)?.let {
                it.ifEmpty { null }
            }

            val customIcon = getValueOrNull(values, headerMap, HEADER_CUSTOM_ICON)?.let {
                it.ifEmpty { null }
            }

            val document = getValueOrNull(values, headerMap, HEADER_DOCUMENT)?.let {
                if (it.isNotEmpty() ) Books.installed().getBook(it) else null
            }

            // Parse labels
            val labels = getValueOrNull(values, headerMap, HEADER_LABELS)?.let { labelsStr ->
                if (labelsStr.isNotEmpty()) {
                    labelsStr.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
            } ?: emptyList()

            val textRange = if (startOffset != null && endOffset != null) {
                BookmarkEntities.TextRange(startOffset, endOffset)
            } else null

            val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
                verseRange = verseRange,
                textRange = textRange,
                wholeVerse = textRange == null,
                book = document as? AbstractPassageBook
            ).apply {
                this.id = id
                this.createdAt = createdAt
                this.lastUpdatedOn = lastUpdatedOn
                this.notes = notes
                this.customIcon = customIcon
            }

            return bookmark to labels

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing CSV row $lineNumber: ${e.message}")
            return null
        }
    }

    private fun getVerseRange(values: List<String>, headerMap: Map<String, Int>): VerseRange? {
        // Try ordinals
        val ordinalStart = getValueOrNull(values, headerMap, HEADER_ORDINAL_START)?.toIntOrNull()
        val ordinalEnd = getValueOrNull(values, headerMap, HEADER_ORDINAL_END)?.toIntOrNull() ?: ordinalStart
        if (ordinalStart != null && ordinalEnd != null) {
            try {
                val v11n = KJVA
                val startVerse = Verse(v11n, ordinalStart)
                val endVerse = Verse(v11n, ordinalEnd)
                return VerseRange(v11n, startVerse, endVerse)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create verse range from ordinals: $ordinalStart-$ordinalEnd", e)
            }
        }

        // Try OSIS reference first
        getValueOrNull(values, headerMap, HEADER_OSIS_REF)?.let { osisRef ->
            if (osisRef.isNotEmpty()) {
                try {
                    return VerseRangeFactory.fromString(KJVA, osisRef)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse OSIS reference: $osisRef", e)
                }
            }
        }

        // Try discrete book/chapter/verse
        val book = getValueOrNull(values, headerMap, HEADER_BOOK)
        val chapterStart = getValueOrNull(values, headerMap, HEADER_CHAPTER_START)?.toIntOrNull()
        val verseStart = getValueOrNull(values, headerMap, HEADER_VERSE_START)?.toIntOrNull()
        if (book != null && chapterStart != null && verseStart != null) {
            try {
                val chapterEnd = getValueOrNull(values, headerMap, HEADER_CHAPTER_END)?.toIntOrNull() ?: chapterStart
                val verseEnd = getValueOrNull(values, headerMap, HEADER_VERSE_END)?.toIntOrNull() ?: verseStart
                
                val osisRef = if (chapterStart == chapterEnd && verseStart == verseEnd) {
                    "$book.$chapterStart.$verseStart"
                } else {
                    "$book.$chapterStart.$verseStart-$book.$chapterEnd.$verseEnd"
                }
                
                return VerseRangeFactory.fromString(KJVA, osisRef)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create verse range from discrete values: $book $chapterStart:$verseStart", e)
            }
        }

        // Lastly, try general bible ref reference
        getValueOrNull(values, headerMap, HEADER_BIBLE_REF)?.let { bibleRef ->
            if (bibleRef.isNotEmpty()) {
                try {
                    return PassageKeyFactory.instance().getKey(KJVA, bibleRef).getRangeAt(0, RestrictionType.NONE)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse bible reference: $bibleRef", e)
                }
            }
        }

        return null
    }

    private fun getValueOrNull(values: List<String>, headerMap: Map<String, Int>, header: String): String? {
        val index = headerMap[header] ?: return null
        return if (index < values.size) values[index] else null
    }

    private fun escapeField(field: String): String {
        return if (field.contains(CSV_SEPARATOR) || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\"" 
        } else {
            field
        }
    }

    /**
     * Reads a complete CSV record that may span multiple lines
     */
    private fun readCsvRecord(reader: BufferedReader): List<String>? {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        while (true) {
            val line = reader.readLine() ?: break
            
            var i = 0
            while (i < line.length) {
                val char = line[i]
                when {
                    char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        // Escaped quote
                        current.append('"')
                        i += 2
                        continue
                    }
                    char == '"' -> {
                        inQuotes = !inQuotes
                    }
                    char == ';' && !inQuotes -> {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                    else -> {
                        current.append(char)
                    }
                }
                i++
            }
            
            if (inQuotes) {
                // We're inside quotes and hit end of line - add newline and continue reading
                current.append('\n')
            } else {
                // Not in quotes, record is complete
                break
            }
        }
        
        if (result.isEmpty() && current.isEmpty()) {
            return null // End of file
        }
        
        // Add the last field
        result.add(current.toString())
        return result
    }

    data class ImportResult(
        val created: Int,
        val updated: Int,
        val errorMessages: List<String>,
    ) {
        val errors: Int get() = errorMessages.size
    }
}
