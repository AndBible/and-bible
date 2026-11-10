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

private fun safeFontPath(path: String): Boolean = path.isNotEmpty() && !path.startsWith('/') &&
    '\\' !in path && ':' !in path && '\u0000' !in path && path.split('/').none { it == "." || it == ".." }

/**
 * Platform-free reading of the add-on metadata that `AndBibleAddons` (`:app`, `java.io.File` API) reads on
 * Android: reading plans, prompts, background images and `AndBibleProvidesFont`. Plan keys are file names without
 * their last extension, later books win, and date-based flags compare to `True` ignoring case. Fonts are listed
 * only for regular files inside their module (including symlink resolution); malformed font markers are ignored.
 * `AndBibleAddons` retains Android's `File` API; iOS and desktop use this scanner.
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

    /** `File(parent, child)` on Android drops leading separators of a relative-looking child; do the same. */
    private fun Path.child(name: String): Path = this / name.trimStart('/')

    /** Live font metadata, shared by the chooser and BibleView's generated stylesheet. Later families win. */
    fun fonts(): List<net.bible.sharedlogic.platform.AddonFont> {
        val byName = linkedMapOf<String, net.bible.sharedlogic.platform.AddonFont>()
        for (book in addons) {
            val base = book.base() ?: continue
            for (marker in book.bookMetaData.getValues("AndBibleProvidesFont").orEmpty()) {
                val parts = marker.split(';', limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || !safeFontPath(parts[1])) continue
                val file = base / parts[1]
                val realBase = try { files.canonicalize(base) } catch (_: Exception) { continue }
                val real = try { files.canonicalize(file) } catch (_: Exception) { continue }
                if (real.root != realBase.root || real.segments.size <= realBase.segments.size ||
                    real.segments.take(realBase.segments.size) != realBase.segments ||
                    files.metadataOrNull(real)?.isRegularFile != true) continue
                byName[parts[0]] = net.bible.sharedlogic.platform.AddonFont(book.initials, parts[0], parts[1], real.toString())
            }
        }
        return byName.values.toList()
    }

    fun readingPlans(): Map<String, AddonReadingPlan> {
        val byCode = LinkedHashMap<String, AddonReadingPlan>()
        for (book in addons) {
            val values = book.bookMetaData.getValues("AndBibleProvidesReadingPlan") ?: continue
            val base = book.base() ?: continue
            val dateBased = book.bookMetaData.getProperty("AndBibleReadingPlanDateBased")?.equals("True", ignoreCase = true) == true
            for (fileName in values) {
                val path = base.child(fileName)
                byCode[path.name.substringBeforeLast('.', path.name)] = AddonReadingPlan(book, fileName, dateBased, path)
            }
        }
        return byCode
    }

    /** Same first-marker, module-initials lookup as Android's providedBackgroundImages. */
    fun backgroundImages(): Map<String, net.bible.sharedlogic.platform.AddonBackgroundImage> = buildMap {
        for (book in addons) {
            val marker = book.bookMetaData.getValues("AndBibleProvidesBackgroundImage")?.firstOrNull() ?: continue
            val parts = marker.split(';')
            val path = (book.base() ?: continue).child(parts.getOrElse(1) { "" })
            if (files.metadataOrNull(path)?.isRegularFile == true) {
                put(book.initials, net.bible.sharedlogic.platform.AddonBackgroundImage(parts[0], path))
            }
        }
    }

    fun promptPacks(): List<PromptPackRef> {
        val packs = mutableListOf<PromptPackRef>()
        for (book in addons) {
            val csvPath = book.bookMetaData.getProperty("AndBibleProvidesPrompts") ?: continue
            val file = (book.base() ?: continue).child(csvPath)
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
 * [promptPacks]`.reloaded`, as `AndBibleAddons.clearCaches` always does (hence not optional).
 */
class SharedAddonCatalog(
    private val scanner: SharedAddonScanner,
    private val promptPacks: SharedPromptPackSource,
) : AddonCatalog, net.bible.sharedlogic.platform.BackgroundImageCatalog {
    override val backgroundImages get() = scanner.backgroundImages()

    private val clearListeners = mutableListOf<() -> Unit>()

    /** Runs [listener] after every [clearCaches] (an installed module changed or was deleted): holders of derived data drop it. */
    fun addClearListener(listener: () -> Unit) { clearListeners.add(listener) }

    override fun clearCaches() {
        scanner.clearCaches()
        promptPacks.notifyReloaded()
        clearListeners.toList().forEach { it() }
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
