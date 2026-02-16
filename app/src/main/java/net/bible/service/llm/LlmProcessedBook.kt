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
import net.bible.service.common.CommonUtils
import net.bible.android.database.IdType
import net.bible.service.llm.processors.PromptProcessor
import net.bible.service.llm.processors.TranslationProcessor
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.processing.RawTextToXmlProcessor
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import org.jdom2.Content
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LlmProcessedBook"

/**
 * Backend state for LLM-processed books. Wraps any Book type.
 */
class LlmProcessedBackendState(
    val wrappedBook: Book,
    val processor: LlmProcessor,
    val processingParams: String,
    private val ownMetadata: SwordBookMetaData,
    val modelOverride: String? = null
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

    /**
     * This method is not used for VERSE-type books since we override readToOsis.
     * It's only called if someone calls getRawText directly on the backend.
     */
    override fun readRawContent(state: LlmProcessedBackendState, key: Key): String {
        val originalInitials = state.wrappedBook.initials
        val keyName = key.osisRef
        val effectiveModel = state.modelOverride?.takeIf { it.isNotBlank() } ?: CommonUtils.settings.llmModel
        val cacheKey = state.processor.getCacheKey(originalInitials, keyName, state.processingParams, effectiveModel)

        val cacheResult = LlmProcessingService.getCached(cacheKey)
        if (cacheResult.processedXml != null) {
            return cacheResult.processedXml
        }

        // Try chapter-level cache fallback
        val chapterCached = LlmProcessingService.getCachedChapter(cacheKey)
        if (chapterCached.processedXml != null) {
            return chapterCached.processedXml
        }

        // Get OSIS XML for the key
        val bookData = BookData(state.wrappedBook, key)
        val osisElement = bookData.getOsisFragment(false)
        val originalXml = XMLOutputter(Format.getRawFormat()).outputString(osisElement)

        return runBlocking {
            LlmProcessingService.processWithTools(state.processor, cacheKey, originalXml, modelOverride = state.modelOverride)
        }
    }

    /**
     * Override readToOsis to process the entire key (chapter) at once.
     *
     * Instead of splitting into individual verses and processing each separately,
     * this sends the whole chapter to the LLM with tool access (e.g., getVerseContent).
     * This allows the LLM to reference other documents and produces better results
     * with fewer API calls (2-3 per chapter vs 100-200 per-verse).
     */
    override fun readToOsis(key: Key, processor: RawTextToXmlProcessor): MutableList<Content> {
        val originalInitials = state.wrappedBook.initials
        val keyName = key.osisRef
        Log.d(TAG, "readToOsis for $originalInitials key=$keyName")

        val effectiveModel = state.modelOverride?.takeIf { it.isNotBlank() } ?: CommonUtils.settings.llmModel
        val cacheKey = state.processor.getCacheKey(originalInitials, keyName, state.processingParams, effectiveModel)

        // Check cache first (exact key, then chapter-level fallback)
        val cached = LlmProcessingService.getCached(cacheKey)
        if (cached.processedXml != null) {
            Log.d(TAG, "Cache hit for $keyName")
            return parseXmlToContentList(cached.processedXml, key, processor)
        }

        // Try chapter-level cache (e.g., verse "Isa.65.19" → chapter "Isa.65")
        val chapterCached = LlmProcessingService.getCachedChapter(cacheKey)
        if (chapterCached.processedXml != null) {
            Log.d(TAG, "Chapter cache hit for $keyName")
            return parseXmlToContentList(chapterCached.processedXml, key, processor)
        }

        // Get OSIS XML for the entire key (chapter)
        val bookData = BookData(state.wrappedBook, key)
        val osisElement = bookData.getOsisFragment(false)
        val originalXml = XMLOutputter(Format.getRawFormat()).outputString(osisElement)

        Log.d(TAG, "Processing chapter $keyName (${originalXml.length} chars)")

        // Process with tool support
        val processedXml = runBlocking {
            LlmProcessingService.processWithTools(state.processor, cacheKey, originalXml, modelOverride = state.modelOverride)
        }

        return parseXmlToContentList(processedXml, key, processor)
    }

    /**
     * Parse processed XML string into a list of JDOM Content elements.
     *
     * @param xml The processed XML string
     * @param fallbackKey The original key, used for fallback if parsing fails
     * @param processor The XML processor, used for fallback
     * @return Mutable list of Content elements
     */
    private fun parseXmlToContentList(
        xml: String,
        fallbackKey: Key,
        processor: RawTextToXmlProcessor
    ): MutableList<Content> {
        val content = mutableListOf<Content>()
        try {
            val builder = SAXBuilder()
            try {
                builder.setFeature("http://xml.org/sax/features/external-general-entities", false)
                builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (e: Exception) {
                // Android SAX parser may not support these features
            }
            val doc = builder.build(StringReader(xml))
            val root = doc.rootElement

            // The processed XML may be a single element or have child elements
            // Add the root element and all its content
            content.add(root.clone())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse processed XML for ${fallbackKey.osisRef}: ${e.message}")
            // Fallback: get original content for each sub-key
            for (subKey in fallbackKey) {
                val originalContent = state.wrappedBook.getRawText(subKey)
                processor.postVerse(subKey, content, originalContent)
            }
        }
        return content
    }
}

/**
 * Creates the metadata configuration for an LLM-processed book.
 */
private fun createProcessedMetadata(
    wrappedBook: Book,
    processor: LlmProcessor,
    processingParams: String,
    modelOverride: String? = null
): SwordBookMetaData {
    val originalMetadata = wrappedBook.bookMetaData as SwordBookMetaData
    val processedInitials = "${wrappedBook.initials}/${processor.processorId}/$processingParams"
    val description = processor.getDescription(processingParams)

    val conf = """
[$processedInitials]
Description=${originalMetadata.name} ($description)
Abbreviation=${originalMetadata.abbreviation}/${processor.processorId}/$processingParams
Category=${originalMetadata.bookCategory.getName()}
AndBibleLlmProcessedModule=1
AndBibleOriginalModule=${wrappedBook.initials}
AndBibleProcessorId=${processor.processorId}
AndBibleProcessingParams=$processingParams${if (modelOverride != null) "\nAndBibleModelOverride=$modelOverride" else ""}
Lang=${if (processor.processorId == "translations") processingParams else originalMetadata.language.code}
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Feature=StrongsNumbers
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
fun getOrCreateProcessedBook(originalBook: Book, processorId: String, processingParams: String, modelOverride: String? = null): Book? {
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
        val metadata = createProcessedMetadata(originalBook, processor, processingParams, modelOverride)
        val state = LlmProcessedBackendState(originalBook, processor, processingParams, metadata, modelOverride)
        val backend = LlmProcessedBackend(state, metadata)

        // Use SwordBook for all types - it's a generic wrapper that delegates to our backend.
        // The backend fetches content from the wrapped book (which can be any type).
        val processedBook = SwordBook(metadata, backend)

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
 * Creates a processed book using an AgentPrompt from the database.
 *
 * @param originalBook The original Book to process
 * @param promptId The ID of the AgentPrompt to use
 * @return A virtual Book that provides processed content, or null if prompt not found
 */
fun getOrCreateProcessedBookWithPrompt(originalBook: Book, promptId: IdType): Book? {
    val prompt = PromptRepository.promptById(promptId) ?: run {
        Log.e(TAG, "AgentPrompt not found: $promptId")
        return null
    }

    // Use PromptProcessor with the prompt ID as params
    val processor = PromptProcessor
    return getOrCreateProcessedBook(originalBook, processor.processorId, promptId.toString(), prompt.modelOverride)
}

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
 * Extension property to get the model override for a processed book.
 */
val Book.llmModelOverride: String?
    get() = bookMetaData.getProperty("AndBibleModelOverride")

/**
 * Resolve the effective model ID for a processed book.
 * Uses the per-prompt model override if set, otherwise falls back to the global setting.
 */
val Book.llmEffectiveModel: String
    get() = llmModelOverride?.takeIf { it.isNotBlank() } ?: CommonUtils.settings.llmModel

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
