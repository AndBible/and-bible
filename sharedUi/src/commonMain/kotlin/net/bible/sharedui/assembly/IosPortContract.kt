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
package net.bible.sharedui.assembly

import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.SettingLabels
import net.bible.android.control.page.window.WindowSettingsPicker
import net.bible.android.control.search.SearchTextHighlighter
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.format.SaxTextPolicy
import net.bible.service.history.HistoryPlatform
import net.bible.service.llm.LlmStrings
import net.bible.service.llm.agent.AgentPlatform
import net.bible.service.sword.ContentSource
import net.bible.service.sword.HtmlToPlainText
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.llm.http.LlmHttpTransport
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.MarkdownRenderer
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.platform.DistributedPlanCatalog
import net.bible.sharedlogic.platform.EpubStoreEdge
import net.bible.sharedlogic.platform.FirstLoadEdge
import net.bible.sharedlogic.platform.LabelNameStrings
import net.bible.sharedlogic.platform.LocalCalendar
import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.ManualInstallFolderEnabler
import net.bible.sharedlogic.platform.MyDocumentStoreEdge
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.platform.PlatformLocales
import net.bible.sharedlogic.platform.PromptPackSource
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.SearchIndexScheduler
import net.bible.sharedlogic.platform.SentenceBreaker
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import kotlin.reflect.KClass

/**
 * The port types `IosPlatformBindings` (sharedUi iosMain) and `buildIosSyncBindings` (sharedLogic iosMain) bind, declared here
 * (common code) so a JVM test can compare them with what the shared definitions require, without a Mac. `IosAssemblyTest.boundPortListMatchesTheModule`
 * checks, on Native, that this list and the real Koin module are the same set. Keep both in step.
 */
val IOS_BOUND_PORT_TYPES: List<KClass<*>> = listOf(
    AppEnvironment::class, SecretStore::class, AppDirectories::class, AppSettings::class,
    CoreStrings::class, LlmStrings::class, LabelNameStrings::class, SettingLabels::class, DistributedPlanCatalog::class,
    DateTimeFormats::class, UserNotifier::class, ReadingPlanSource::class,
    DownloadHttp::class, LlmHttpTransport::class,
    SpeakPlatform::class, SpeakSettingsStore::class, MarkdownRenderer::class, AgentPlatform::class,
    AddonCatalog::class, PromptPackSource::class, PlatformLocales::class, LocaleSupport::class,
    ReadOnlyModuleDbOpener::class, SearchIndexScheduler::class, LocalCalendar::class,
    FirstLoadEdge::class, EpubStoreEdge::class, MyDocumentStoreEdge::class, SentenceBreaker::class,
    NightModeState::class,
    GlobalTextDisplaySettingsSource::class, WindowSettingsPicker::class, KeyChooserLauncher::class,
    HistoryPlatform::class, LinkPlatform::class, HtmlToPlainText::class, ContentSource::class,
    SaxTextPolicy::class, SearchTextHighlighter::class, ManualInstallFolderEnabler::class, TextDisplayPrefItems::class,
    // Stream D, bound by `buildIosSyncBindings` (joined into the Koin start in I2 phase 2)
    CloudAdapterFactory::class, SyncSettingsPlatform::class, DocumentSyncStarter::class, SyncScheduler::class,
)
