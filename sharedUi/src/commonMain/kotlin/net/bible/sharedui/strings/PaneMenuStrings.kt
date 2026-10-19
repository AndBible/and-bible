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

package net.bible.sharedui.strings

/**
 * The labels of the window (☰) pane menu ([net.bible.android.view.activity.page.WindowPaneMenuStateBuilder]), one member
 * per Android string resource the menu used to read with `getString`. Android implements it over `R.string`
 * (`AndroidPaneMenuStrings`); iOS over the generated tables ([net.bible.sharedui.strings.keyed.KeyedPaneMenuStrings]),
 * both by the same resource key, which the strings test pins against `strings.xml`.
 */
@kotlin.native.HiddenFromObjC
interface PaneMenuStrings {
    val newWindow: String
    val windowMaximise: String
    val windowMinimise: String
    val changeToNormal: String
    val moveWindow: String
    val windowPinMode: String
    val windowSynchronise: String
    val addWholePageBookmark: String
    val textOptionsWindowMenuTitle: String
    val allTextOptionsWindowMenuTitle: String
    val copySettings: String
    val copyReference: String
    val close: String
    val disableSync: String
    val studypad: String
    val llmActions: String
    val copySettingsToWorkspace: String
    val copySettingsToGlobal: String
    /** Shown after the copy-reference action (a toast on iOS; Android toasts it itself). */
    val referenceCopiedToClipboard: String

    fun exportFileFormat(format: String): String
    fun exportSomething(what: String): String
    fun exportBookmarksCsv(format: String): String
    fun goToRef(name: String?): String
    /** One-based [position], the target's document abbreviation and key name. */
    fun moveWindowToPosition(position: Int, abbreviation: String?, key: String?): String
    fun syncGroupN(n: Int): String
    fun copySettingsToWindow(position: Int, abbreviation: String?, key: String?): String
}
