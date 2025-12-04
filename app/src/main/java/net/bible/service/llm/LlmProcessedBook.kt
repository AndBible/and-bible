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

package net.bible.service.llm

import android.util.Log
import kotlinx.coroutines.runBlocking
import net.bible.service.llm.processors.TranslationProcessor
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LlmProcessedBook"

/**
 * Backend state for LLM-processed books. Wraps any Book type.
 */
class LlmProcessedBackendState(
    val wrappedBook: Book,
    val processor: LlmProcessor,
    val processingParams: String,
    private val ownMetadata: SwordBookMetaData
) : OpenFileState {

    private var _lastAccess: Long = 0L

    // Return our OWN metadata, not the wrapped book's, to avoid state pooling conflicts
    override fun getBookMetaData(): SwordBookMetaData = ownMetadata

    override fun releaseResources() {
        // The wrapped book manages its own resources
    }

    override fun close() {
        // Nothing to close - wrapped book manages resources
    }

    override fun getLastAccess(): Long = _lastAccess

    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }
}

/**
 * Backend that wraps another Book and provides LLM-processed content.
 *
 * This backend:
 * 1. First checks the processing cache
 * 2. If not cached, fetches original content from wrapped book
 * 3. Processes via LLM API and caches the result
 */
class LlmProcessedBackend(
    private val state: LlmProcessedBackendState,
    metadata: SwordBookMetaData
) : AbstractKeyBackend<LlmProcessedBackendState>(metadata) {

    /**
     * Get the global key list from the wrapped book.
     * Book.getGlobalKeyList() is always supported, unlike Backend.getGlobalKeyList().
     */
    private val wrappedKeyList: Key by lazy {
        state.wrappedBook.globalKeyList
    }

    override fun initState(): LlmProcessedBackendState {
        Log.i(TAG, "initState for ${bookMetaData.initials}")
        return state
    }

    override fun getCardinality(): Int = wrappedKeyList.cardinality

    override fun get(index: Int): Key = wrappedKeyList.get(index)

    override fun indexOf(that: Key): Int = wrappedKeyList.indexOf(that)

    override fun readRawContent(state: LlmProcessedBackendState, key: Key): String {
        val originalInitials = state.wrappedBook.initials
        val keyName = key.osisRef
        val cacheKey = state.processor.getCacheKey(originalInitials, keyName, state.processingParams)

        // Check cache first
        val cacheResult = LlmProcessingService.getCached(cacheKey)

        if (cacheResult.processedXml != null) {
            Log.d(TAG, "Using cached content for $originalInitials:$keyName [${state.processor.processorId}/${state.processingParams}]")
            return cacheResult.processedXml
        }

        // Get original content from wrapped book (use Book.getRawText, not Backend.getRawText)
        val originalContent = state.wrappedBook.getRawText(key)

        // Process and cache
        return runBlocking {
            LlmProcessingService.processAndCache(
                state.processor,
                cacheKey,
                originalContent
            )
        }
    }
}

/**
 * Creates the metadata configuration for an LLM-processed book.
 */
private fun createProcessedMetadata(
    wrappedBook: Book,
    processor: LlmProcessor,
    processingParams: String
): SwordBookMetaData {
    val originalMetadata = wrappedBook.bookMetaData as SwordBookMetaData
    val processedInitials = "${wrappedBook.initials}/${processor.processorId}/$processingParams"
    val description = processor.getDescription(processingParams)

    val conf = """
[$processedInitials]
Description=${originalMetadata.name} ($description)
Abbreviation=${originalMetadata.abbreviation}/${processor.processorId}/$processingParams
Category=${originalMetadata.bookCategory.name}
AndBibleLlmProcessedModule=1
AndBibleOriginalModule=${wrappedBook.initials}
AndBibleProcessorId=${processor.processorId}
AndBibleProcessingParams=$processingParams
Lang=${if (processor.processorId == "translations") processingParams else originalMetadata.language.code}
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=${if (wrappedBook is SwordBook) wrappedBook.versification.name else "KJVA"}
""".trim()

    return SwordBookMetaData(conf.toByteArray(), processedInitials).also {
        it.driver = originalMetadata.driver
    }
}

/**
 * Cache of processed books to avoid creating duplicates.
 * Key is "originalInitials/processorId/params"
 */
