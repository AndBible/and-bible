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

package net.bible.sharedui.ios.ports

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.app.welcome.WelcomeHostPlatform
import net.bible.sharedui.assembly.iosStringTable
import net.bible.sharedui.ios.IosPending
import net.bible.sharedui.strings.keyed.StringTable
import platform.Foundation.NSBundle

/**
 * Welcome on iOS: the Easy start and Download paths are real; Import, Restore, Redownload and the links are pending.
 * [leaveFor] initialises the app off the main thread and navigates (Android `WelcomeFlow.leaveFor`).
 */
@kotlin.native.HiddenFromObjC
class IosWelcomeHostPlatform(
    private val table: StringTable = iosStringTable("en"),
    private val isDiscrete: () -> Boolean = { false },
    private val leaveFor: (String) -> Unit = { IosPending.log("WelcomeHostPlatform.leaveFor") },
) : WelcomeHostPlatform {
    // Discrete-aware like Android's startupWelcomeAppNameRes: the calculator name hides the app.
    override val appName: String
        get() = table.text(if (isDiscrete()) "app_name_calculator" else "app_name_long")

    override val windowTitle: String get() = appName

    override val logo: @Composable () -> Painter? = { null }

    // The previous-install (Redownload) probe reads the database, so it is not asked on the main thread here.
    override val controller: StartupWelcomeController by lazy { StartupWelcomeController { loadInfo() } }

    private fun loadInfo(): StartupWelcomeInfo {
        val zip = table.fmt("format_zip", table.text("app_name_short"))
        val formats = table.fmt(
            "supported_formats",
            "$zip, ${table.text("format_mybible")}, ${table.text("format_mysword")}, ${table.text("format_epub")}",
        )
        val versionName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
        return StartupWelcomeInfo(
            versionText = table.fmt("version_text", versionName),
            supportedFormatsText = formats,
            previousInstallDetected = false,
            easyStartAvailable = table.tag.substringBefore('-').substringBefore('_') == "en",
            homepageButtonsVisible = !isDiscrete(),
        )
    }

    override fun onDownload() = leaveFor(NavRoutes.download(firstDownload = true))

    override fun onImport() {
        IosPending.log("WelcomeHostPlatform.onImport")
    }

    override fun onRestore() {
        IosPending.log("WelcomeHostPlatform.onRestore")
    }

    override fun onRedownload() {
        IosPending.log("WelcomeHostPlatform.onRedownload")
    }

    override fun onEasyStart() = leaveFor(NavRoutes.download(firstDownload = true, downloadRecommended = true))

    override fun onOpenHomepage() {
        IosPending.log("WelcomeHostPlatform.onOpenHomepage")
    }

    override fun onOpenGithub() {
        IosPending.log("WelcomeHostPlatform.onOpenGithub")
    }

    @Composable
    override fun Dialogs() {
        IosPending.log("WelcomeHostPlatform.Dialogs")
    }
}
