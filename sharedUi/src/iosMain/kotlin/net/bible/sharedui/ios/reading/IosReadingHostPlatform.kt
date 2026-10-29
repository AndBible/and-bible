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

import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedlogic.platform.ios.IosNightModeState
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.ios.app.iosSetStatusBarHidden
import net.bible.sharedui.ios.ports.IosBookmarkCsvFiles
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.ios.ports.iosBookmarkCsvTimestamp
import net.bible.sharedui.ios.ports.iosBundleVersionName
import okio.Sink
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

/** The iOS [ReadingHostPlatform]: the UIKit, Foundation and CoreMotion calls the reading host made itself before. */
@kotlin.native.HiddenFromObjC
object IosReadingHostPlatform : ReadingHostPlatform {
    override fun openUrl(url: String) = iosOpenUrl(url)

    override val settingsUrl: String get() = iosOpenSettingsUrl

    override fun setStatusBarHidden(hidden: Boolean) = iosSetStatusBarHidden(hidden)

    override fun isPortrait(): Boolean = iosIsPortrait()

    override fun isRightToLeft(): Boolean = iosIsRightToLeft()

    override fun screenScale(): Float = iosScreenScale()

    override fun versionName(): String = iosBundleVersionName()

    override fun tiltAvailable(): Boolean = iosMotionManager.deviceMotionAvailable

    override fun formatHistoryTimestamp(epochMillis: Long): String =
        NSDateFormatter().apply { dateFormat = "h:mm a, E d MMM " }
            .stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

    override fun copyToPasteboard(text: String) = iosCopyToPasteboard(text)

    override fun beginBackgroundTask(onExpire: () -> Unit): ULong = iosBeginAgentBackgroundTask(onExpire)

    override fun endBackgroundTask(id: ULong) = iosEndAgentBackgroundTask(id)

    override val invalidBackgroundTaskId: ULong get() = iosInvalidBackgroundTaskId

    override fun newDocumentPicker(): DocumentPicker = IosDocumentPicker()

    override fun newCsvSinkOpener(): (DocumentRef) -> Sink? = IosBookmarkCsvFiles()::openSink

    override fun csvTimestamp(): String = iosBookmarkCsvTimestamp()

    override fun newImagePicker(): ImagePicker = IosImagePicker()

    override fun newTextShare(): TextShare = IosTextShare()

    /** A fresh `IosDeviceSettings()` now and an [IosNightModeState] over it, built on first use. */
    override fun newToolbarDeviceState(): ToolbarDeviceState {
        val deviceSettings = IosDeviceSettings()
        val nightState by lazy { IosNightModeState(deviceSettings) }
        return ToolbarDeviceState(
            deviceSettings = deviceSettings,
            nightModeOn = { nightState.nightMode },
            nightModeManual = { nightState.mode == "manual" },
        )
    }
}
