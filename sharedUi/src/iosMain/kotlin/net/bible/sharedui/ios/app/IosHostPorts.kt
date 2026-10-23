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
import net.bible.sharedui.assembly.iosStringTable
import net.bible.sharedui.strings.keyed.StringTable
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
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.ios.ports.IosOrientationLock
import net.bible.sharedui.ios.ports.IosPromptFiles
import net.bible.sharedui.ios.ports.IosReadingPlanHostPlatform
import net.bible.sharedui.ios.ports.IosStudyPadExportPlatform
import net.bible.sharedui.ios.ports.IosSystemScreens
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.ios.ports.IosWelcomeHostPlatform
import org.koin.core.Koin

/**
 * Assembles the 22 implemented [HostPorts] for iOS; no port is pending. Among them
 * [HostPorts.backupHost] (Stream D's `IosBackupHost` taken from [koin]), Welcome, document/help dialogs,
 * and the Download/cloud document ports. Retained language-sensitive ports read the current table through a provider.
 */
@kotlin.native.HiddenFromObjC
@Suppress("UNUSED_PARAMETER")
fun buildIosHostPorts(
    shell: IosHostShell,
    koin: Koin,
    table: StringTable = iosStringTable("en"),
    initialiseWelcome: suspend () -> Unit = {},
    isPortrait: () -> Boolean = { true },
): HostPorts {
    val welcome = IosWelcomeHostPlatform(koin, shell.hostScope, table, initialiseWelcome, shell::navigate)
    val readingPlan = IosReadingPlanHostPlatform(koin.get(), isPortrait)
    val currentTable = { koin.getOrNull<net.bible.sharedui.assembly.IosStringTableHolder>()?.current ?: table }
    val installZip = IosInstallZipHostPlatform(table = currentTable)
    val picker = IosDocumentPicker()
    val bookmark by lazy {
        IosBookmarkHostPlatform({ koin.get() }, picker, koin.get(), koin.get(), currentTable, koin.get(), koin.get())
    }
    val chooser = IosChooserHostPlatform(koin, shell.hostScope, currentTable)
    val documents = IosDocumentDialogs(shell.hostScope, koin.get(), currentTable, koin.get(), koin.get())
    val download = IosDownloadHostPlatform(documents)
    val cloud = IosCloudDocumentsHostPlatform()
    val promptFiles = IosPromptFiles()
    val bugReport = IosBugReportPlatform()
    val ai = IosAiHostPlatform(koin.get(), koin.get(), currentTable) { message, cause ->
        koin.get<BackupHost>().showErrorReport(message, cause)
    }
    return HostPorts(
        documentPicker = picker,
        imagePicker = IosImagePicker(),
        textShare = IosTextShare(),
        notificationPermission = koin.get<NotificationPermission>(),
        systemScreens = IosSystemScreens(),
        documentDialogs = documents,
        helpDialogs = IosHelpDialogs(koin.get(), currentTable),
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
