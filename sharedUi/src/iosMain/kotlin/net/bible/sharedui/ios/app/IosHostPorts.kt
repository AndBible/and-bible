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
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.host.ports.HostAiHostPlatform
import net.bible.sharedui.ios.ports.IosNotificationPermission
import net.bible.sharedui.ios.ports.IosBookmarkCsvFiles
import net.bible.sharedui.host.ports.HostBookmarkHostPlatform
import net.bible.sharedui.ios.ports.iosAiLogTimestamp
import net.bible.sharedui.ios.ports.iosBookmarkCsvTimestamp
import net.bible.sharedui.ios.ports.IosBugReportPlatform
import net.bible.sharedui.host.ports.HostChooserHostPlatform
import net.bible.sharedui.host.ports.HostCloudDocumentsHostPlatform
import net.bible.sharedui.host.ports.HostDocumentDialogs
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.host.ports.HostDownloadHostPlatform
import net.bible.sharedui.host.ports.HostHelpDialogs
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.host.ports.HostInstallZipHostPlatform
import net.bible.sharedui.host.ports.HostLabelIcons
import net.bible.sharedui.ios.ports.IosMyDocumentFiles
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.ios.ports.IosOrientationLock
import net.bible.sharedui.ios.ports.IosPromptFiles
import net.bible.sharedui.host.ports.HostReadingPlanHostPlatform
import net.bible.sharedui.host.ports.HostStudyPadExportPlatform
import net.bible.sharedui.ios.ports.IosSystemScreens
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform
import net.bible.sharedui.ios.ports.IosWindowMetrics
import net.bible.sharedui.ios.ports.iosBundleVersionName
import net.bible.sharedui.ios.ports.iosBundledHtml
import net.bible.sharedui.ios.ports.iosFreeSpaceBytes
import org.koin.core.Koin

/**
 * Assembles the 22 implemented [HostPorts] for iOS; no port is pending. Among them
 * [HostPorts.backupHost] (Stream D's `HostBackupHost` taken from [koin]), Welcome, document/help dialogs,
 * and the Download/cloud document ports. Retained language-sensitive ports read the current table through a provider.
 */
@kotlin.native.HiddenFromObjC
@Suppress("UNUSED_PARAMETER")
fun buildIosHostPorts(
    shell: net.bible.sharedui.app.HostShell,
    koin: Koin,
    table: StringTable = hostStringTable("en"),
    initialiseWelcome: suspend () -> Unit = {},
    isPortrait: () -> Boolean = { true },
): HostPorts {
    val welcome = HostWelcomeHostPlatform(koin, shell.hostScope, table, initialiseWelcome, shell::navigate, ::iosBundleVersionName)
    val readingPlan = HostReadingPlanHostPlatform(koin.get(), isPortrait)
    val currentTable = { koin.getOrNull<net.bible.sharedui.assembly.HostStringTableHolder>()?.current ?: table }
    val installZip = HostInstallZipHostPlatform(table = currentTable, notificationPermission = IosNotificationPermission(), debugArgs = IosDebugLaunchArgs)
    val picker = IosDocumentPicker()
    val bookmark by lazy {
        HostBookmarkHostPlatform({ koin.get() }, picker, koin.get(), koin.get(), currentTable, koin.get(), koin.get(),
            IosBookmarkCsvFiles()::openSink, ::iosBookmarkCsvTimestamp)
    }
    val chooser = HostChooserHostPlatform(koin, shell.hostScope, currentTable, IosWindowMetrics())
    val documents = HostDocumentDialogs(shell.hostScope, koin.get(), currentTable, koin.get(), { koin.get() })
    val download = HostDownloadHostPlatform(documents, ::iosFreeSpaceBytes)
    val cloud = HostCloudDocumentsHostPlatform()
    val promptFiles = IosPromptFiles()
    val bugReport = IosBugReportPlatform()
    val ai = HostAiHostPlatform(koin.get(), koin.get(), currentTable, { iosAiLogTimestamp(currentTable().tag, it) }) { message, cause ->
        koin.get<BackupHost>().showErrorReport(message, cause)
    }
    return HostPorts(
        documentPicker = picker,
        imagePicker = IosImagePicker(),
        textShare = IosTextShare(),
        notificationPermission = koin.get<NotificationPermission>(),
        systemScreens = IosSystemScreens(),
        documentDialogs = documents,
        helpDialogs = HostHelpDialogs(koin.get(), currentTable, ::iosBundledHtml, ::iosBundleVersionName),
        welcomePlatform = { welcome },
        chooserPlatform = { chooser },
        labelIcons = HostLabelIcons(),
        studyPadExport = HostStudyPadExportPlatform(),
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
