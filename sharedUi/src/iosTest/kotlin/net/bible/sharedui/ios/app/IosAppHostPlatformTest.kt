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
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package net.bible.sharedui.ios.app

import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.assembly.IosHostLifecycle
import net.bible.sharedui.assembly.iosPreferredLanguages
import net.bible.sharedui.assembly.resolveDeviceUiLanguage
import net.bible.sharedui.ios.reading.IosDisplayModeSignals
import net.bible.sharedui.ios.reading.IosReadingHostPlatform
import net.bible.sharedui.ios.reading.IosTiltScroll
import net.bible.sharedui.webview.IosReadingPaneEngine
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pins [IosAppHostPlatform] to the objects and calls IosApp used directly before the seam. */
class IosAppHostPlatformTest {
    @Test
    fun theSingletonsAreTheOnesTheRootUsedBefore() {
        assertSame(IosDebugLaunchArgs, IosAppHostPlatform.debugArgs)
        assertSame(IosHostLifecycle, IosAppHostPlatform.lifecycle)
        assertSame(IosReadingHostPlatform, IosAppHostPlatform.readingHost)
        assertSame(IosReadingPaneEngine, IosAppHostPlatform.paneEngine)
        assertSame(IosDisplayModeSignals, IosAppHostPlatform.displayModeSignals)
        assertSame(IosAppDialogHost, IosAppHostPlatform.dialogHost)
    }

    @Test
    fun noBackgroundTaskIsUiKitsInvalidId() {
        assertEquals(UIBackgroundTaskInvalid, IosAppHostPlatform.invalidBackgroundTaskId)
    }

    @Test
    fun eachReadingDestinationGetsAFreshIosTiltScroll() {
        val koin = koinApplication { }.koin
        val first = IosAppHostPlatform.newTiltScroll(koin)
        assertTrue(first is IosTiltScroll)
        assertNotSame(first, IosAppHostPlatform.newTiltScroll(koin))
    }

    @Test
    fun launchFlagsAreTheDebugProcessArgumentsAsBefore() {
        // The old root: `if (isDebugBinary) NSProcessInfo.processInfo.arguments else emptyList()` and `flag in` that list.
        val before: List<*> = if (Platform.isDebugBinary) NSProcessInfo.processInfo.arguments else emptyList<Any?>()
        val names = NSProcessInfo.processInfo.arguments.map { it.toString() } + listOf("-task5Settings", "-h2SyncSettings", "-task5Error", "-noSuchFlag")
        for (name in names) assertEquals(name in before, IosAppHostPlatform.debugArgs.hasLaunchFlag(name), name)
    }

    @Test
    fun deviceUiLanguageReadsTheKoinDeviceSettingsAndDeviceLanguages() {
        val settings = IosDeviceSettings()
        val koin = koinApplication { modules(module { single { settings } }) }.koin
        assertEquals(resolveDeviceUiLanguage(settings, ::iosPreferredLanguages), IosAppHostPlatform.deviceUiLanguage(koin))
    }
}
