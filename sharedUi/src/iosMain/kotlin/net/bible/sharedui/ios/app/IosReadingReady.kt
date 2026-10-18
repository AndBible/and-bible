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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.service.startup.ReadingAppInit
import net.bible.service.startup.ReadingAppInitPlatform
import net.bible.sharedui.strings.keyed.StringTable

/**
 * The one "reading is ready" step both start paths share (cold start straight to READING, and Welcome -> Download ->
 * READING): initialise the app, run the first-run data preparation (idempotent through the data version; needs a usable
 * Bible, which both paths have by now), then build the window repository. All of it runs off the main thread, once per
 * process; the reading destination awaits [ensure] behind a spinner, the cold start awaits it behind the splash.
 */
@kotlin.native.HiddenFromObjC
internal object IosReadingReady {
    private var currentScope: CoroutineScope? = null

    /**
     * Process-lifetime scope the window repository, the reading host and the window sessions run on (main thread).
     * Valid once [ensure] has completed; each attempt of [ensure] uses a fresh scope, so a retry after a failure
     * builds a new repository instead of reusing a half-loaded one.
     */
    val scope: CoroutineScope get() = checkNotNull(currentScope) { "IosReadingReady.ensure has not completed" }

    private val mutex = Mutex()
    @kotlin.concurrent.Volatile private var ready = false

    // Test seams: the three steps, so a test can see their order and count.
    internal var initialise: suspend (ReadingAppInitPlatform) -> Unit = { ReadingAppInit.initialise(it) }
    internal var prepareData: suspend (ReadingAppInitPlatform) -> Unit = { ReadingAppInit.prepareData(it) }
    internal var createRepository: (CoroutineScope) -> Unit = { ReadingAppInit.createWindowRepository(it) }

    suspend fun ensure(table: StringTable) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            val attempt = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            try {
                withContext(Dispatchers.Default) {
                    val platform = IosReadingAppInitPlatform(table)
                    initialise(platform)
                    prepareData(platform)
                    createRepository(attempt)
                }
            } catch (t: Throwable) {
                attempt.cancel()
                throw t
            }
            currentScope = attempt
            ready = true
        }
    }

    internal fun resetForTest() {
        ready = false
        currentScope?.cancel()
        currentScope = null
        initialise = { ReadingAppInit.initialise(it) }
        prepareData = { ReadingAppInit.prepareData(it) }
        createRepository = { ReadingAppInit.createWindowRepository(it) }
    }
}
