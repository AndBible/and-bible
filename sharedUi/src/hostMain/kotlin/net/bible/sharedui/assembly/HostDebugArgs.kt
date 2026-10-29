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

/**
 * Debug-only launch arguments the shared host code reads. Every member has a neutral default, so a host
 * without debug launch arguments (desktop) uses `object : HostDebugArgs {}`. iOS implements it with
 * `IosDebugLaunchArgs` (`-installCommitDelayMs` etc.), which only answers non-neutral values in debug binaries.
 */
@kotlin.native.HiddenFromObjC
interface HostDebugArgs {
    /**
     * Milliseconds the module-install commit sleeps after each extracted archive entry, so a UI test can kill the
     * process mid-extraction. 0 means no delay.
     */
    fun installCommitDelayMs(): Long = 0L
}