private val processedBooksCache = ConcurrentHashMap<String, Book>()

/**
 * Gets or creates a virtual LLM-processed book for the given original book.
 *
 * @param originalBook The original Book to process
 * @param processorId The processor type (e.g., "translations")
 * @param processingParams The processing parameters (e.g., "fi" for Finnish translation)
 * @return A virtual Book that provides processed content
 */
fun getOrCreateProcessedBook(originalBook: Book, processorId: String, processingParams: String): Book? {
    val processor = LlmProcessingService.getProcessor(processorId) ?: run {
        Log.e(TAG, "Unknown processor: $processorId")
        return null
    }

    val cacheKey = "${originalBook.initials}/$processorId/$processingParams"

    return processedBooksCache.getOrPut(cacheKey) {
        Log.i(TAG, "Creating processed book: $cacheKey")

        // Check if already registered in Books
        val existingBook = Books.installed().getBook(cacheKey)
        if (existingBook != null) {
            Log.i(TAG, "Found existing processed book in Books.installed(): $cacheKey")
            return@getOrPut existingBook
        }

        // Create new processed book
        val metadata = createProcessedMetadata(originalBook, processor, processingParams)
        val state = LlmProcessedBackendState(originalBook, processor, processingParams, metadata)
        val backend = LlmProcessedBackend(state, metadata)

        // Create appropriate book type based on original
        val processedBook = when (originalBook.bookCategory) {
            BookCategory.DICTIONARY -> SwordDictionary(metadata, backend)
            BookCategory.GENERAL_BOOK -> SwordGenBook(metadata, backend)
            else -> SwordBook(metadata, backend)
        }

        // Register with JSword's Books
        Books.installed().addBook(processedBook)
        Log.i(TAG, "Registered processed book: $cacheKey")

        processedBook
    }
}

/**
 * Convenience function to get/create a translated book.
 */
fun getOrCreateTranslatedBook(originalBook: Book, targetLanguage: String): Book? =
    getOrCreateProcessedBook(originalBook, TranslationProcessor.processorId, targetLanguage)

/**
 * Extension property to check if a book is an LLM-processed wrapper.
 */
val Book.isLlmProcessedBook: Boolean
    get() = bookMetaData.getProperty("AndBibleLlmProcessedModule") != null

/**
 * Extension property to get the original book initials for a processed book.
 */
val Book.originalBookInitials: String?
    get() = bookMetaData.getProperty("AndBibleOriginalModule")

/**
 * Extension property to get the processor ID for a processed book.
 */
val Book.llmProcessorId: String?
    get() = bookMetaData.getProperty("AndBibleProcessorId")

/**
 * Extension property to get the processing params for a processed book.
 */
val Book.llmProcessingParams: String?
    get() = bookMetaData.getProperty("AndBibleProcessingParams")

/**
 * Parses a book initials string that may be a processed book.
 * Format: {original}/{processorId}/{params}
 *
 * Returns a triple of (originalInitials, processorId, params) where processorId and params
 * are null if this is not a processed book initials.
 *
 * Examples:
 * - "ESV2011" -> ("ESV2011", null, null)
 * - "ESV2011/translations/fi" -> ("ESV2011", "translations", "fi")
 */
fun parseProcessedBookInitials(bookInitials: String): Triple<String, String?, String?> {
    val parts = bookInitials.split("/")
    return when {
        parts.size >= 3 -> Triple(parts[0], parts[1], parts.drop(2).joinToString("/"))
        else -> Triple(bookInitials, null, null)
    }
}

/**
 * Gets a book by initials, handling processed book initials.
 * If the initials represent a processed book, returns the appropriate virtual book.
 */
fun getBookByInitials(bookInitials: String): Book? {
    // First try direct lookup
    Books.installed().getBook(bookInitials)?.let { return it }

    // Parse potential processed book initials
    val (originalInitials, processorId, params) = parseProcessedBookInitials(bookInitials)

    if (processorId != null && params != null) {
        // Get original book and create processed wrapper
        val originalBook = Books.installed().getBook(originalInitials)
        if (originalBook != null) {
            return getOrCreateProcessedBook(originalBook, processorId, params)
        }
    }

    return null
}
