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
import net.bible.android.view.activity.ai.AiConnectionSettingsComposeActivity
import net.bible.android.view.activity.ai.AiDocumentFilterComposeActivity
import net.bible.android.view.activity.ai.AiModelsComposeActivity
import net.bible.android.view.activity.ai.AiProvidersComposeActivity
import net.bible.android.view.activity.ai.AiPromptsComposeActivity
import net.bible.android.view.activity.ai.GlobalToolPermissionsComposeActivity
import net.bible.android.view.activity.ai.PromptEditComposeActivity
import net.bible.android.view.activity.ai.RawLlmLogComposeActivity
import net.bible.android.view.activity.ai.RawLogHistoryComposeActivity
import net.bible.android.view.activity.ai.ToolInfoComposeActivity
import net.bible.android.view.activity.backup.BackupComposeActivity
import net.bible.android.view.activity.bookmark.BookmarksComposeActivity
import net.bible.android.view.activity.bookmark.LabelEditComposeActivity
import net.bible.android.view.activity.bookmark.ManageLabelsComposeActivity
import net.bible.android.view.activity.cloud.CloudDocumentsComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.download.CustomRepositoriesComposeActivity
import net.bible.android.view.activity.download.CustomRepositoryEditorComposeActivity
import net.bible.android.view.activity.download.DownloadComposeActivity
import net.bible.android.view.activity.download.ProgressStatusComposeActivity
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.mydocuments.MyDocumentsComposeActivity
import net.bible.android.view.mydocuments.MyDocumentPagesComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingListComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressComposeActivity
import net.bible.android.view.activity.settings.ReadingProgressSettingsComposeActivity
import net.bible.android.view.activity.search.EpubSearchComposeActivity
import net.bible.android.view.activity.search.EpubSearchResultsComposeActivity
import net.bible.android.view.activity.search.SearchComposeActivity
import net.bible.android.view.activity.search.SearchIndexComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressComposeActivity
import net.bible.android.view.activity.search.SearchResultsComposeActivity
import net.bible.android.view.activity.settings.SettingsComposeActivity
import net.bible.android.view.activity.settings.SyncSettingsComposeActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.StartupComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ScreenLauncherTest {
    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun searchIndexProgress_routes_to_compose() {
        assertEquals(SearchIndexProgressComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SearchIndexProgress))
    }

    @Test
    fun readingPlanSelector_routes_to_compose() {
        assertEquals(ReadingPlanSelectorComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingPlanSelector))
    }

    @Test
    fun dailyReadingList_routes_to_compose() {
        assertEquals(DailyReadingListComposeActivity::class.java, ScreenLauncher.targetFor(Screen.DailyReadingList))
    }

    @Test
    fun readingPlan_routes_to_compose() {
        assertEquals(DailyReadingComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingPlan))
    }

    @Test
    fun chooseGeneralBookKey_routes_to_compose() {
        assertEquals(ChooseGeneralBookKeyComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseGeneralBookKey))
    }

    @Test
    fun chooseMapKey_routes_to_compose() {
        assertEquals(ChooseMapKeyComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseMapKey))
    }

    @Test
    fun chooseDictionaryWord_routes_to_compose() {
        assertEquals(ChooseDictionaryWordComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseDictionaryWord))
    }

    @Test
    fun chooseDocument_routes_to_compose() {
        assertEquals(ChooseDocumentComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ChooseDocument))
    }

    @Test
    fun gridChoosePassageBook_routes_to_compose() {
        assertEquals(GridChoosePassageComposeActivity::class.java, ScreenLauncher.targetFor(Screen.GridChoosePassageBook))
    }

    @Test
    fun download_routes_to_compose() {
        assertEquals(DownloadComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Download))
    }

    @Test
    fun firstDownload_routes_to_compose() {
        // same DownloadComposeActivity as Screen.Download (the firstDownload extra, added by the
        // caller, differentiates behaviour)
        assertEquals(DownloadComposeActivity::class.java, ScreenLauncher.targetFor(Screen.FirstDownload))
    }

    @Test
    fun search_routes_to_compose() {
        assertEquals(SearchComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Search))
    }

    @Test
    fun searchIndex_routes_to_compose() {
        assertEquals(SearchIndexComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SearchIndex))
    }

    @Test
    fun searchResults_routes_to_compose() {
        assertEquals(SearchResultsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SearchResults))
    }

    @Test
    fun epubSearch_routes_to_compose() {
        assertEquals(EpubSearchComposeActivity::class.java, ScreenLauncher.targetFor(Screen.EpubSearch))
    }

    @Test
    fun epubSearchResults_routes_to_compose() {
        assertEquals(EpubSearchResultsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.EpubSearchResults))
    }

    @Test
    fun myDocuments_routes_to_compose() {
        assertEquals(MyDocumentsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.MyDocuments))
    }

    @Test
    fun myDocumentPages_routes_to_compose() {
        assertEquals(MyDocumentPagesComposeActivity::class.java, ScreenLauncher.targetFor(Screen.MyDocumentPages))
    }

    @Test
    fun cloudDocuments_routes_to_compose() {
        assertEquals(CloudDocumentsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.CloudDocuments))
    }

    @Test
    fun workspaceSelector_routes_to_compose() {
        assertEquals(
            WorkspaceSelectorComposeActivity::class.java,
            ScreenLauncher.targetFor(Screen.WorkspaceSelector),
        )
    }

    @Test
    fun aiConnectionSettings_routes_to_compose() {
        assertEquals(AiConnectionSettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiConnectionSettings))
    }

    @Test
    fun aiProviders_routes_to_compose() {
        assertEquals(AiProvidersComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiProviders))
    }

    @Test
    fun aiModels_routes_to_compose() {
        assertEquals(AiModelsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiModels))
    }

    @Test
    fun aiPrompts_routes_to_compose() {
        assertEquals(AiPromptsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiPrompts))
    }

    @Test
    fun promptEdit_routes_to_compose() {
        assertEquals(PromptEditComposeActivity::class.java, ScreenLauncher.targetFor(Screen.PromptEdit))
    }

    @Test
    fun globalToolPermissions_routes_to_compose() {
        assertEquals(GlobalToolPermissionsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.GlobalToolPermissions))
    }

    @Test
    fun toolInfo_routes_to_compose() {
        assertEquals(ToolInfoComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ToolInfo))
    }

    @Test
    fun aiDocumentFilter_routes_to_compose() {
        assertEquals(AiDocumentFilterComposeActivity::class.java, ScreenLauncher.targetFor(Screen.AiDocumentFilter))
    }

    @Test
    fun rawLogHistory_routes_to_compose() {
        assertEquals(RawLogHistoryComposeActivity::class.java, ScreenLauncher.targetFor(Screen.RawLogHistory))
    }

    @Test
    fun rawLlmLog_routes_to_compose() {
        assertEquals(RawLlmLogComposeActivity::class.java, ScreenLauncher.targetFor(Screen.RawLlmLog))
    }

    @Test
    fun labelEdit_routes_to_compose() {
        assertEquals(LabelEditComposeActivity::class.java, ScreenLauncher.targetFor(Screen.LabelEdit))
    }

    @Test
    fun manageLabels_routes_to_compose() {
        assertEquals(ManageLabelsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ManageLabels))
    }

    @Test
    fun bookmarks_routes_to_compose() {
        assertEquals(BookmarksComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Bookmarks))
    }

    @Test
    fun readingProgress_routes_to_compose() {
        assertEquals(ReadingProgressComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingProgress))
    }

    @Test
    fun settings_routes_to_compose() {
        assertEquals(SettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Settings))
    }

    @Test
    fun readingProgressSettings_routes_to_compose() {
        assertEquals(ReadingProgressSettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ReadingProgressSettings))
    }

    @Test
    fun syncSettings_routes_to_compose() {
        assertEquals(SyncSettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.SyncSettings))
    }

    @Test
    fun startup_routes_to_compose() {
        assertEquals(StartupComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Startup))
    }

    @Test
    fun installZip_routes_to_compose() {
        assertEquals(InstallZipComposeActivity::class.java, ScreenLauncher.targetFor(Screen.InstallZip))
    }

    @Test
    fun textDisplaySettings_routes_to_compose() {
        assertEquals(TextDisplaySettingsComposeActivity::class.java, ScreenLauncher.targetFor(Screen.TextDisplaySettings))
    }

    @Test
    fun customRepositories_routes_to_compose() {
        assertEquals(CustomRepositoriesComposeActivity::class.java, ScreenLauncher.targetFor(Screen.CustomRepositories))
    }

    @Test
    fun customRepositoryEditor_routes_to_compose() {
        assertEquals(CustomRepositoryEditorComposeActivity::class.java, ScreenLauncher.targetFor(Screen.CustomRepositoryEditor))
    }

    @Test
    fun backup_routes_to_compose() {
        assertEquals(BackupComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Backup))
    }

    @Test
    fun progressStatus_routes_to_compose() {
        assertEquals(ProgressStatusComposeActivity::class.java, ScreenLauncher.targetFor(Screen.ProgressStatus))
    }
}
