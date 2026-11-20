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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.widget.LocalZone
import net.bible.android.control.widget.SpeakWidgetStateProducer
import net.bible.android.control.widget.VerseCandidates
import net.bible.android.control.widget.WidgetSnapshot
import net.bible.android.control.widget.WidgetSnapshotPublisher
import net.bible.android.control.widget.WidgetStrings
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.log.Log
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/**
 * Writes the widget snapshot into the App Group container whenever the speak state, the speak bookmarks or the verse
 * sources change, and reloads the widget timelines ([IosWidgetBridge.reloadTimelines]). The logic (pool caching,
 * throttling, change detection) is [WidgetSnapshotPublisher]; this class supplies the iOS pieces.
 *
 * Start it with [start] once startup is Ready (phase 2 of the startup join: the databases and a default Bible exist),
 * never earlier; see the `ios-startup-is-databases-only-until-phase-2` memory.
 */
class IosWidgetSnapshotWriter(
    private val producer: SpeakWidgetStateProducer,
    private val candidates: VerseCandidates,
    private val strings: WidgetStrings,
    private val bookmarkControl: BookmarkControl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val subscriptions = Subscriptions()
    private var subscribed = false
    private val json = Json { encodeDefaults = true }

    private val publisher = WidgetSnapshotPublisher(
        scope = scope,
        state = producer.state,
        candidates = candidates,
        strings = strings,
        requestedBibles = IosWidgetBridge::requestedBibles,
        zone = LocalZone { epochMs -> NSTimeZone.defaultTimeZone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)) * 1000L },
        nowMs = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
        poolInvalidations = invalidations.asSharedFlow(),
        write = ::write,
        reload = { IosWidgetBridge.reloadTimelines?.invoke() },
    )

    /** Starts the speak state producer and the publisher; idempotent. */
    fun start() {
        if (!subscribed) {
            subscribed = true
            // A label or bookmark change can change a pool; the publisher rebuilds the pools on the next write.
            subscriptions.add(bookmarkControl.changes.subscribe { invalidations.tryEmit(Unit) })
        }
        producer.start()
        publisher.start()
    }

    /** Publishes now (the app came to the foreground, or the extension asked for another Bible). */
    fun writeNow() = publisher.writeNow()

    /** The language changed: texts in the producer state and the snapshot strings are rebuilt. */
    fun refreshForLocale() {
        producer.refreshForLocale()
        publisher.writeNow()
    }

    fun close() {
        publisher.stop()
        producer.stop()
        subscriptions.cancelAll()
        subscribed = false
        scope.cancel()
    }

    private fun write(snapshot: WidgetSnapshot) {
        val ok = IosWidgetBridge.writeSnapshot(json.encodeToString(WidgetSnapshot.serializer(), snapshot))
        if (!ok) Log.w("IosWidgetSnapshotWriter", "snapshot not written: App Group container unavailable")
    }
}
