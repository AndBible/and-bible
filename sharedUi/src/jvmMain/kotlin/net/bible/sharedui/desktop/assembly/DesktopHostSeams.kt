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
package net.bible.sharedui.desktop.assembly

import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.app.ports.DocumentRef
import okio.Sink

/**
 * Small host seams that the hoisted iOS classes take as function parameters and that have an owner in wave 3. They are
 * ports bound in Koin so that [buildDesktopHostPorts] stays frozen: the owning stream file binds the real one.
 */
interface DesktopBookmarkCsvFiles {
    /** A sink for the CSV export to [ref], null when it cannot be opened. */
    fun openSink(ref: DocumentRef): Sink?

    /** The file name stamp of a bookmark export, e.g. `2026-10-29_12-30`. */
    fun timestamp(): String
}

/** The bug-report flow of the reading drawer ("Report a bug"). Owner: D3d. */
fun interface DesktopBugReport {
    suspend fun report(cause: Throwable?)
}

/**
 * The command-line arguments `main(args)` received (files to open, for example), bound in Koin by the desktop platform
 * module. Read by plan D3b's [DesktopIncomingFiles]; today nobody reads them.
 */
class DesktopLaunchArgs(val args: List<String>)

/**
 * Files handed to the app from outside ("open with", command-line files, a second launch): what
 * `DesktopAppHostPlatform.receiveIncomingFiles` delegates to, with the same contract as
 * `AppHostPlatform.receiveIncomingFiles` (deliver each batch through [open]; runs until cancelled).
 *
 * Owner: plan D3b, bound in `DesktopDocumentBindings`. Default = today's behaviour: no incoming files, suspends until
 * cancelled.
 */
fun interface DesktopIncomingFiles {
    suspend fun receive(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit)
}

/**
 * Sharing a file from a reading pane (the "share as HTML" of the page): [canShare] false makes the pane skip the share
 * (it is also false while a share is in progress), [shareFile] hands the written file at `path` over.
 *
 * Owner: plan D3b, bound in `DesktopDocumentBindings` (it may build it over `FileDelivery`). Default = today's
 * behaviour: no file share ([canShare] false).
 */
interface DesktopFileShare {
    val canShare: Boolean
    fun shareFile(path: String)
}

/**
 * The desktop night-mode answer: the [NightModeState] of the graph plus what the toolbar and the reading panes need
 * besides it. [manual] is whether the stored mode is "manual" (the toolbar then shows its night toggle);
 * [observeChanges] calls back (any thread) whenever [nightMode] or [manual] may have changed, so the reading panes
 * re-send their config; a system-appearance source fires it too.
 *
 * Owner: plan D3d, bound in `DesktopMiscBindings` (it adds the OS appearance). Default = today's behaviour:
 * `DesktopNightModeStub` over the device settings' two night-mode keys, "system" meaning light.
 */
interface DesktopNightModeSource : NightModeState {
    val manual: Boolean
    fun observeChanges(onChange: () -> Unit): () -> Unit
}

/**
 * The crash-recovery gate before the startup, read by `main()` before Koin exists: [recoveryRequired] puts the gate up,
 * [reportBug] is the gate screen's report action. Built by `desktopCrashGate(paths)` in `DesktopMiscBindings.kt`.
 *
 * Owner: plan D3d (its crash tracker). Default = today's behaviour: never required, reporting does nothing.
 */
class DesktopCrashGate(
    val recoveryRequired: () -> Boolean = { false },
    val reportBug: suspend () -> Unit = {},
    val onStartupReady: () -> Unit = {},
    val onCleanExit: () -> Unit = {},
)
