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

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.backup.BackupComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.StartupComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ScreenLauncherTest {
    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // The thirteen tests below were converted from `targetFor(Screen.X) == XComposeActivity::class.java`
    // by nav-graph 3/5/6 Task 9, which deleted all thirteen reading-plan/search/settings host
    // Activities outright (targetFor throws for these Screens now -- see
    // ScreenLauncher.targetForMigratedScreen). Ten assert the MIGRATED route; the three whose
    // Screen is deliberately absent from MIGRATED assert that absence plus a throwing intentFor.

    @Test
    fun searchIndexProgress_isNotMigrated_argumentLessRouteHasNoSafeMeaning() {
        // An argument-free index-progress route watches nothing and can route nowhere when
        // indexing ends, so Screen.SearchIndexProgress is deliberately absent from MIGRATED; with
        // the classic Activity gone, intentFor falls through to targetFor and throws.
        // See NavHostRoutingGuardTest.searchIndexProgressIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.SearchIndexProgress))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.SearchIndexProgress) }
    }

    @Test
    fun readingPlanSelector_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.READING_PLAN_SELECTOR, ScreenLauncher.MIGRATED[Screen.ReadingPlanSelector])
    }

    @Test
    fun dailyReadingList_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.READING_PLAN_DAY_LIST, ScreenLauncher.MIGRATED[Screen.DailyReadingList])
    }

    @Test
    fun readingPlan_routes_to_the_nav_graph() {
        // The ARGUMENT-FREE daily-reading route: "no plan, no day" means "the current plan's
        // current day", which is a real state rather than a dropped argument.
        assertEquals(NavRoutes.dailyReading(), ScreenLauncher.MIGRATED[Screen.ReadingPlan])
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
    fun download_isNotMigrated_fiveArgumentRouteHasNoSafeArgumentFreeForm() {
        // NavRoutes.download(...) carries five arguments, every one of which some classic launch
        // site attached; an argument-free MIGRATED entry could not carry them, and classic
        // DownloadComposeActivity is gone (nav-graph slice 4 Task 9), so targetFor throws too. See
        // NavHostRoutingGuardTest.downloadIsNotInMigratedAndItsPatternIsRegisteredByAGraph.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.Download))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.Download) }
    }

    @Test
    fun search_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.searchForm(), ScreenLauncher.MIGRATED[Screen.Search])
    }

    @Test
    fun searchIndex_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.searchIndex(), ScreenLauncher.MIGRATED[Screen.SearchIndex])
    }

    @Test
    fun searchResults_isNotMigrated_argumentLessRouteHasNoSafeMeaning() {
        // A results route with no searchText has nothing to search for.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.SearchResults))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.SearchResults) }
    }

    @Test
    fun epubSearch_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.EPUB_SEARCH, ScreenLauncher.MIGRATED[Screen.EpubSearch])
    }

    @Test
    fun epubSearchResults_isNotMigrated_routeRequiresASearchText() {
        // NavRoutes.epubSearchResults takes searchText as a NON-NULL parameter: there is no
        // argument-free form of this route even in principle.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.EpubSearchResults))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.EpubSearchResults) }
    }

    // Converted from `targetFor(Screen.MyDocuments) == MyDocumentsComposeActivity::class.java` to
    // the MIGRATED[Screen.X] shape by nav-graph slice 4 Task 6, the same conversion the nine AI
    // tests below document: `targetFor` now throws for this Screen (see
    // ScreenLauncher.targetForMigratedScreen), so the real contract is what MIGRATED resolves it to.
    @Test
    fun myDocuments_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.myDocuments(), ScreenLauncher.MIGRATED[Screen.MyDocuments])
    }

    @Test
    fun myDocumentPages_isNotMigrated_threeArgumentRouteHasNoSafeArgumentFreeForm() {
        // All three of NavRoutes.myDocumentPages(...)'s arguments are required, and classic
        // MyDocumentPagesComposeActivity is gone (nav-graph slice 4 Task 9), so targetFor throws
        // too. See NavHostRoutingGuardTest.myDocumentPagesIsNotInMigratedAndItsPatternIsRegisteredByAGraph.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.MyDocumentPages))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.MyDocumentPages) }
    }

    @Test
    fun cloudDocuments_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.cloudDocuments(), ScreenLauncher.MIGRATED[Screen.CloudDocuments])
    }

    @Test
    fun workspaceSelector_routes_to_compose() {
        assertEquals(
            WorkspaceSelectorComposeActivity::class.java,
            ScreenLauncher.targetFor(Screen.WorkspaceSelector),
        )
    }

    // The nine tests below were converted from `targetFor(Screen.X) == XComposeActivity::class.java`
    // to the MIGRATED[Screen.X] shape by nav-graph Task 10, which deleted all ten classic AI-cluster
    // ComposeActivity classes outright (targetFor throws for these Screens now -- see
    // ScreenLauncher.targetForMigratedScreen). `toolInfo_routes_to_the_nav_graph` already used
    // this shape from an earlier task (slice 1) and needed no change.

    @Test
    fun aiConnectionSettings_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.AI_CONNECTION_SETTINGS, ScreenLauncher.MIGRATED[Screen.AiConnectionSettings])
    }

    @Test
    fun aiProviders_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.aiProviders(startEasySetup = false), ScreenLauncher.MIGRATED[Screen.AiProviders])
    }

    @Test
    fun aiModels_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.AI_MODELS, ScreenLauncher.MIGRATED[Screen.AiModels])
    }

    @Test
    fun aiPrompts_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.AI_PROMPTS, ScreenLauncher.MIGRATED[Screen.AiPrompts])
    }

    @Test
    fun promptEdit_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.promptEdit(), ScreenLauncher.MIGRATED[Screen.PromptEdit])
    }

    @Test
    fun globalToolPermissions_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS, ScreenLauncher.MIGRATED[Screen.GlobalToolPermissions])
    }

    @Test
    fun toolInfo_routes_to_the_nav_graph() {
        // Migrated in the nav-graph phase's slice 1; targetFor no longer answers for it.
        assertEquals(NavRoutes.AI_TOOL_INFO, ScreenLauncher.MIGRATED[Screen.ToolInfo])
    }

    @Test
    fun aiDocumentFilter_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.AI_DOCUMENT_FILTER, ScreenLauncher.MIGRATED[Screen.AiDocumentFilter])
    }

    @Test
    fun rawLogHistory_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.AI_RAW_LOG_HISTORY, ScreenLauncher.MIGRATED[Screen.RawLogHistory])
    }

    @Test
    fun rawLlmLog_isNotMigrated_argumentLessRouteHasNoSafeMeaning() {
        // Whole-branch review M2: unlike Screen.PromptEdit's argument-less "new prompt" meaning,
        // an argument-less RawLlmLog route has no safe interpretation (both ids null just renders
        // an empty screen), so it is deliberately absent from MIGRATED rather than mapped to
        // NavRoutes.rawLlmLog(). See NavHostRoutingGuardTest.rawLlmLogIsNotInMigratedAndIntentForThrows.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.RawLlmLog))
    }

    // The three tests below were converted from `targetFor(Screen.X) == XComposeActivity::class.java`
    // to the MIGRATED-map / throwing shape by nav-graph slices 2+4 Task 7, which deleted the classic
    // Bookmarks/ManageLabels/LabelEdit ComposeActivity classes (targetFor throws for these Screens
    // now -- see ScreenLauncher.targetForMigratedScreen).

    @Test
    fun labelEdit_isNotMigrated_dataArgumentIsRequired() {
        // NavRoutes.labelEdit(data) takes the whole LabelEditContract.LabelData payload as a
        // NON-NULL parameter: there is no argument-free form of this route even in principle.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.LabelEdit))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.LabelEdit) }
    }

    @Test
    fun manageLabels_isNotMigrated_dataArgumentIsRequired() {
        // NavRoutes.manageLabels(data) takes the whole ManageLabelsContract.ManageLabelsData
        // payload as a NON-NULL parameter, and its `mode` field is what decides WHICH of the four
        // screens is drawn -- an argument-free route could not even pick one.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.ManageLabels))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.ManageLabels) }
    }

    @Test
    fun bookmarks_routes_to_the_nav_graph() {
        // Unlike LabelEdit/ManageLabels, NavRoutes.BOOKMARKS_PATTERN's `labelNo` argument is
        // OPTIONAL and its absence is a real state ("no label filter"), so Bookmarks IS migrated.
        assertEquals(NavRoutes.bookmarks(), ScreenLauncher.MIGRATED[Screen.Bookmarks])
    }

    @Test
    fun readingProgress_routes_to_the_nav_graph() {
        // The ARGUMENT-FREE reading-progress route: an absent tab means "the tab the user was last
        // on", which the screen resolves from the persisted setting.
        assertEquals(NavRoutes.readingProgress(), ScreenLauncher.MIGRATED[Screen.ReadingProgress])
    }

    @Test
    fun settings_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.SETTINGS, ScreenLauncher.MIGRATED[Screen.Settings])
    }

    @Test
    fun readingProgressSettings_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.READING_PROGRESS_SETTINGS, ScreenLauncher.MIGRATED[Screen.ReadingProgressSettings])
    }

    @Test
    fun syncSettings_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.SYNC_SETTINGS, ScreenLauncher.MIGRATED[Screen.SyncSettings])
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
    fun customRepositories_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.customRepositories(), ScreenLauncher.MIGRATED[Screen.CustomRepositories])
    }

    @Test
    fun customRepositoryEditor_isNotMigrated_optionalIdOnlyMeaningfulInGraphContext() {
        // NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN's repositoryId is OPTIONAL, but its absence
        // means "new repository" -- a meaning only the CustomRepositories arm's own in-graph
        // navigate(customRepositoryEditor(null)) call may supply deliberately. Classic
        // CustomRepositoryEditorComposeActivity is gone (nav-graph slice 4 Task 9), so targetFor
        // throws too. See
        // NavHostRoutingGuardTest.customRepositoryEditorIsNotInMigratedAndItsPatternIsRegisteredByAGraph.
        assertFalse(ScreenLauncher.MIGRATED.containsKey(Screen.CustomRepositoryEditor))
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.CustomRepositoryEditor) }
    }

    @Test
    fun backup_routes_to_compose() {
        assertEquals(BackupComposeActivity::class.java, ScreenLauncher.targetFor(Screen.Backup))
    }

    @Test
    fun backup_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.BACKUP, ScreenLauncher.MIGRATED[Screen.Backup])
    }

    @Test
    fun progressStatus_routes_to_the_nav_graph() {
        assertEquals(NavRoutes.progressStatus(), ScreenLauncher.MIGRATED[Screen.ProgressStatus])
    }

    // ——— slice 8 B7: the seven slice-7 screens route to the nav graph ———————————————————————————
    // targetFor still names their Activities until Task F6 deletes them; MIGRATED is what intentFor reads.

    @Test
    fun theSevenSliceSevenScreensRouteToTheNavGraph() {
        assertEquals(NavRoutes.CHOOSE_GENERAL_BOOK_KEY, ScreenLauncher.MIGRATED[Screen.ChooseGeneralBookKey])
        assertEquals(NavRoutes.CHOOSE_MAP_KEY, ScreenLauncher.MIGRATED[Screen.ChooseMapKey])
        assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, ScreenLauncher.MIGRATED[Screen.ChooseDictionaryWord])
        assertEquals(NavRoutes.gridChoosePassage(), ScreenLauncher.MIGRATED[Screen.GridChoosePassageBook])
        assertEquals(NavRoutes.chooseDocument(), ScreenLauncher.MIGRATED[Screen.ChooseDocument])
        assertEquals(NavRoutes.WORKSPACE_SELECTOR, ScreenLauncher.MIGRATED[Screen.WorkspaceSelector])
        assertEquals(NavRoutes.textDisplaySettings(), ScreenLauncher.MIGRATED[Screen.TextDisplaySettings])
    }
}
