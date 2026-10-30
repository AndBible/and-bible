/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.bible.sharedui.desktop.assembly

import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.files.DesktopFileDelivery
import net.bible.sharedcore.platform.desktop.files.DesktopFilePicker
import net.bible.sharedcore.platform.desktop.files.FileChooserBackend
import net.bible.sharedcore.platform.desktop.files.desktopFileChooser
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedlogic.platform.desktop.content.DesktopContentSource
import net.bible.service.sword.ContentSource
import net.bible.sharedui.app.ai.PromptFiles
import net.bible.sharedui.app.ports.*
import net.bible.sharedui.desktop.files.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

fun desktopDocumentModule(): Module = module {
    single<FileChooserBackend> { desktopFileChooser(DesktopDbus()) }
    single<FilePicker> { DesktopFilePicker(get()) }
    single<FileDelivery> { DesktopFileDelivery(get()) }
    single<DocumentPicker> { DesktopDocumentPicker(get()) }
    single<ImagePicker> { DesktopImagePicker(get()) }
    single<ContentSource> { DesktopContentSource() }
    single<ClipboardText> { DesktopClipboardText() }
    single<TextShare> { DesktopTextShare(get(), get()) }
    single<DesktopIncomingFiles>(createdAtStart = true) {
        DesktopIncomingFilesImpl(get<AppDirectories>().internalFilesDir.toNioPath(), get<DesktopLaunchArgs>().args, get(), get<DesktopExitTasks>())
    } onClose { (it as? DesktopIncomingFilesImpl)?.close() }
    single<DesktopFileShare> { DesktopFileShareImpl(get()) }
    single<ExternalLinks> { DesktopExternalLinks() }
    single<MyDocumentFiles> { DesktopMyDocumentFiles() }
    single<PromptFiles> { DesktopPromptFiles() }
}
