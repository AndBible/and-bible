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
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.host.ports.HostAiHostPlatform
import net.bible.sharedui.host.ports.HostBookmarkHostPlatform
import net.bible.sharedui.host.ports.HostChooserHostPlatform
import net.bible.sharedui.host.ports.HostCloudDocumentsHostPlatform
import net.bible.sharedui.host.ports.HostDocumentDialogs
import net.bible.sharedui.host.ports.HostDownloadHostPlatform
import net.bible.sharedui.host.ports.HostHelpDialogs
import net.bible.sharedui.host.ports.HostInstallZipHostPlatform
import net.bible.sharedui.host.ports.HostLabelIcons
import net.bible.sharedui.host.ports.HostReadingPlanHostPlatform
import net.bible.sharedui.host.ports.HostStudyPadExportPlatform
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedcore.ui.dialog.AppDialogController
import org.koin.core.Koin
import java.io.File

/**
 * The desktop [HostPorts] over [shell]: the counterpart of `buildIosHostPorts`. Every port comes from [koin] (the stream
 * modules bind the wave-3 ones), so no stream edits this file. [table] is the welcome page's table before startup
 * completes; later reads follow the live string-table holder. `backupHost` is the hoisted `HostBackupHost` and
 * `welcomePlatform()` an `HostWelcomeHostPlatform`: `HostApp` casts both.
 */
fun buildDesktopHostPorts(
    koin: Koin,
    shell: HostShell,
    table: StringTable = koin.get<HostStringTableHolder>().current,
    initialiseWelcome: suspend () -> Unit = {},
    isPortrait: () -> Boolean = { koin.get<HostWindowMetrics>().isPortrait },
): HostPorts {
    val versionName = { koin.get<net.bible.sharedcore.platform.desktop.DesktopAppEnvironment>().versionName }
    val currentTable = { koin.getOrNull<HostStringTableHolder>()?.current ?: table }
    val welcome = HostWelcomeHostPlatform(koin, shell.hostScope, table, initialiseWelcome, shell::navigate, versionName)
    val readingPlan = HostReadingPlanHostPlatform(koin.get(), isPortrait)
    val installZip = HostInstallZipHostPlatform(table = currentTable, notificationPermission = koin.get(), debugArgs = koin.get())
    val picker = koin.get<net.bible.sharedui.app.ports.DocumentPicker>()
    val csvFiles by lazy { koin.get<DesktopBookmarkCsvFiles>() }
    val bookmark by lazy {
        HostBookmarkHostPlatform({ koin.get() }, picker, koin.get(), koin.get(), currentTable, koin.get(), koin.get(),
            { csvFiles.openSink(it) }, { csvFiles.timestamp() })
    }
    val chooser = HostChooserHostPlatform(koin, shell.hostScope, currentTable, koin.get())
    val documents = HostDocumentDialogs(shell.hostScope, koin.get(), currentTable, koin.get(), { koin.get() })
    val download = HostDownloadHostPlatform(documents) { path -> runCatching { File(path).usableSpace }.getOrNull() }
    val cloud = HostCloudDocumentsHostPlatform()
    val promptFiles = koin.get<net.bible.sharedui.app.ai.PromptFiles>()
    val bugReport = koin.get<net.bible.sharedui.app.ai.BugReportPlatform>()
    val ai = HostAiHostPlatform(koin.get(), koin.get(), currentTable, { aiLogTimestamp(currentTable().tag, it) }) { message, cause ->
        koin.get<BackupHost>().showErrorReport(message, cause)
    }
    return HostPorts(
        documentPicker = picker,
        imagePicker = koin.get(),
        textShare = koin.get(),
        notificationPermission = koin.get(),
        systemScreens = koin.get(),
        documentDialogs = documents,
        helpDialogs = HostHelpDialogs(koin.get<AppDialogController>(), currentTable, ::classpathBundledHtml, versionName),
        welcomePlatform = { welcome },
        chooserPlatform = { chooser },
        labelIcons = HostLabelIcons(),
        studyPadExport = HostStudyPadExportPlatform(),
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
