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
 * off-screen ([Osr], the default) paints into a Compose canvas, so Compose popups and dialogs stay in-window; windowed is a
 * heavyweight CEF component in a \`SwingPanel\` (the fallback; it needs Compose layers as own windows to show above the pane).
 */
internal enum class KcefRenderingMode(val windowless: Boolean) {
    Windowed(false),
    Osr(true),
    ;

    companion object {
        /** System property that selects the mode: \`osr\` (default) or \`windowed\`. */
        const val PROPERTY = "andbible.kcef.rendering"

        /** The mode named by [value]: null or \`osr\` is [Osr], \`windowed\` is [Windowed]; anything else is [Osr] with one [log] line. */
        fun parse(value: String?, log: (String) -> Unit): KcefRenderingMode = when (value) {
            null, "osr" -> Osr
            "windowed" -> Windowed
            else -> Osr.also { log("unknown $PROPERTY=\"$value\" (expected osr or windowed): using osr") }
        }

        private var resolved: KcefRenderingMode? = null

        /** The process-wide mode from the system property, parsed on first use; an unknown value is logged to [log] once. */
        @Synchronized
        fun current(log: (String) -> Unit = ::println): KcefRenderingMode =
            resolved ?: parse(System.getProperty(PROPERTY), log).also { resolved = it }
    }
}
