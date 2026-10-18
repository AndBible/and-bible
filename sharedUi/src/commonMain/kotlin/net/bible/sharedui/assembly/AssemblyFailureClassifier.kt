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
package net.bible.sharedui.assembly

/** How the debug assembly probe files an unresolvable Koin definition. */
enum class AssemblyFailureKind {
    /** Needs Stream D's sync ports or an initialised workspace: expected in production phase 1, joins in phase 2. */
    PHASE_2_DEPENDENT,

    /** Anything else: a real assembly bug. */
    UNEXPECTED,
}

/**
 * Pure classification of a resolution failure from the text of its whole cause chain (every layer's message, joined).
 * A failure is a phase 2 dependent when the chain names a Stream D port type ([SYNC_PORT_TYPES]) or shows the
 * uninitialised workspace: an empty window list (`index: 0, size: 0`) that only counts when the chain names
 * [WORKSPACE_DEPENDENT_TYPES] (`TextToSpeechServiceManager` reads the first window while it is built). The same
 * message under any other definition is a real bug. Nothing else is excused.
 */
fun classifyAssemblyFailure(causeChainText: String): AssemblyFailureKind {
    val ports = SYNC_PORT_TYPES.mapNotNull { it.qualifiedName }
    val emptyWorkspace = causeChainText.contains("index: 0, size: 0") &&
        WORKSPACE_DEPENDENT_TYPES.any { causeChainText.contains(it) }
    return if (ports.any { causeChainText.contains(it) } || emptyWorkspace) {
        AssemblyFailureKind.PHASE_2_DEPENDENT
    } else {
        AssemblyFailureKind.UNEXPECTED
    }
}

/** Definitions verified to fail with an empty-list message while the workspace is not initialised (phase 2 join). */
private val WORKSPACE_DEPENDENT_TYPES = listOf("TextToSpeechServiceManager")
