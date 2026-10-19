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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedui.app.ai.PromptFiles
import net.bible.sharedui.app.ports.DocumentRef
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.mp.KoinPlatform
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURL
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeWithLocaleIdentifier

/** Security-scoped UTF-8 prompt I/O; default dependencies resolve when used, not during host construction. */
@kotlin.native.HiddenFromObjC
class IosPromptFiles(
    private val now: () -> NSDate = { NSDate() },
    private val timeZone: () -> NSTimeZone = { NSTimeZone.defaultTimeZone },
    private val modulesDirectory: () -> Path = { KoinPlatform.getKoin().get<AppDirectories>().modulesDir },
    private val addonCatalog: () -> AddonCatalog = { KoinPlatform.getKoin().get() },
    private val startAccess: (NSURL) -> Boolean = { it.startAccessingSecurityScopedResource() },
    private val stopAccess: (NSURL) -> Unit = { it.stopAccessingSecurityScopedResource() },
) : PromptFiles {
    override suspend fun read(ref: DocumentRef): String = withContext(Dispatchers.IO) {
        access(ref) { url -> readBytes(url).decodeToString() }
    }

    override suspend fun write(ref: DocumentRef, text: String) = withContext(Dispatchers.IO) {
        access(ref) { url ->
            if (!NSString.create(string = text).writeToURL(url, false, NSUTF8StringEncoding, null)) {
                throw IllegalArgumentException()
            }
        }
    }

    override suspend fun installAsAddon(ref: DocumentRef) = withContext(Dispatchers.IO) {
        access(ref) { url ->
            val files = FileSystem.SYSTEM
            val name = safeName(url.lastPathComponent)
            val directory = modulesDirectory() / "prompts"
            // Neither the folder nor an existing destination may redirect this sandbox write elsewhere.
            require(files.metadataOrNull(directory)?.symlinkTarget == null)
            files.createDirectories(directory)
            val destination = directory / name
            require(files.metadataOrNull(destination)?.symlinkTarget == null)
            // Read first so failure never truncates an existing pack, including importing the pack itself.
            val content = readBytes(url)
            files.write(destination) { write(content) }
            addCsvPromptBook(destination)
            addonCatalog().clearCaches()
        }
    }

    override fun suggestedExportName(): String {
        val timestamp = NSDateFormatter().apply {
            locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
            calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
            timeZone = this@IosPromptFiles.timeZone()
            dateFormat = "yyyy-MM-dd_HH-mm"
        }.stringFromDate(now())
        return "ai_prompts_$timestamp.csv"
    }

    private fun safeName(name: String?): String = name
        ?.replace(Regex("[/\\\\\\x00-\\x1f\\x7f]"), "_")
        ?.takeUnless { it.isBlank() || it == "." || it == ".." }
        ?: "prompts.csv"

    /** Foundation preserves literal POSIX backslashes that Okio would normalize into separators. */
    private fun readBytes(url: NSURL): ByteArray {
        val data = NSData.dataWithContentsOfURL(url) ?: throw IllegalArgumentException()
        require(data.length <= Int.MAX_VALUE.toULong())
        if (data.length == 0uL) return ByteArray(0)
        return data.bytes?.readBytes(data.length.toInt()) ?: throw IllegalArgumentException()
    }

    /** Sandbox URLs can deny scope and still work. Only release scopes actually acquired. */
    private inline fun <T> access(ref: DocumentRef, block: (NSURL) -> T): T {
        val url = NSURL.URLWithString(ref.uri)
            ?.takeIf { it.isFileURL() && it.path?.startsWith("/") == true }
            ?: throw IllegalArgumentException()
        val scoped = startAccess(url)
        return try {
            block(url)
        } catch (_: IOException) {
            // The shared caller supplies the localized CSV error; do not expose a hardcoded platform message.
            throw IllegalArgumentException()
        } finally {
            if (scoped) stopAccess(url)
        }
    }
}
