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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedui.strings.keyed.StringTable

/**
 * The one "reading is ready" step both start paths share (cold start straight to READING, and Welcome -> Download ->
 * READING): initialise the app, run the first-run data preparation (idempotent through the data version; needs a usable
 * Bible, which both paths have by now), then build the window repository. All of it runs off the main thread, once per
 * process; the reading destination awaits [ensure] behind a spinner, the cold start awaits it behind the splash.
 */
@kotlin.native.HiddenFromObjC
internal object IosReadingReady {
    /** Process-lifetime scope the window repository, the reading host and the window sessions run on (main thread). */
    val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    private val mutex = Mutex()
    @kotlin.concurrent.Volatile private var ready = false

    suspend fun ensure(table: StringTable) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.Default) {
                val platform = IosReadingAppInitPlatform(table)
                ReadingAppInit.initialise(platform)
                ReadingAppInit.prepareData(platform)
                ReadingAppInit.createWindowRepository(scope)
            }
            ready = true
        }
    }

    internal fun resetForTest() { ready = false }
}
