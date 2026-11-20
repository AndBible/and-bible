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
package net.bible.sharedui.platform.widget

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.bible.sharedcore.log.Log
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL

/**
 * The seam between the app and the WidgetKit extension on the Swift side: the App Group container the snapshot lives
 * in, and the callback that reloads the widget timelines (`iOSApp.swift` sets it to
 * `WidgetCenter.shared.reloadAllTimelines`).
 */
object IosWidgetBridge {
    const val SNAPSHOT_FILE = "widget-snapshot.json"
    const val REQUESTS_FILE = "widget-requests.json"
    private const val TAG = "IosWidgetBridge"

    /** Set by the Swift host; null in tests and before launch. */
    var reloadTimelines: (() -> Unit)? = null

    /** The App Group id the Info.plist names (`AppGroupIdentifier`, set per configuration by `project.yml`). */
    fun appGroupId(): String? = NSBundle.mainBundle.objectForInfoDictionaryKey("AppGroupIdentifier") as? String

    @OptIn(ExperimentalForeignApi::class)
    internal fun containerUrl(): NSURL? =
        appGroupId()?.let { NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(it) }

    /** The Bibles the extension asked for (`widget-requests.json`, written by the extension's configuration). */
    fun requestedBibles(): Set<String> {
        val url = containerUrl()?.URLByAppendingPathComponent(REQUESTS_FILE) ?: return emptySet()
        val data = NSData.create(contentsOfURL = url) ?: return emptySet()
        val text = NSString.create(data, NSUTF8StringEncoding)?.toString() ?: return emptySet()
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<WidgetRequests>(text).bibles.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "unreadable $REQUESTS_FILE", e)
            emptySet()
        }
    }

    /** Atomically replaces the snapshot file; false when the container is unavailable or the write failed. */
    internal fun writeSnapshot(json: String): Boolean {
        val url = containerUrl()?.URLByAppendingPathComponent(SNAPSHOT_FILE) ?: return false
        val data = (json as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        return data.writeToURL(url, atomically = true)
    }

    @Serializable
    private data class WidgetRequests(val bibles: List<String> = emptyList())
}
