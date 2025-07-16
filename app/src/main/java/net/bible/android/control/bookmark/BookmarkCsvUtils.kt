/*
 * Copyright (c) 2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.defaultLabelColor
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
        bookmarkControl: BookmarkControl
    ) = withContext(Dispatchers.IO) {
        try {
            OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                // Write header
                val headers = listOf(
                    HEADER_OSIS_REF, HEADER_BIBLE_REF, HEADER_DOCUMENT, HEADER_BOOK, HEADER_CHAPTER_START,
                    HEADER_VERSE_START, HEADER_CHAPTER_END, HEADER_VERSE_END, HEADER_ID,
                    HEADER_ORDINAL_START, HEADER_ORDINAL_END, HEADER_CREATED_AT, HEADER_LAST_UPDATED,
                    HEADER_START_OFFSET, HEADER_END_OFFSET, HEADER_LABELS, HEADER_NOTES, HEADER_CUSTOM_ICON
                )
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

                    val values = listOf(
                        verseRange.osisRef,
                        escapeField(verseRange.name),
                        escapeField(bookmark.book?.initials ?: ""),
                        escapeField(start.book.osis),
                        start.chapter.toString(),
                        start.verse.toString(),
                        end.chapter.toString(), 
                        end.verse.toString(),
                        bookmark.id.toString(),
                        bookmark.ordinalStart.toString(),
                        bookmark.ordinalEnd.toString(),
                        bookmark.createdAt.let { ISO_DATE_FORMAT.format(it) },
                        bookmark.lastUpdatedOn.let { ISO_DATE_FORMAT.format(it) },
                        bookmark.startOffset?.toString() ?: "",
                        bookmark.endOffset?.toString() ?: "",
                        escapeField(labelNames),
                        escapeField(bookmark.notes ?: ""),
                        bookmark.customIcon ?: ""
                    )
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
                val headerLine = reader.readLine() ?: throw IOException("Empty CSV file")
                val headers = parseCSVLine(headerLine)
                val headerMap = headers.withIndex().associate { it.value to it.index }

                reader.lineSequence().forEachIndexed { lineIndex, line ->
                    try {
                        if (line.trim().isEmpty()) return@forEachIndexed
                        
                        val values = parseCSVLine(line)
                        val bookmarkData = parseCsvRowToBookmark(values, headerMap, lineIndex + 2)
                        
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
                            errorMessages.add("Line ${lineIndex + 2}: Invalid bookmark data")
                        }
                    } catch (e: Exception) {
                        errorMessages.add("Line ${lineIndex + 2}: ${e.message}")
                        Log.w(TAG, "Error importing bookmark from line ${lineIndex + 2}", e)
                    }
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
                    labelsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
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

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
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
