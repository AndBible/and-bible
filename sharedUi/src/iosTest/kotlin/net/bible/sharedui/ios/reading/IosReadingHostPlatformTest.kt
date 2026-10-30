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

package net.bible.sharedui.ios.reading

import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedlogic.platform.ios.IosNightModeState
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextShare
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import net.bible.sharedui.host.reading.ReadingHostPlatform
import net.bible.sharedui.host.reading.ToolbarDeviceState

/**
 * The reading host's [ReadingHostPlatform] seam: the iOS implementation answers what the host's own UIKit calls answered,
 * and the host builds the platform objects when it used to (slots and toolbar state at construction, pickers per use).
 */
class IosReadingHostPlatformTest {
    /** Counts the object-building hooks; everything else is the iOS platform. */
    private class CountingPlatform(private val base: ReadingHostPlatform = IosReadingHostPlatform) : ReadingHostPlatform by base {
        var imagePickers = 0
        var textShares = 0
        var toolbarStates = 0
        var documentPickers = 0
        var csvOpeners = 0

        override fun newImagePicker(): ImagePicker = base.newImagePicker().also { imagePickers++ }
        override fun newTextShare(): TextShare = base.newTextShare().also { textShares++ }
        override fun newToolbarDeviceState(): ToolbarDeviceState = base.newToolbarDeviceState().also { toolbarStates++ }
        override fun newDocumentPicker(): DocumentPicker = base.newDocumentPicker().also { documentPickers++ }
        override fun newCsvSinkOpener() = base.newCsvSinkOpener().also { csvOpeners++ }
    }

    @Test
    fun theHostBuildsSlotAndToolbarObjectsOnceAndPickersOnlyOnUse() {
        val platform = CountingPlatform()
        withIosReadingHost(hostPlatform = platform) { _, _ ->
            assertEquals(1, platform.imagePickers, "image picker built once, with the slots")
            assertEquals(1, platform.textShares, "text share built once, with the slots")
            assertEquals(1, platform.toolbarStates, "toolbar device state built once, with the host")
            assertEquals(0, platform.documentPickers, "document picker built per pane-menu use")
            assertEquals(0, platform.csvOpeners, "CSV sink opener built per export")
        }
    }

    @Test
    fun theSettingsUrlAndInvalidTaskIdAreUikits() {
        assertEquals(UIApplicationOpenSettingsURLString, IosReadingHostPlatform.settingsUrl)
        assertEquals(UIBackgroundTaskInvalid, IosReadingHostPlatform.invalidBackgroundTaskId)
    }

    /** Each toolbar state owns fresh `IosDeviceSettings`; its night reads are an [IosNightModeState] over the standard defaults. */
    @Test
    fun toolbarDeviceStateReadsTheNightModeKeysOfTheStandardDefaults() {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = IosNightModeState.NIGHT_MODE_MODE_KEY
        val previous = defaults.objectForKey(key)
        try {
            val first = IosReadingHostPlatform.newToolbarDeviceState()
            val second = IosReadingHostPlatform.newToolbarDeviceState()
            assertTrue(first.deviceSettings is IosDeviceSettings)
            assertNotSame(first.deviceSettings, second.deviceSettings)
            defaults.setObject("manual", key)
            assertTrue(first.nightModeManual())
            defaults.setObject("system", key)
            assertFalse(first.nightModeManual())
            assertEquals(IosNightModeState(IosDeviceSettings()).nightMode, first.nightModeOn())
        } finally {
            if (previous == null) defaults.removeObjectForKey(key) else defaults.setObject(previous, key)
        }
    }
}
