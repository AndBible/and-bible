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

package net.bible.sharedui.ios.reading

import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextShare
import okio.Sink

/**
 * The platform hooks of the reading host ([IosReadingHost]) and its window assembly ([IosReadingWindows]): the system
 * calls (URL opening, status bar, layout direction, screen, background tasks, pasteboard) and the platform objects
 * (pickers, share, device preferences) the host builds. The host calls each hook exactly where and when it used to make
 * the platform call itself: a `new*` hook is called once per object the host used to construct, so instance
 * identity and the timing of construction are the caller's, not the platform's.
 */
@kotlin.native.HiddenFromObjC
interface ReadingHostPlatform {
    /** Opens [url] with the system (the default of the host's `openUrl`). */
    fun openUrl(url: String)

    /** The URL of the app's page in the system settings (the Speak sheet's system-settings row). */
    val settingsUrl: String

    /** Hides or shows the system status bar (full screen hides it). */
    fun setStatusBarHidden(hidden: Boolean)

    /** True when the screen is at least as tall as it is wide; read per call. */
    fun isPortrait(): Boolean

    /** True when the app's UI is laid out right to left; read per swipe. */
    fun isRightToLeft(): Boolean

    /** Device pixels per point of the screen. */
    fun screenScale(): Float

    /** The app's version name, shown in the toolbar host's About rows. */
    fun versionName(): String

    /** Whether the device has the motion sensor tilt-to-scroll needs. */
    fun tiltAvailable(): Boolean

    /** The history list's timestamp for [epochMillis] (local time zone, `h:mm a, E d MMM `). */
    fun formatHistoryTimestamp(epochMillis: Long): String

    /** Puts [text] on the system clipboard (the selection menu's copy action). */
    fun copyToPasteboard(text: String)

    /** Asks the system for background time for an agent run; [onExpire] runs when it ends early. Returns the task id. */
    fun beginBackgroundTask(onExpire: () -> Unit): ULong

    /** Ends the background task [id] that [beginBackgroundTask] returned. */
    fun endBackgroundTask(id: ULong)

    /**
     * The id [beginBackgroundTask] returns when no task was granted; it is never ended. A host without background
     * tasks (desktop) returns this id from every [beginBackgroundTask] and so never ends anything.
     */
    val invalidBackgroundTaskId: ULong

    /** A new document picker (one per pane-menu import or export). */
    fun newDocumentPicker(): DocumentPicker

    /** A new bookmark CSV sink opener (one per export). */
    fun newCsvSinkOpener(): (DocumentRef) -> Sink?

    /** The timestamp in a bookmark CSV export's file name. */
    fun csvTimestamp(): String

    /** The image picker of the host's slots (built once, with the slots). */
    fun newImagePicker(): ImagePicker

    /** The text share of the host's slots (built once, with the slots). */
    fun newTextShare(): TextShare

    /** The toolbar host's device preferences and the night-mode reads over them (built once, with the host). */
    fun newToolbarDeviceState(): ToolbarDeviceState
}

/**
 * The device-local preferences the toolbar host writes and the night-mode answers read over the same store:
 * [nightModeOn] is the resolved night mode, [nightModeManual] whether the stored mode is `"manual"`.
 */
@kotlin.native.HiddenFromObjC
class ToolbarDeviceState(
    val deviceSettings: DeviceLocalPrefs,
    val nightModeOn: () -> Boolean,
    val nightModeManual: () -> Boolean,
)
