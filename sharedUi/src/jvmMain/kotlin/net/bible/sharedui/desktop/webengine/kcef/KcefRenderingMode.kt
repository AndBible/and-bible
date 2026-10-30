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
package net.bible.sharedui.desktop.webengine.kcef

/**
 * How the pane's browser is rendered. Read once per process (CEF initialises once, and its windowless setting is global):
 * windowed is a heavyweight CEF component in a \`SwingPanel\`, off-screen ([Osr]) paints into a Compose canvas.
 */
internal enum class KcefRenderingMode(val windowless: Boolean) {
    Windowed(false),
    Osr(true),
    ;

    companion object {
        /** System property that selects the mode: \`osr\` or \`windowed\` (default). */
        const val PROPERTY = "andbible.kcef.rendering"

        /** The mode named by [value]: null or \`windowed\` is [Windowed], \`osr\` is [Osr]; anything else is [Windowed] with one [log] line. */
        fun parse(value: String?, log: (String) -> Unit): KcefRenderingMode = when (value) {
            null, "windowed" -> Windowed
            "osr" -> Osr
            else -> Windowed.also { log("unknown $PROPERTY=\"$value\" (expected osr or windowed): using windowed") }
        }

        private var resolved: KcefRenderingMode? = null

        /** The process-wide mode from the system property, parsed on first use; an unknown value is logged to [log] once. */
        @Synchronized
        fun current(log: (String) -> Unit = ::println): KcefRenderingMode =
            resolved ?: parse(System.getProperty(PROPERTY), log).also { resolved = it }
    }
}
