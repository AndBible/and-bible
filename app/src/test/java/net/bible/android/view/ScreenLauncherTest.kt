/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.ai.AiConnectionSettingsActivity
import net.bible.android.view.activity.ai.AiConnectionSettingsComposeActivity
import net.bible.android.view.activity.ai.AiDocumentFilterActivity
import net.bible.android.view.activity.ai.AiDocumentFilterComposeActivity
import net.bible.android.view.activity.ai.AiModelsActivity
import net.bible.android.view.activity.ai.AiModelsComposeActivity
import net.bible.android.view.activity.ai.AiProvidersActivity
import net.bible.android.view.activity.ai.AiProvidersComposeActivity
import net.bible.android.view.activity.ai.AiPromptsComposeActivity
import net.bible.android.view.activity.ai.AiSettingsActivity
import net.bible.android.view.activity.ai.GlobalToolPermissionsActivity
import net.bible.android.view.activity.ai.GlobalToolPermissionsComposeActivity
import net.bible.android.view.activity.ai.PromptEditActivity
import net.bible.android.view.activity.ai.PromptEditComposeActivity
import net.bible.android.view.activity.ai.RawLlmLogActivity
import net.bible.android.view.activity.ai.RawLlmLogComposeActivity
import net.bible.android.view.activity.ai.RawLogHistoryActivity
import net.bible.android.view.activity.ai.RawLogHistoryComposeActivity
import net.bible.android.view.activity.ai.ToolInfoActivity
import net.bible.android.view.activity.ai.ToolInfoComposeActivity
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.bookmark.BookmarksComposeActivity
import net.bible.android.view.activity.bookmark.LabelEditActivity
import net.bible.android.view.activity.bookmark.LabelEditComposeActivity
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.ManageLabelsComposeActivity
import net.bible.android.view.activity.cloud.CloudDocumentsActivity
import net.bible.android.view.activity.cloud.CloudDocumentsComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWord
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.DownloadComposeActivity
import net.bible.android.view.activity.download.FirstDownload
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.navigation.HistoryComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.mydocuments.MyDocumentsActivity
import net.bible.android.view.activity.mydocuments.MyDocumentPagesActivity
import net.bible.android.view.mydocuments.MyDocumentsComposeActivity
import net.bible.android.view.mydocuments.MyDocumentPagesComposeActivity
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.android.view.activity.readingplan.DailyReadingComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingList
import net.bible.android.view.activity.readingplan.DailyReadingListComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList
import net.bible.android.view.activity.progress.ReadingProgressActivity
import net.bible.android.view.activity.progress.ReadingProgressComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressSettingsActivity
import net.bible.android.view.activity.settings.ReadingProgressSettingsComposeActivity
import net.bible.android.view.activity.search.EpubSearch
import net.bible.android.view.activity.search.EpubSearchComposeActivity
import net.bible.android.view.activity.search.EpubSearchResults
import net.bible.android.view.activity.search.EpubSearchResultsComposeActivity
import net.bible.android.view.activity.search.Search
import net.bible.android.view.activity.search.SearchComposeActivity
import net.bible.android.view.activity.search.SearchIndex
import net.bible.android.view.activity.search.SearchIndexComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressStatus
import net.bible.android.view.activity.search.SearchResults
import net.bible.android.view.activity.search.SearchResultsComposeActivity
import net.bible.android.view.activity.settings.SettingsActivity
import net.bible.android.view.activity.settings.SettingsComposeActivity
import net.bible.android.view.activity.settings.SyncSettingsActivity
import net.bible.android.view.activity.settings.SyncSettingsComposeActivity
import net.bible.android.view.activity.StartupActivity
import net.bible.android.view.activity.StartupComposeActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.BibleSpeakComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ScreenLauncherTest {
    @After
    fun tearDown() {
        CommonUtils.settings.removeBoolean("use_compose_ui")
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun default_off_routes_to_old() {
        CommonUtils.settings.removeBoolean("use_compose_ui")
        assertFalse(ScreenLauncher.useComposeFor(Screen.Calculator))
    }

    @Test
    fun flag_on_routes_to_new() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertTrue(ScreenLauncher.useComposeFor(Screen.Calculator))
    }

    @Test
    fun history_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(HistoryComposeActivity::class.java, ScreenLauncher.targetFor(Screen.History))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(History::class.java, ScreenLauncher.targetFor(Screen.History))
    }

    @Test
    fun searchIndexProgress_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(SearchIndexProgressComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SearchIndexProgress))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(SearchIndexProgressStatus::class.java, ScreenLauncher.targetFor(Screen.SearchIndexProgress))
    }

    @Test
    fun readingPlanSelector_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ReadingPlanSelectorComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingPlanSelector))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ReadingPlanSelectorList::class.java, ScreenLauncher.targetFor(Screen.ReadingPlanSelector))
    }

    @Test
    fun dailyReadingList_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(DailyReadingListComposeActivity::class.java, ScreenLauncher.targetFor(Screen.DailyReadingList))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(DailyReadingList::class.java, ScreenLauncher.targetFor(Screen.DailyReadingList))
    }

    @Test
    fun readingPlan_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(DailyReadingComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingPlan))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(DailyReading::class.java, ScreenLauncher.targetFor(Screen.ReadingPlan))
    }

    @Test
    fun chooseGeneralBookKey_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ChooseGeneralBookKeyComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseGeneralBookKey))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ChooseGeneralBookKey::class.java, ScreenLauncher.targetFor(Screen.ChooseGeneralBookKey))
    }

    @Test
    fun chooseMapKey_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ChooseMapKeyComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseMapKey))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ChooseMapKey::class.java, ScreenLauncher.targetFor(Screen.ChooseMapKey))
    }

    @Test
    fun chooseDictionaryWord_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ChooseDictionaryWordComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseDictionaryWord))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ChooseDictionaryWord::class.java, ScreenLauncher.targetFor(Screen.ChooseDictionaryWord))
    }

    @Test
    fun chooseDocument_routes_old_and_new() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ChooseDocumentComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseDocument))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ChooseDocument::class.java, ScreenLauncher.targetFor(Screen.ChooseDocument))
    }

    @Test
    fun gridChoosePassageBook_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(GridChoosePassageComposeActivity::class.java, ScreenLauncher.targetFor(Screen.GridChoosePassageBook))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(GridChoosePassageBook::class.java, ScreenLauncher.targetFor(Screen.GridChoosePassageBook))
    }

    @Test
    fun download_routes_old_and_new() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(DownloadComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Download))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(DownloadActivity::class.java, ScreenLauncher.targetFor(Screen.Download))
    }

    @Test
    fun firstDownload_routes_old_and_new() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        // new: same DownloadComposeActivity (the firstDownload extra differentiates behaviour)
        assertEquals(DownloadComposeActivity::class.java, ScreenLauncher.targetFor(Screen.FirstDownload))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(FirstDownload::class.java, ScreenLauncher.targetFor(Screen.FirstDownload))
    }

    @Test
    fun search_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(SearchComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Search))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(Search::class.java, ScreenLauncher.targetFor(Screen.Search))
    }

    @Test
    fun searchIndex_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(SearchIndexComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SearchIndex))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(SearchIndex::class.java, ScreenLauncher.targetFor(Screen.SearchIndex))
    }

    @Test
    fun searchResults_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(SearchResultsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SearchResults))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(SearchResults::class.java, ScreenLauncher.targetFor(Screen.SearchResults))
    }

    @Test
    fun epubSearch_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(EpubSearchComposeActivity::class.java, ScreenLauncher.targetFor(Screen.EpubSearch))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(EpubSearch::class.java, ScreenLauncher.targetFor(Screen.EpubSearch))
    }

    @Test
    fun epubSearchResults_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(EpubSearchResultsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.EpubSearchResults))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(EpubSearchResults::class.java, ScreenLauncher.targetFor(Screen.EpubSearchResults))
    }

    @Test
    fun myDocuments_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(MyDocumentsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.MyDocuments))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(MyDocumentsActivity::class.java, ScreenLauncher.targetFor(Screen.MyDocuments))
    }

    @Test
    fun myDocumentPages_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(MyDocumentPagesComposeActivity::class.java, ScreenLauncher.targetFor(Screen.MyDocumentPages))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(MyDocumentPagesActivity::class.java, ScreenLauncher.targetFor(Screen.MyDocumentPages))
    }

    @Test
    fun cloudDocuments_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(CloudDocumentsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.CloudDocuments))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(CloudDocumentsActivity::class.java, ScreenLauncher.targetFor(Screen.CloudDocuments))
    }

    @Test
    fun bibleSpeak_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(BibleSpeakComposeActivity::class.java, ScreenLauncher.targetFor(Screen.BibleSpeak))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(BibleSpeakActivity::class.java, ScreenLauncher.targetFor(Screen.BibleSpeak))
    }

    @Test
    fun workspaceSelector_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(WorkspaceSelectorComposeActivity::class.java, ScreenLauncher.targetFor(Screen.WorkspaceSelector))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(WorkspaceSelectorActivity::class.java, ScreenLauncher.targetFor(Screen.WorkspaceSelector))
    }

    @Test
    fun aiConnectionSettings_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(AiConnectionSettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiConnectionSettings))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(AiConnectionSettingsActivity::class.java, ScreenLauncher.targetFor(Screen.AiConnectionSettings))
    }

    @Test
    fun aiProviders_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(AiProvidersComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiProviders))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(AiProvidersActivity::class.java, ScreenLauncher.targetFor(Screen.AiProviders))
    }

    @Test
    fun aiModels_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(AiModelsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiModels))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(AiModelsActivity::class.java, ScreenLauncher.targetFor(Screen.AiModels))
    }

    @Test
    fun aiPrompts_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(AiPromptsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiPrompts))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(AiSettingsActivity::class.java, ScreenLauncher.targetFor(Screen.AiPrompts))
    }

    @Test
    fun promptEdit_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(PromptEditComposeActivity::class.java, ScreenLauncher.targetFor(Screen.PromptEdit))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(PromptEditActivity::class.java, ScreenLauncher.targetFor(Screen.PromptEdit))
    }

    @Test
    fun globalToolPermissions_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(GlobalToolPermissionsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.GlobalToolPermissions))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(GlobalToolPermissionsActivity::class.java, ScreenLauncher.targetFor(Screen.GlobalToolPermissions))
    }

    @Test
    fun toolInfo_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ToolInfoComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ToolInfo))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ToolInfoActivity::class.java, ScreenLauncher.targetFor(Screen.ToolInfo))
    }

    @Test
    fun aiDocumentFilter_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(AiDocumentFilterComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiDocumentFilter))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(AiDocumentFilterActivity::class.java, ScreenLauncher.targetFor(Screen.AiDocumentFilter))
    }

    @Test
    fun rawLogHistory_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(RawLogHistoryComposeActivity::class.java, ScreenLauncher.targetFor(Screen.RawLogHistory))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(RawLogHistoryActivity::class.java, ScreenLauncher.targetFor(Screen.RawLogHistory))
    }

    @Test
    fun rawLlmLog_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(RawLlmLogComposeActivity::class.java, ScreenLauncher.targetFor(Screen.RawLlmLog))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(RawLlmLogActivity::class.java, ScreenLauncher.targetFor(Screen.RawLlmLog))
    }

    @Test
    fun labelEdit_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(LabelEditComposeActivity::class.java, ScreenLauncher.targetFor(Screen.LabelEdit))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(LabelEditActivity::class.java, ScreenLauncher.targetFor(Screen.LabelEdit))
    }

    @Test
    fun manageLabels_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ManageLabelsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ManageLabels))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ManageLabels::class.java, ScreenLauncher.targetFor(Screen.ManageLabels))
    }

    @Test
    fun bookmarks_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(BookmarksComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Bookmarks))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(Bookmarks::class.java, ScreenLauncher.targetFor(Screen.Bookmarks))
    }

    @Test
    fun readingProgress_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ReadingProgressComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingProgress))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ReadingProgressActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingProgress))
    }

    @Test
    fun settingsRoutesToComposeWhenFlagOn() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(SettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Settings))
    }

    @Test
    fun settingsRoutesToClassicWhenFlagOff() {
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(SettingsActivity::class.java, ScreenLauncher.targetFor(Screen.Settings))
    }

    @Test
    fun readingProgressSettings_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(ReadingProgressSettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingProgressSettings))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(ReadingProgressSettingsActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingProgressSettings))
    }

    @Test
    fun syncSettings_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(SyncSettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SyncSettings))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(SyncSettingsActivity::class.java, ScreenLauncher.targetFor(Screen.SyncSettings))
    }

    @Test
    fun startup_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(StartupComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Startup))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(StartupActivity::class.java, ScreenLauncher.targetFor(Screen.Startup))
    }

    @Test
    fun installZip_routes_by_flag() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertEquals(InstallZipComposeActivity::class.java, ScreenLauncher.targetFor(Screen.InstallZip))
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertEquals(InstallZip::class.java, ScreenLauncher.targetFor(Screen.InstallZip))
    }
}
