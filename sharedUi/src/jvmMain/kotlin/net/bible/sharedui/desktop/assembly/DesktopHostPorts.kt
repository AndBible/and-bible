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
package net.bible.sharedui.desktop.assembly

import net.bible.android.control.backup.BackupHost
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.assembly.HostWindowMetrics
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.host.ports.IosAiHostPlatform
import net.bible.sharedui.host.ports.IosBookmarkHostPlatform
import net.bible.sharedui.host.ports.IosChooserHostPlatform
import net.bible.sharedui.host.ports.IosCloudDocumentsHostPlatform
import net.bible.sharedui.host.ports.IosDocumentDialogs
import net.bible.sharedui.host.ports.IosDownloadHostPlatform
import net.bible.sharedui.host.ports.IosHelpDialogs
import net.bible.sharedui.host.ports.IosInstallZipHostPlatform
import net.bible.sharedui.host.ports.IosLabelIcons
import net.bible.sharedui.host.ports.IosReadingPlanHostPlatform
import net.bible.sharedui.host.ports.IosStudyPadExportPlatform
import net.bible.sharedui.host.ports.IosWelcomeHostPlatform
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedcore.ui.dialog.AppDialogController
import org.koin.core.Koin
import java.io.File

/**
 * The desktop [HostPorts] over [shell]: the counterpart of `buildIosHostPorts`. Every port comes from [koin] (the stream
 * modules bind the wave-3 ones), so no stream edits this file. [table] is the welcome page's table before startup
 * completes; later reads follow the live string-table holder. `backupHost` is the hoisted `IosBackupHost` and
 * `welcomePlatform()` an `IosWelcomeHostPlatform`: `IosApp` casts both.
 */
fun buildDesktopHostPorts(
    koin: Koin,
    shell: HostShell,
    table: StringTable = koin.get<IosStringTableHolder>().current,
    initialiseWelcome: suspend () -> Unit = {},
    isPortrait: () -> Boolean = { koin.get<HostWindowMetrics>().isPortrait },
): HostPorts {
    val versionName = { koin.get<net.bible.sharedcore.platform.desktop.DesktopAppEnvironment>().versionName }
    val currentTable = { koin.getOrNull<IosStringTableHolder>()?.current ?: table }
    val welcome = IosWelcomeHostPlatform(koin, shell.hostScope, table, initialiseWelcome, shell::navigate, versionName)
    val readingPlan = IosReadingPlanHostPlatform(koin.get(), isPortrait)
    val installZip = IosInstallZipHostPlatform(table = currentTable, notificationPermission = koin.get(), debugArgs = koin.get())
    val picker = koin.get<net.bible.sharedui.app.ports.DocumentPicker>()
    val csvFiles by lazy { koin.get<DesktopBookmarkCsvFiles>() }
    val bookmark by lazy {
        IosBookmarkHostPlatform({ koin.get() }, picker, koin.get(), koin.get(), currentTable, koin.get(), koin.get(),
            { csvFiles.openSink(it) }, { csvFiles.timestamp() })
    }
    val chooser = IosChooserHostPlatform(koin, shell.hostScope, currentTable, koin.get())
    val documents = IosDocumentDialogs(shell.hostScope, koin.get(), currentTable, koin.get(), { koin.get() })
    val download = IosDownloadHostPlatform(documents) { path -> runCatching { File(path).usableSpace }.getOrNull() }
    val cloud = IosCloudDocumentsHostPlatform()
    val promptFiles = koin.get<net.bible.sharedui.app.ai.PromptFiles>()
    val bugReport = koin.get<net.bible.sharedui.app.ai.BugReportPlatform>()
    val ai = IosAiHostPlatform(koin.get(), koin.get(), currentTable, { aiLogTimestamp(currentTable().tag, it) }) { message, cause ->
        koin.get<BackupHost>().showErrorReport(message, cause)
    }
    return HostPorts(
        documentPicker = picker,
        imagePicker = koin.get(),
        textShare = koin.get(),
        notificationPermission = koin.get(),
        systemScreens = koin.get(),
        documentDialogs = documents,
        helpDialogs = IosHelpDialogs(koin.get<AppDialogController>(), currentTable, ::classpathBundledHtml, versionName),
        welcomePlatform = { welcome },
        chooserPlatform = { chooser },
        labelIcons = IosLabelIcons(),
        studyPadExport = IosStudyPadExportPlatform(),
        bookmarkPlatform = { bookmark },
        readingPlanPlatform = { readingPlan },
        myDocumentFiles = koin.get(),
        backupHost = koin.get<BackupHost>(),
        orientationLock = koin.get(),
        installZipPlatform = { installZip },
        downloadPlatform = { download },
        cloudDocumentsPlatform = { cloud },
        promptFiles = { promptFiles },
        bugReport = { bugReport },
        aiPlatform = { ai },
    )
}

private fun aiLogTimestamp(tag: String, epochMs: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.forLanguageTag(tag.replace('_', '-')))
        .format(java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()))
