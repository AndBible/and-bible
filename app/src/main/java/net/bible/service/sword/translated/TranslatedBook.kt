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

package net.bible.service.sword.translated

import android.util.Log
import kotlinx.coroutines.runBlocking
import net.bible.service.llm.LlmTranslationService
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.Backend
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TranslatedBook"

/**
 * Backend state for translated books. Delegates to the wrapped book's backend.
 */
class TranslatedBackendState(
    val wrappedBook: SwordBook,
    val targetLanguage: String
) : OpenFileState {

    private var _lastAccess: Long = 0L

    override fun getBookMetaData(): SwordBookMetaData = wrappedBook.bookMetaData as SwordBookMetaData

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
 * Backend that wraps another SwordBook and provides LLM-translated content.
 *
 * This backend:
 * 1. First checks the translation cache for existing translations
 * 2. If not cached, fetches original content from wrapped book
 * 3. Translates via LLM API and caches the result
 */
class TranslatedBackend(
    private val state: TranslatedBackendState,
    metadata: SwordBookMetaData
) : AbstractKeyBackend<TranslatedBackendState>(metadata) {

    private val wrappedBackend: Backend<*> get() = state.wrappedBook.backend

    override fun initState(): TranslatedBackendState {
        Log.i(TAG, "initState for ${bookMetaData.initials}")
        return state
    }

    override fun getCardinality(): Int = wrappedBackend.cardinality

    override fun iterator(): MutableIterator<Key> = wrappedBackend.iterator()

    override fun get(index: Int): Key = wrappedBackend.get(index)

    override fun indexOf(that: Key): Int = wrappedBackend.indexOf(that)

    override fun readRawContent(state: TranslatedBackendState, key: Key): String {
        val originalInitials = state.wrappedBook.initials
        val keyName = key.osisRef

        // Check cache first
        val cacheResult = LlmTranslationService.getCached(
            originalInitials,
            keyName,
            state.targetLanguage
        )

        if (cacheResult.translatedXml != null) {
            Log.d(TAG, "Using cached translation for $originalInitials:$keyName -> ${state.targetLanguage}")
            return cacheResult.translatedXml
        }

        // Get original content from wrapped book
        val originalContent = state.wrappedBook.backend.readRawContent(key)

        // Translate and cache
        return runBlocking {
            LlmTranslationService.translateAndCache(
                originalInitials,
                keyName,
                originalContent,
                state.targetLanguage
            )
        }
    }
}

/**
 * Creates the metadata configuration for a translated book.
 */
private fun createTranslatedMetadata(
    wrappedBook: SwordBook,
    targetLanguage: String
): SwordBookMetaData {
    val originalMetadata = wrappedBook.bookMetaData as SwordBookMetaData
    val translatedInitials = "${wrappedBook.initials}/$targetLanguage"
    val languageName = Locale(targetLanguage).displayLanguage

    val conf = """
[$translatedInitials]
Description=${originalMetadata.name} (Translated to $languageName)
Abbreviation=${originalMetadata.abbreviation}/$targetLanguage
Category=${originalMetadata.bookCategory.name}
AndBibleTranslatedModule=1
AndBibleOriginalModule=${wrappedBook.initials}
AndBibleTargetLanguage=$targetLanguage
Lang=$targetLanguage
Version=0.0
Encoding=UTF-8
LCSH=Bible
SourceType=OSIS
ModDrv=zText
BlockType=BOOK
Versification=${wrappedBook.versification.name}
""".trim()

    return SwordBookMetaData(conf.toByteArray(), translatedInitials).also {
        it.driver = originalMetadata.driver
    }
}

/**
 * BookType for translated Bible books.
 */
val translatedBibleBookType = object : BookType("TranslatedBible", BookCategory.BIBLE, KeyType.VERSE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book {
        return SwordBook(sbmd, backend)
    }

    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        throw UnsupportedOperationException("Use createTranslatedBook instead")
    }
}

/**
 * Cache of translated books to avoid creating duplicates.
 * Key is "originalInitials/targetLanguage"
 */
private val translatedBooksCache = ConcurrentHashMap<String, SwordBook>()

/**
 * Gets or creates a virtual translated book for the given original book and target language.
 *
 * @param originalBook The original SwordBook to translate
 * @param targetLanguage The target language code (e.g., "fi", "en")
 * @return A virtual SwordBook that provides translated content
 */
fun getOrCreateTranslatedBook(originalBook: SwordBook, targetLanguage: String): SwordBook {
    val cacheKey = "${originalBook.initials}/$targetLanguage"

    return translatedBooksCache.getOrPut(cacheKey) {
        Log.i(TAG, "Creating translated book: $cacheKey")

        // Check if already registered in Books
        val existingBook = Books.installed().getBook(cacheKey)
        if (existingBook != null && existingBook is SwordBook) {
            Log.i(TAG, "Found existing translated book in Books.installed(): $cacheKey")
            return@getOrPut existingBook
        }

        // Create new translated book
        val metadata = createTranslatedMetadata(originalBook, targetLanguage)
        val state = TranslatedBackendState(originalBook, targetLanguage)
        val backend = TranslatedBackend(state, metadata)
        val translatedBook = SwordBook(metadata, backend)

        // Register with JSword's Books
        Books.installed().addBook(translatedBook)
        Log.i(TAG, "Registered translated book: $cacheKey")

        translatedBook
    }
}

/**
 * Extension property to check if a book is a translated wrapper.
 */
val Book.isTranslatedBook: Boolean
    get() = bookMetaData.getProperty("AndBibleTranslatedModule") != null

/**
 * Extension property to get the original book initials for a translated book.
 */
val Book.originalBookInitials: String?
    get() = bookMetaData.getProperty("AndBibleOriginalModule")

/**
 * Extension property to get the target language for a translated book.
 */
val Book.translationTargetLanguage: String?
    get() = bookMetaData.getProperty("AndBibleTargetLanguage")

/**
 * Parses a book initials string that may contain a translation suffix.
 * Returns a pair of (originalInitials, targetLanguage) where targetLanguage is null
 * if no translation suffix is present.
 *
 * Examples:
 * - "ESV2011" -> ("ESV2011", null)
 * - "ESV2011/fi" -> ("ESV2011", "fi")
 */
fun parseBookInitials(bookInitials: String): Pair<String, String?> {
    val slashIndex = bookInitials.indexOf('/')
    return if (slashIndex >= 0) {
        Pair(
            bookInitials.substring(0, slashIndex),
            bookInitials.substring(slashIndex + 1)
        )
    } else {
        Pair(bookInitials, null)
    }
}

/**
 * Gets a book by initials, handling translated book initials (e.g., "ESV2011/fi").
 * If the initials include a translation suffix, returns the appropriate translated book.
 */
fun getBookByInitials(bookInitials: String): Book? {
    // First try direct lookup
    Books.installed().getBook(bookInitials)?.let { return it }

    // Parse potential translation suffix
    val (originalInitials, targetLanguage) = parseBookInitials(bookInitials)

    if (targetLanguage != null) {
        // Get original book and create translated wrapper
        val originalBook = Books.installed().getBook(originalInitials) as? SwordBook
        if (originalBook != null) {
            return getOrCreateTranslatedBook(originalBook, targetLanguage)
        }
    }

    return null
}
