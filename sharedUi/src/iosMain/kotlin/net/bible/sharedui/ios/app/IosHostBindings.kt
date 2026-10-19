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

import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.TextDisplayPlatform
import net.bible.sharedui.ios.ports.IosAppSettingsService
import net.bible.sharedui.ios.ports.IosClipboardText
import net.bible.sharedui.ios.ports.IosDebugActions
import net.bible.sharedui.ios.ports.IosDocumentInstallStarter
import net.bible.sharedui.ios.ports.IosExternalLinks
import net.bible.sharedui.ios.ports.IosPlatformText
import net.bible.sharedui.ios.ports.IosSettingsPlatform
import net.bible.sharedui.ios.ports.IosTextDisplayPlatform
import org.koin.core.module.Module
import org.koin.dsl.module

/** The eight host ports the app's Koin graph carries on Android (bound there in `:app`); see [IOS_HOST_BINDING_TYPES]. */
@kotlin.native.HiddenFromObjC
fun buildIosHostBindings(): Module = module {
    single<ExternalLinks> { IosExternalLinks() }
    single<ClipboardText> { IosClipboardText() }
    single<PlatformText> { IosPlatformText() }
    single<DebugActions> { IosDebugActions() }
    single<SettingsPlatform> { IosSettingsPlatform(get<CoreStrings>()) }
    single<TextDisplayPlatform> { IosTextDisplayPlatform() }
    single<DocumentInstallStarter> { IosDocumentInstallStarter() }
    single<AppSettingsService> { IosAppSettingsService() }
}
