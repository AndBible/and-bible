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
package net.bible.sharedlogic.addons

import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.log.Log
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.platform.AddonReadingPlan
import net.bible.sharedlogic.platform.PromptPackRef
import net.bible.sharedlogic.platform.PromptPackSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Book

private const val TAG = "SharedAddonScanner"

/**
 * Platform-free reading of the add-on metadata that `AndBibleAddons` (`:app`, `java.io.File` API) reads on
 * Android: `AndBibleProvidesReadingPlan`, `AndBibleReadingPlanDateBased` and `AndBibleProvidesPrompts`, with
 * the same semantics (plan key = file name without its last extension, later books win, date-based flag is the
 * book-level property compared to `True` ignoring case, a prompt pack is listed only when its file is readable).
 * `AndBibleAddons` itself is not moved because Streams A and B use its `File` API; the oracle test in `:app`
 * compares the two on the same books. Bound on iOS only.
 *
 * [books] returns the installed add-on books (the platform applies `AndBibleAddonFilter`); the result is cached
 * until [clearCaches], as `AndBibleAddons` caches it. Deviation: a book without a location is skipped (Android
 * would throw on `File(null)`).
 */
class SharedAddonScanner(
    private val books: () -> List<Book>,
    private val files: FileSystem = FileSystem.SYSTEM,
) {
    @kotlin.concurrent.Volatile private var cached: List<Book>? = null

    private val addons: List<Book> get() = cached ?: books().also { cached = it }

    /** Drops the cached book list. */
    fun clearCaches() {
        cached = null
    }

    private fun Book.base(): Path? = bookMetaData.location?.getPath()?.toPath()

    fun readingPlans(): Map<String, AddonReadingPlan> {
        val byCode = LinkedHashMap<String, AddonReadingPlan>()
        for (book in addons) {
            val values = book.bookMetaData.getValues("AndBibleProvidesReadingPlan") ?: continue
            val base = book.base() ?: continue
            val dateBased = book.bookMetaData.getProperty("AndBibleReadingPlanDateBased")?.equals("True", ignoreCase = true) == true
            for (fileName in values) {
                val path = base / fileName
                byCode[path.name.substringBeforeLast('.', path.name)] = AddonReadingPlan(book, fileName, dateBased, path)
            }
        }
        return byCode
    }

    fun promptPacks(): List<PromptPackRef> {
        val packs = mutableListOf<PromptPackRef>()
        for (book in addons) {
            val csvPath = book.bookMetaData.getProperty("AndBibleProvidesPrompts") ?: continue
            val file = (book.base() ?: continue) / csvPath
            if (files.metadataOrNull(file)?.isRegularFile == true) {
                packs.add(PromptPackRef(book.initials, file.toString()))
            } else {
                Log.e(TAG, "Could not read prompt pack file $file for module ${book.initials}")
            }
        }
        return packs
    }
}

/**
 * [AddonCatalog] over a [SharedAddonScanner]. [clearCaches] drops the scanner cache and then fires
 * [SharedPromptPackSource.reloaded] when a [promptPacks] source is given.
 */
class SharedAddonCatalog(
    private val scanner: SharedAddonScanner,
    private val promptPacks: SharedPromptPackSource? = null,
) : AddonCatalog {
    override fun clearCaches() {
        scanner.clearCaches()
        promptPacks?.notifyReloaded()
    }

    override val readingPlans: Map<String, AddonReadingPlan> get() = scanner.readingPlans()
}

/** [PromptPackSource] over a [SharedAddonScanner]; [reloaded] is its own event, fired by [SharedAddonCatalog.clearCaches]. */
class SharedPromptPackSource(private val scanner: SharedAddonScanner) : PromptPackSource {
    private val reloadedSource = EventSource<Unit>()

    override val packs: List<PromptPackRef> get() = scanner.promptPacks()
    override val reloaded: Events<Unit> get() = reloadedSource

    internal fun notifyReloaded() = reloadedSource.emit(Unit)
}
