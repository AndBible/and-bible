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
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.app.welcome.WelcomeHostPlatform
import net.bible.sharedui.ios.IosPending

/** Pending (Task 5 makes the controller and the buttons real). */
@kotlin.native.HiddenFromObjC
class IosWelcomeHostPlatform : WelcomeHostPlatform {
    override val windowTitle: String = ""

    override val appName: String = ""

    override val logo: @Composable () -> Painter? = { null }

    override val controller: StartupWelcomeController = StartupWelcomeController {
        StartupWelcomeInfo(
            versionText = "", supportedFormatsText = "",
            previousInstallDetected = false, easyStartAvailable = false,
        )
    }

    override fun onDownload() {
        IosPending.log("WelcomeHostPlatform.onDownload")
    }

    override fun onImport() {
        IosPending.log("WelcomeHostPlatform.onImport")
    }

    override fun onRestore() {
        IosPending.log("WelcomeHostPlatform.onRestore")
    }

    override fun onRedownload() {
        IosPending.log("WelcomeHostPlatform.onRedownload")
    }

    override fun onEasyStart() {
        IosPending.log("WelcomeHostPlatform.onEasyStart")
    }

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
