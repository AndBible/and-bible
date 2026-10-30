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

package net.bible.sharedui.assembly

import net.bible.sharedui.strings.keyed.StringTable

/** What the shared host code reads from the host's own startup object (iOS: `IosAppStartup`). */
@kotlin.native.HiddenFromObjC
interface HostStartupInfo {
    /** True once the host's startup has published its platform parts (a test reset clears it again). */
    val isStarted: Boolean

    /** True once the application databases are open. */
    val databasesOpen: Boolean

    /** The UI-language string table of the started app; throws [IllegalStateException] before the start completed. */
    val stringTable: StringTable
}

/**
 * Process-wide access to the host's [HostStartupInfo], for code that is a singleton and cannot be handed it.
 * The host's startup object calls [register] from its initializer. Until then nothing can have started, so the
 * unregistered answers equal the not-started answers: [isStarted] and [databasesOpen] are false and [stringTable]
 * throws, which makes reading before registration indistinguishable from reading before startup.
 */
@kotlin.native.HiddenFromObjC
object HostStartup : HostStartupInfo {
    @kotlin.concurrent.Volatile
    private var delegate: HostStartupInfo? = null

    /** Installs the host's startup info; null removes it (tests). */
    fun register(info: HostStartupInfo?) { delegate = info }

    override val isStarted: Boolean get() = delegate?.isStarted ?: false
    override val databasesOpen: Boolean get() = delegate?.databasesOpen ?: false
    override val stringTable: StringTable
        get() = delegate?.stringTable ?: error("Host startup has not completed")
}
