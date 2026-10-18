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

package net.bible.sharedui.ios.app

import net.bible.android.control.backup.BackupHost
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.ios.ports.IosAiHostPlatform
import net.bible.sharedui.ios.ports.IosBookmarkHostPlatform
import net.bible.sharedui.ios.ports.IosBugReportPlatform
import net.bible.sharedui.ios.ports.IosChooserHostPlatform
import net.bible.sharedui.ios.ports.IosCloudDocumentsHostPlatform
import net.bible.sharedui.ios.ports.IosDocumentDialogs
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.ios.ports.IosDownloadHostPlatform
import net.bible.sharedui.ios.ports.IosHelpDialogs
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.ios.ports.IosInstallZipHostPlatform
import net.bible.sharedui.ios.ports.IosLabelIcons
import net.bible.sharedui.ios.ports.IosMyDocumentFiles
import net.bible.sharedui.ios.ports.IosNotificationPermission
import net.bible.sharedui.ios.ports.IosOrientationLock
import net.bible.sharedui.ios.ports.IosPromptFiles
import net.bible.sharedui.ios.ports.IosReadingPlanHostPlatform
import net.bible.sharedui.ios.ports.IosStudyPadExportPlatform
import net.bible.sharedui.ios.ports.IosSystemScreens
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.ios.ports.IosWelcomeHostPlatform
import org.koin.core.Koin

/**
 * Assembles the 22 [HostPorts] for iOS. Every port is pending except [HostPorts.backupHost], which is Stream D's
 * `IosBackupHost` taken from [koin]. [shell] is reserved for ports that grow real behaviour (Task 5 and the streams).
 */
@kotlin.native.HiddenFromObjC
@Suppress("UNUSED_PARAMETER")
fun buildIosHostPorts(shell: IosHostShell, koin: Koin): HostPorts {
    val welcome = IosWelcomeHostPlatform()
    val chooser = IosChooserHostPlatform()
    val bookmark = IosBookmarkHostPlatform()
    val readingPlan = IosReadingPlanHostPlatform()
    val installZip = IosInstallZipHostPlatform()
    val download = IosDownloadHostPlatform()
    val cloud = IosCloudDocumentsHostPlatform()
    val promptFiles = IosPromptFiles()
    val bugReport = IosBugReportPlatform()
    val ai = IosAiHostPlatform()
    return HostPorts(
        documentPicker = IosDocumentPicker(),
        imagePicker = IosImagePicker(),
        textShare = IosTextShare(),
        notificationPermission = IosNotificationPermission(),
        systemScreens = IosSystemScreens(),
        documentDialogs = IosDocumentDialogs(),
        helpDialogs = IosHelpDialogs(),
        welcomePlatform = { welcome },
        chooserPlatform = { chooser },
        labelIcons = IosLabelIcons(),
        studyPadExport = IosStudyPadExportPlatform(),
        bookmarkPlatform = { bookmark },
        readingPlanPlatform = { readingPlan },
        myDocumentFiles = IosMyDocumentFiles(),
        backupHost = koin.get<BackupHost>(),
        orientationLock = IosOrientationLock(),
        installZipPlatform = { installZip },
        downloadPlatform = { download },
        cloudDocumentsPlatform = { cloud },
        promptFiles = { promptFiles },
        bugReport = { bugReport },
        aiPlatform = { ai },
    )
}
