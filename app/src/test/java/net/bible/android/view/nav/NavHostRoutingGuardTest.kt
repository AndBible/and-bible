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

package net.bible.android.view.nav

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.compose.ClassicRemovalScan
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

/**
 * The coexistence seam's contract: a MIGRATED screen resolves to the nav host carrying its route,
 * an unmigrated one still resolves to its own Activity, and no screen is both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class NavHostRoutingGuardTest {

    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aMigratedScreenResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.ToolInfo)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_TOOL_INFO, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiDocumentFilterResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiDocumentFilter)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_DOCUMENT_FILTER, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun globalToolPermissionsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.GlobalToolPermissions)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiModelsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiModels)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_MODELS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiConnectionSettingsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiConnectionSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.AI_CONNECTION_SETTINGS,
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aiProvidersResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiProviders)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.aiProviders(startEasySetup = false),
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aiProvidersEasySetupRouteCarriesTheArgument() {
        val plainRoute = NavRoutes.aiProviders(startEasySetup = false)
        val easySetupRoute = NavRoutes.aiProviders(startEasySetup = true)
        assertTrue(
            easySetupRoute.contains("${NavRoutes.ARG_START_EASY_SETUP}=true"),
            "the easy-setup route must carry startEasySetup=true: $easySetupRoute",
        )
        assertTrue(
            plainRoute != easySetupRoute,
            "the easy-setup route must be distinguishable from the plain AiProviders route",
        )
    }

    @Test
    fun aiPromptsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiPrompts)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_PROMPTS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun promptEditResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.PromptEdit)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.promptEdit(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun rawLlmLogIsNotInMigratedAndIntentForThrows() {
        // Whole-branch review M2: unlike Screen.PromptEdit, an argument-less RawLlmLog route has
        // no safe meaning (both ids null renders an empty screen with no log), so it was dropped
        // from ScreenLauncher.MIGRATED rather than mapped to NavRoutes.rawLlmLog(). intentFor then
        // falls through to targetFor, whose targetForMigratedScreen guard throws loudly instead
        // of silently opening a blank screen.
        assertTrue(Screen.RawLlmLog !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.RawLlmLog) }
    }

    @Test
    fun rawLogHistoryResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.RawLogHistory)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_RAW_LOG_HISTORY, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun readingPlanSelectorResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingPlanSelector)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.READING_PLAN_SELECTOR, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun dailyReadingListResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.DailyReadingList)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.READING_PLAN_DAY_LIST, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun readingPlanResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The argument-free route is the correct MIGRATED value: the classic host branched on
        // extras.containsKey, so "no plan, no day" is a real state meaning "current plan day".
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingPlan)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.dailyReading(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun searchResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The argument-free search form is a real screen (classic `SearchComposeActivity` opened
        // with no extras is the empty Find screen), so it is a valid MIGRATED value. Callers that
        // DO know a query/search-type/section (HistoryManager's stored route) build
        // NavRoutes.searchForm(...) directly, bypassing this map.
        val intent = ScreenLauncher.intentFor(context, Screen.Search)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.searchForm(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun searchIndexResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // Also a real screen without arguments: classic `SearchIndexComposeActivity.kt:46` falls
        // back to the CURRENT PAGE's document when SEARCH_DOCUMENT is absent, so an argument-free
        // index prompt has a defined meaning ("index the book I am reading").
        val intent = ScreenLauncher.intentFor(context, Screen.SearchIndex)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.searchIndex(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * The slice-5 twin of [rawLlmLogIsNotInMigratedAndIntentForThrows], and the reason
     * `Screen.SearchIndexProgress` is deliberately absent from [ScreenLauncher.MIGRATED] even
     * though its destination now exists in the graph: an ARGUMENT-FREE index-progress route has no
     * safe meaning. Every real edge into that screen (classic
     * `SearchIndexComposeActivity.kt:72`, now the graph's `SEARCH_INDEX_PATTERN` arm) builds a
     * route carrying the chain's five arguments; a bare `ScreenLauncher.open(ctx,
     * Screen.SearchIndexProgress)` would open a progress screen watching nothing, which then has
     * no document to route onward to when indexing completes.
     *
     * Task 9 deleted the six search host Activities, so this now asserts exactly what the
     * AI-cluster twin does: absent from MIGRATED, and `intentFor` falls through to `targetFor`,
     * whose `targetForMigratedScreen` guard throws. (Between slice 5 and Task 9 it asserted the
     * weaker "never the nav host with an argument-free route", because the classic Activity was
     * still a legal fallback target; the guarantee that matters is unchanged, and is now stronger.)
     */
    @Test
    fun searchIndexProgressIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt() {
        assertTrue(Screen.SearchIndexProgress !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.SearchIndexProgress) }
    }

    @Test
    fun epubSearchResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The EPUB search FORM is argument-free by construction — classic
        // `EpubSearchComposeActivity` read no extras at all and took its document from the current
        // page — so `NavRoutes.EPUB_SEARCH` is the whole route and a valid MIGRATED value. (Its two
        // callers, `SearchControl.getSearchIntent` and classic `SearchIndexProgressComposeActivity`,
        // both build it with no extras.)
        val intent = ScreenLauncher.intentFor(context, Screen.EpubSearch)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.EPUB_SEARCH, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * A second slice-5 twin of [rawLlmLogIsNotInMigratedAndIntentForThrows] — see
     * [searchIndexProgressIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt] for the full reasoning
     * and for why Task 9 turned this into the `assertFailsWith` form.
     *
     * `Screen.SearchResults` specifically: a results route with no `searchText` has nothing to
     * search for. Every real edge builds `NavRoutes.searchResults(...)` with a query (the graph's
     * `SEARCH_FORM_PATTERN`/`SEARCH_INDEX_PROGRESS_PATTERN` arms; `BibleView` and `LinkControl`
     * since Task 6), bypassing this map.
     */
    @Test
    fun searchResultsIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt() {
        assertTrue(Screen.SearchResults !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.SearchResults) }
    }

    /**
     * The EPUB twin of [searchResultsIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt], and for the
     * same reason: `NavRoutes.EPUB_SEARCH_RESULTS_PATTERN`'s `searchText` is REQUIRED
     * (`NavRoutes.epubSearchResults` takes it as a non-null parameter), so there is no argument-free
     * route to map here even in principle. Every real edge builds one with a query — the graph's
     * `EPUB_SEARCH` arm on submit, and its `SEARCH_INDEX_PROGRESS_PATTERN` arm after an epub index
     * completes. Task 9 converted this to the `assertFailsWith<IllegalStateException>` form when it
     * deleted `EpubSearchResultsComposeActivity`.
     */
    @Test
    fun epubSearchResultsIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt() {
        assertTrue(Screen.EpubSearchResults !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.EpubSearchResults) }
    }

    @Test
    fun settingsResolvesToTheNavHostCarryingItsRoute() {
        // slice 6, Task 7. The REFRESH_DISPLAY_ON_FINISH edge (MenuCommandHandler.kt:182-186)
        // survives this move untouched: the intent still names an Activity — the nav host — so
        // `startActivityForResult(handlerIntent, REFRESH_DISPLAY_ON_FINISH)` still returns its
        // request code to MainBibleActivity.onActivityResult when the host finishes.
        val intent = ScreenLauncher.intentFor(context, Screen.Settings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.SETTINGS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun syncSettingsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.SyncSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.SYNC_SETTINGS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun readingProgressSettingsResolvesToTheNavHostCarryingItsRoute() {
        // slice 6, Task 8. No arguments at all: the classic host read no extras.
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingProgressSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.READING_PROGRESS_SETTINGS,
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun readingProgressResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The ARGUMENT-FREE route is the correct MIGRATED value, and argument-free MEANS something
        // here: classic ReadingProgressComposeActivity.kt:76-82 defaults an ABSENT
        // ReadingProgressKeys.EXTRA_TAB to the persisted `reading_progress_last_tab` setting, so
        // "no tab" is the real state "open the tab the user was last on" — not a dropped argument.
        // The one caller that DOES know a tab (BibleJavascriptInterface.openReadingProgress) builds
        // NavRoutes.readingProgress(tab) directly, bypassing this map.
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingProgress)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.readingProgress(),
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    /**
     * slice 2, Task 4 -- the cousin of [rawLlmLogIsNotInMigratedAndIntentForThrows]. Nav-graph
     * slices 2+4 Task 7 deleted the classic `LabelEditComposeActivity`, which is what makes the
     * full assertion possible now (Tasks 4-6 could only assert the absent-from-MIGRATED /
     * registered-pattern half, because `targetFor` still resolved to the real Activity then).
     *
     * `Screen.LabelEdit` is deliberately absent from [ScreenLauncher.MIGRATED] for exactly
     * `Screen.RawLlmLog`'s reason: [NavRoutes.LABEL_EDIT_PATTERN]'s `data` argument carries the
     * whole `LabelEditContract.LabelData` payload, so an argument-free route would open the editor
     * with no label to edit. Every real edge builds `NavRoutes.labelEdit(data)` instead -- the
     * graph's own `ManageLabels` arm is the only caller, in-graph.
     */
    @Test
    fun labelEditIsNotInMigratedAndItsPatternIsRegisteredByAGraph() {
        assertTrue(Screen.LabelEdit !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.LabelEdit) }
        val registered = registeredRoutePatterns()
        assertTrue(
            NavRoutes.LABEL_EDIT_PATTERN in registered,
            "no *NavGraph.kt registers NavRoutes.LABEL_EDIT_PATTERN, so nothing can navigate to the " +
                "label editor inside the graph. Registered: ${registered.sorted()}",
        )
    }

    /**
     * slice 2, Task 5 -- [labelEditIsNotInMigratedAndItsPatternIsRegisteredByAGraph]'s twin, and it
     * asserts exactly the same for the same reason. Nav-graph slices 2+4 Task 7 deleted the classic
     * `ManageLabelsComposeActivity`, which is what makes the `intentFor` throw assertion possible
     * now.
     *
     * `Screen.ManageLabels` is deliberately absent from [ScreenLauncher.MIGRATED]:
     * [NavRoutes.MANAGE_LABELS_PATTERN]'s `data` argument carries the whole
     * `ManageLabelsContract.ManageLabelsData` payload -- the MODE among other things -- so an
     * argument-free route could not even choose which of the four screens (StudyPads, assign,
     * workspace auto-assign, hide-labels) to draw, and `NavRoutes.manageLabels(data)` cannot be
     * called without one, its parameter being non-null. All eight consumption sites build the
     * payload as JSON: the six outside the bookmark cluster
     * (`MenuCommandHandler`/`OptionsMenuItems` x2/`BibleView`/`CurrentGeneralBookPage`/
     * `TextDisplaySettingsComposeActivity`) now build
     * `NavHostComposeActivity.intentFor(context, NavRoutes.manageLabels(data))` directly, bypassing
     * this map and `targetFor` entirely (Task 7); the two in the deleted `BookmarksComposeActivity`
     * became in-graph `navigate` calls in Task 6.
     */
    @Test
    fun manageLabelsIsNotInMigratedAndItsPatternIsRegisteredByAGraph() {
        assertTrue(Screen.ManageLabels !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.ManageLabels) }
        val registered = registeredRoutePatterns()
        assertTrue(
            NavRoutes.MANAGE_LABELS_PATTERN in registered,
            "no *NavGraph.kt registers NavRoutes.MANAGE_LABELS_PATTERN, so nothing can navigate to " +
                "the label manager inside the graph. Registered: ${registered.sorted()}",
        )
    }

    /**
     * slice 2, Task 5, the EXTERNAL entry mode's half that is assertable without composing a
     * `NavHost`: the payload the six outside callers hand to `ManageLabels` survives the trip
     * through the route argument byte for byte.
     *
     * This matters more here than for any earlier argument, because the payload is dense JSON --
     * braces, quotes, commas and (in a label name) arbitrary user text. Same shape, and the same
     * double-decode trap, as [promptEditTemplateRoundTripsThroughDecodeArg]: `NavRoutes.manageLabels`
     * encodes exactly once, and the arm reads `arguments` PLAINLY because the navigation library has
     * already decoded it by then. The manual `decodeArg` here stands in for that library step.
     */
    @Test
    fun manageLabelsRouteRoundTripsAJsonPayloadThroughItsArgument() {
        val payload = """{"mode":"HIDELABELS","selectedLabels":["a&b"],"name":"100% \"Rock\" — t\u00e4st"}"""
        val route = NavRoutes.manageLabels(payload)
        val encoded = route.substringAfter("${NavRoutes.ARG_MANAGE_LABELS_DATA}=").substringBefore("&")
        assertEquals(payload, NavRoutes.decodeArg(encoded))
    }

    @Test
    fun promptEditTemplateRoundTripsThroughDecodeArg() {
        // Free text, deliberately containing reserved/percent/unicode characters that would
        // corrupt the route if encodeArg/decodeArg were not both applied — see NavRoutes' kdoc.
        val freeText = "Rock & Roll: 100% <great> \"quoted\" — täst\nwith newline"
        val route = NavRoutes.promptEdit(template = freeText)
        val encoded = route.substringAfter("${NavRoutes.ARG_PROMPT_TEMPLATE}=").substringBefore("&")
        assertEquals(freeText, NavRoutes.decodeArg(encoded))
    }

    /**
     * Task 3 fix round 1, finding I3. `HistoryManager` re-launches a stored history intent through
     * `IntentHistoryItem.revertTo()` (`IntentHistoryItem.kt:59-65`) with
     * `FLAG_ACTIVITY_REORDER_TO_FRONT`, which matches on the **component** alone — it carries no
     * notion of this host's `EXTRA_ROUTE`. One component now serves every migrated cluster, so with
     * the default `standard` launch mode a reading-plan history entry could reorder an EXISTING
     * AI-cluster instance to the front, drop the stored route, and show a completely unrelated
     * screen. `singleTop` is what routes that re-launch into
     * [NavHostComposeActivity.onNewIntent], which navigates the live graph to the requested route
     * instead.
     *
     * A text scan of the manifest rather than a behavioural test: `launchMode` is a manifest-only
     * fact with no runtime accessor that a Robolectric unit test can read back, and the failure it
     * guards against (a silently wrong screen after a history revert) has nothing else watching it.
     */
    @Test
    fun theNavHostIsSingleTopSoAHistoryRevertCannotShowAnotherClustersScreen() {
        val manifest = java.io.File("src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "src/main/AndroidManifest.xml is missing — this guard would pass vacuously")

        val block = manifest.readText()
            .substringAfter("""android:name="${NavHostComposeActivity::class.java.name}"""", "")
            .substringBefore("/>")
        assertTrue(
            block.isNotBlank(),
            "no <activity> block for ${NavHostComposeActivity::class.java.name} in the manifest",
        )
        assertTrue(
            block.contains("""android:launchMode="singleTop""""),
            "the nav host must be singleTop so a FLAG_ACTIVITY_REORDER_TO_FRONT history revert " +
                "reaches onNewIntent with its EXTRA_ROUTE instead of silently reordering an " +
                "instance serving a different cluster. Block was:\n$block",
        )
    }

    /**
     * slice 2, Task 6. The ARGUMENT-FREE bookmarks route is the correct [ScreenLauncher.MIGRATED]
     * value, and argument-free MEANS something here -- which is why `Screen.Bookmarks` is in the map
     * at all, unlike its two cluster siblings: classic `BookmarksComposeActivity.kt:73-77` branches
     * on `intent.extras?.containsKey(BookmarkControl.LABEL_NO_EXTRA)`, so "no labelNo" is the real
     * state "no label filter" (filter index 0), not a dropped argument. The precedent is
     * [readingPlanResolvesToTheNavHostCarryingItsArgumentFreeRoute]: same optional-argument shape,
     * same reasoning.
     *
     * The one live caller (`MenuCommandHandler.kt:205`) passes no labelNo at all, so it goes through
     * this map; a caller that knew one would build `NavRoutes.bookmarks(labelNo)` directly.
     */
    @Test
    fun bookmarksResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.Bookmarks)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.bookmarks(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * nav-graph slice 4, Task 3. The ARGUMENT-FREE custom-repositories route is the correct
     * [ScreenLauncher.MIGRATED] value: [NavRoutes.CUSTOM_REPOSITORIES_PATTERN] takes no argument at
     * all, unlike its child editor -- same shape as [aMigratedScreenResolvesToTheNavHostCarryingItsRoute]
     * and its argument-free siblings. `DownloadComposeActivity.onCustomRepositories()` is the one
     * live caller, and it keeps working unchanged through this map (Task 3's coexistence seam).
     */
    @Test
    fun customRepositoriesResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.CustomRepositories)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.customRepositories(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * nav-graph slice 4, Task 3 -- [labelEditIsNotInMigratedAndItsPatternIsRegisteredByAGraph]'s
     * twin, now the FULL shape: `Screen.CustomRepositoryEditor` is deliberately absent from
     * [ScreenLauncher.MIGRATED], because [NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN]'s
     * `repositoryId` argument is OPTIONAL, so a bare [Screen] entry has no way to say which of
     * "edit this row" / "start a new one" it means -- the only real edge into it is
     * [net.bible.sharedui.download.nav.CustomRepositoriesDeps]'s own `navController.navigate(...)`
     * calls, in-graph.
     *
     * The `assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(...) }` half
     * [labelEditIsNotInMigratedAndItsPatternIsRegisteredByAGraph] also carries did NOT hold until
     * now: Task 3 through Task 8 kept classic `CustomRepositoryEditorComposeActivity` alive, so
     * `ScreenLauncher.targetFor` still resolved to it and `intentFor` returned a real Intent rather
     * than throwing. Task 9 deletes the classic Activity (mirroring `Screen.LabelEdit`'s own
     * deletion in nav-graph slices 2+4 Task 7) and points the `targetFor` arm at
     * `targetForMigratedScreen`, which is what makes the throw assertion below hold at last.
     */
    @Test
    fun customRepositoryEditorIsNotInMigratedAndItsPatternIsRegisteredByAGraph() {
        assertFalse(
            Screen.CustomRepositoryEditor in ScreenLauncher.MIGRATED,
            "the editor's route requires a payload, so an argument-free MIGRATED entry would be a lie",
        )
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.CustomRepositoryEditor) }
        assertTrue(
            NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN in registeredRoutePatterns(),
            "no graph registers the editor's pattern",
        )
    }

    /**
     * nav-graph slice 4, Task 4. The ARGUMENT-FREE progress-status route is correct:
     * `ProgressStatusComposeActivity` read no extras at all, and its only inbound edge is a
     * getActivity `PendingIntent` from `ProgressNotificationManager`, which cannot carry one
     * either -- same shape as [settingsResolvesToTheNavHostCarryingItsRoute] and
     * [customRepositoriesResolvesToTheNavHostCarryingItsRoute].
     */
    @Test
    fun progressStatusResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.ProgressStatus)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.progressStatus(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * The host is `launchMode="singleTop"`
     * ([theNavHostIsSingleTopSoAHistoryRevertCannotShowAnotherClustersScreen]), so a notification
     * tap while the host is already open lands in `onNewIntent` -> `navigateToRoute`, which needs
     * the pattern registered by SOME graph -- not just resolvable through [ScreenLauncher.MIGRATED].
     */
    @Test
    fun progressStatusPatternIsRegisteredByAGraph() {
        val registered = registeredRoutePatterns()
        assertTrue(
            NavRoutes.PROGRESS_STATUS_PATTERN in registered,
            "no *NavGraph.kt registers NavRoutes.PROGRESS_STATUS_PATTERN, so a notification tap " +
                "while the host is already open cannot navigate to it. Registered: ${registered.sorted()}",
        )
    }

    /**
     * nav-graph slice 4, Task 6. `Screen.MyDocuments` was the batch's last direct
     * `ScreenLauncher.targetFor` caller (`MyDocumentsComposeActivity.kt:215`, inside `openDocument`)
     * -- once it went into [ScreenLauncher.MIGRATED], `intentFor` never fell through to `targetFor`
     * for it, so that call became unreachable dead code rather than a live crash path, and Task 9
     * deleted both the class and the call along with it (`targetFor`'s own `Screen.MyDocuments` arm
     * now throws via `targetForMigratedScreen`; see [noDirectTargetForCallSurvivesInAppSrcMain]).
     * The ARGUMENT-FREE route is correct for the same reason as
     * [customRepositoriesResolvesToTheNavHostCarryingItsRoute]: classic `MyDocumentsComposeActivity`
     * read no extras of its own, and `NavRoutes.MY_DOCUMENTS_PATTERN` takes none either.
     */
    @Test
    fun myDocumentsResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.MyDocuments)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.myDocuments(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * nav-graph slice 4, Task 8 -- the batch's LAST destination. `NavRoutes.CLOUD_DOCUMENTS_PATTERN`
     * takes no argument at all (Task 2), and classic `CloudDocumentsComposeActivity` read none
     * either, so the ARGUMENT-FREE route is the correct [ScreenLauncher.MIGRATED] value -- the same
     * shape as [myDocumentsResolvesToTheNavHostCarryingItsArgumentFreeRoute] and
     * [customRepositoriesResolvesToTheNavHostCarryingItsRoute]. `CloudDocumentsComposeActivity`
     * itself was not deleted until Task 9, which is also when its `targetFor` arm switched to
     * `targetForMigratedScreen` (unlike `Screen.MyDocuments`'s, which already threw from Task 6) --
     * this test only exercises the MIGRATED path, which is now the ONLY one any caller actually
     * reaches for this screen.
     */
    @Test
    fun cloudDocumentsResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.CloudDocuments)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.cloudDocuments(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * nav-graph slice 4, Task 9 -- the assertion Task 6 could only RECORD, not make. Task 6 found
     * `MyDocumentsComposeActivity.kt:215` (inside `openDocument`) to be the tree's last direct
     * `ScreenLauncher.targetFor` call, and put `Screen.MyDocuments` into [ScreenLauncher.MIGRATED]
     * so `intentFor` no longer reached it -- but the call site itself, and the class hosting it,
     * were both still there, just unreachable. Task 9 deletes the class and the call along with it,
     * so this scan -- the automated form of Task 9's own `grep -rn "ScreenLauncher.targetFor"
     * app/src/main` check -- can finally hold. `ScreenLauncher.kt` itself is excluded: `targetFor`
     * is defined there and `intentFor` calls it there, which is the one legitimate call in the whole
     * tree. A future caller reintroducing a direct call for a graph-only screen would otherwise slip
     * past `targetForMigratedScreen`'s loud failure entirely, by simply not going through
     * `targetFor`'s throwing arm for a screen that still returns a real class.
     */
    @Test
    fun noDirectTargetForCallSurvivesInAppSrcMain() {
        val callers = ClassicRemovalScan.appSources()
            .filter { it.name != "ScreenLauncher.kt" }
            .filter { file -> file.readLines().any { it.contains("ScreenLauncher.targetFor(") } }
            .map { it.path.replace('\\', '/') }
            .sorted()
        assertEquals(
            emptyList<String>(),
            callers,
            "a direct ScreenLauncher.targetFor(...) call survives outside ScreenLauncher.kt -- every " +
                "caller should reach a screen through intentFor/open instead: $callers",
        )
    }

    /**
     * nav-graph slice 4, Task 5 -- [customRepositoryEditorIsNotInMigratedAndItsPatternIsRegisteredByAGraph]'s
     * shape, for the batch's second dual-entry destination. `Screen.MyDocumentPages` is deliberately
     * absent from [ScreenLauncher.MIGRATED]: all THREE of [NavRoutes.MY_DOCUMENT_PAGES_PATTERN]'s
     * arguments (`documentId`/`documentInitials`/`documentName`) are required, so an argument-free
     * entry has nothing meaningful to show -- the `Screen.LabelEdit`/`Screen.ManageLabels`
     * precedent, not the editor's OPTIONAL-argument one.
     *
     * The `assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(...) }` half those two
     * tests also carry did NOT hold until now, for `Screen.CustomRepositoryEditor`'s own reason:
     * Tasks 5 through 8 kept classic `MyDocumentPagesComposeActivity` alive, so
     * `ScreenLauncher.targetFor` still resolved to it and `intentFor` returned a real Intent rather
     * than throwing. Task 9 deletes the class and points the `targetFor` arm at
     * `targetForMigratedScreen`, which is what makes the throw assertion below hold at last.
     */
    @Test
    fun myDocumentPagesIsNotInMigratedAndItsPatternIsRegisteredByAGraph() {
        assertFalse(
            Screen.MyDocumentPages in ScreenLauncher.MIGRATED,
            "all three of the pages editor's route arguments are required, so an argument-free " +
                "MIGRATED entry would be a lie",
        )
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.MyDocumentPages) }
        assertTrue(
            NavRoutes.MY_DOCUMENT_PAGES_PATTERN in registeredRoutePatterns(),
            "no graph registers the pages editor's pattern",
        )
    }

    /**
     * nav-graph slice 4, Task 7a -- the third destination in the batch that is registered by a graph
     * but deliberately absent from [ScreenLauncher.MIGRATED], and the one with the strongest reason:
     * [NavRoutes.DOWNLOAD_PATTERN] carries FIVE arguments (`firstDownload`, `downloadRecommended`,
     * `search`, `addons`, `documentIds`), every one of which a classic launch site used to attach as
     * an Intent extra. A bare `Screen.Download` entry could only map to the argument-free route, so
     * the six call sites that DO attach an argument would silently open the plain download list --
     * the `migratedScreenArgumentIsNeverDroppedByAPutExtra` failure mode, except unguardable here
     * because `StartupComposeActivity.firstDownloadIntent()`'s extras are attached by its callers,
     * lines away. Task 7b repoints all ten launch edges at explicit `NavRoutes.download(...)` routes
     * instead, which is why this assertion is `assertFalse` and not a MIGRATED round trip.
     *
     * The second half is the one that matters for THIS task: the destination has to actually be
     * registered by some `*NavGraph.kt`, or nothing Task 7b repoints can resolve.
     */
    @Test
    fun downloadIsNotInMigratedAndItsPatternIsRegisteredByAGraph() {
        assertFalse(
            Screen.Download in ScreenLauncher.MIGRATED,
            "the download route has five arguments, so an argument-free MIGRATED entry would be a lie",
        )
        assertTrue(
            NavRoutes.DOWNLOAD_PATTERN in registeredRoutePatterns(),
            "no graph registers the download screen's pattern",
        )
    }

    /**
     * Task 7b deletes `Screen.FirstDownload` (plan design §5): a route with five arguments has no
     * use for an alias, since `NavRoutes.download(firstDownload = true, ...)` already says the same
     * thing explicitly. Scanned via [Screen.entries] rather than a direct `Screen.FirstDownload`
     * reference, so this test keeps compiling after the enum value is gone instead of failing to
     * build the moment the deletion lands.
     */
    @Test
    fun firstDownloadIsNoLongerAScreen() {
        assertFalse(
            Screen.entries.any { it.name == "FirstDownload" },
            "Screen.FirstDownload should be gone -- NavRoutes.download(firstDownload = true) is its " +
                "replacement, and a route with arguments has no use for an argument-free alias",
        )
    }

    /**
     * The coexistence seam's other half: a screen NOT in [ScreenLauncher.MIGRATED] still resolves to its
     * own Activity. Since slice 8 C3 `Screen.Calculator` is the one screen that stays an Activity BY
     * DECISION (slice 8 spec §3.4 -- an in-graph overlay was rejected), so it is the subject; it used to
     * be `Screen.Backup`, which moved into the graph. The property is the subject, not the screen.
     */
    @Test
    fun anUnmigratedScreenStillResolvesToItsOwnActivity() {
        val intent = ScreenLauncher.intentFor(context, Screen.Calculator)
        assertEquals(
            "net.bible.android.view.activity.discrete.CalculatorComposeActivity",
            intent.component?.className,
        )
        assertTrue(intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE) == null)
    }

    @Test
    fun everyMigratedScreenHasARouteAndNoneIsBlank() {
        assertTrue(ScreenLauncher.MIGRATED.isNotEmpty())
        ScreenLauncher.MIGRATED.forEach { (screen, route) ->
            assertTrue(route.isNotBlank(), "$screen has a blank route")
        }
    }

    /**
     * Task 7 fix round 1's durable net. `ScreenLauncher.MIGRATED` is a `Map<Screen, String>` — it
     * can only carry an argument-LESS route: [ScreenLauncher.intentFor] resolves a MIGRATED screen
     * straight to `NavHostComposeActivity.intentFor(context, route)`, which reads only
     * [NavHostComposeActivity.EXTRA_ROUTE]. A caller that then adds a `.putExtra(...)` on TOP of
     * `ScreenLauncher.intentFor(context, Screen.X)` for a MIGRATED `X` gets an Intent whose extra is
     * silently dropped — no compiler error, no crash, just the wrong screen state. This is exactly
     * what broke `AiPromptsComposeActivity.onOpenPrompt` and `BibleJavascriptInterface
     * .openPromptEditor` the moment `Screen.PromptEdit` was migrated (Task 7), and
     * `AiConnectionSettingsComposeActivity.launchEasySetup` the same way against `Screen.AiProviders`
     * (Task 6) — all three fixed in the same round as this test, by building the concrete route
     * directly instead: `NavHostComposeActivity.intentFor(context, NavRoutes.xyz(...))`.
     *
     * Scans every shipping source file ([ClassicRemovalScan.appSources]) for three shapes, per
     * nav-host-bound `intentFor(...)` call site -- either marker in [INTENT_FOR_CALL_MARKERS]; for
     * the `ScreenLauncher` one, only when its argument list names a screen currently in
     * [ScreenLauncher.MIGRATED]:
     *  1. `ScreenLauncher.intentFor(..., Screen.X).putExtra(...)` — chained directly.
     *  2. `ScreenLauncher.intentFor(..., Screen.X).apply { ... putExtra(...) ... }` — chained via
     *     `apply` (the block is captured by brace-balance, not by a fixed-width window, since an
     *     `apply` block can itself contain nested braces).
     *  3. `val name = ScreenLauncher.intentFor(..., Screen.X)` followed nearby by
     *     `name.putExtra(...)` — the shape that actually broke `openPromptEditor`; a scan limited to
     *     shapes 1-2 would have missed it.
     *
     * **Hardened in Task 8's fix round** against three ways the ORIGINAL version of this scan
     * (Task 7) failed OPEN — missed a real offender instead of flagging it — rather than merely
     * failing to flag a shape that never occurs:
     *  - **An explicit type annotation** (`val intent: Intent = ScreenLauncher.intentFor(...)`).
     *    The old shape-3 regex captured the token immediately before `=`, so a type annotation
     *    made it capture the TYPE NAME (`Intent`), not the variable (`intent`) — the later
     *    `intent.putExtra(...)` was then never matched against anything. Fixed by trying a
     *    typed-assignment regex first (`(\w+)\s*:\s*[^=\n]*=\s*$`) and falling back to the bare one.
     *  - **A multi-line-formatted call.** The old scan located each call by the literal substring
     *    `"Screen.X)"` — the screen literal adjacent to the closing paren with ZERO whitespace —
     *    which a line-wrapped call (screen argument on its own line, closing paren on the next)
     *    never produces. A real precedent already exists in the tree:
     *    `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt` spreads
     *    `ScreenLauncher.intentFor(` across several lines with the screen chosen by an `if`
     *    expression (not a live miss today — `Screen.SearchIndex`/`Screen.SearchResults` aren't
     *    migrated yet — but it WILL be, in a later slice). Fixed by locating each call by its own
     *    `(` / matching `)` (brace-balance, reusing the same technique as the `.apply` block scan)
     *    rather than by a marker glued to one particular argument.
     *  - **Named or reordered arguments**, where the screen is not textually the last argument
     *    (`ScreenLauncher.intentFor(screen = Screen.X, context = context)`). The old scan required
     *    the screen literal to sit immediately before the call's OWN closing paren, which a named
     *    argument in a non-last position never does. Fixed by extracting the whole balanced argument
     *    list once per call and searching it for a word-bounded `Screen.X` anywhere inside, rather
     *    than anchoring on "right before the closing paren".
     *
     * **Hardened again in Task 8's fix round 1** against a fourth fail-open shape a reviewer
     * constructed and ran against the round-1 version of this scan: `.also { it.putExtra(...) }`
     * (and equally `.let`/`.run`) — chained-scope-function forms other than `.apply`, which the
     * scan did not look for at all. No live caller uses one of these today (verified by grep over
     * every `ScreenLauncher.intentFor` call site in `app/src`), but `.also`/`.let` on an `Intent` is
     * idiomatic enough Kotlin that a future caller will write one. Fixed by matching any of
     * `apply`/`also`/`let`/`run` after the call, then checking the captured block for `putExtra(...)`
     * with the receiver reference each form actually uses: `apply`/`run` rebind `this`, so a bare
     * `putExtra(...)` is what a real caller writes; `also`/`let` do NOT rebind `this` -- the receiver
     * is only reachable as the implicit `it` or an explicit named lambda parameter (`.also { intent ->
     * intent.putExtra(...) }`), so the scan looks for `it.putExtra(...)` OR `<paramName>.putExtra(...)`
     * for those two, reading the parameter name off the lambda's own `name ->` header when present.
     *
     * **Hardened again in Task 8's fix round 2** against two more fail-open gaps a second reviewer
     * found — one of them against a LIVE call site, not merely a future risk:
     *  - **`putExtras` (plural, a whole `Bundle`) matched none of the three checks**, because every
     *    one of them searched literally for `"putExtra("` / `\.putExtra\s*\(`, and the character
     *    after `putExtra` in `putExtras(` is `s`, not `(`. Live today:
     *    `LinkControl.kt`'s `intent.putExtras(searchParams)` after a plain assignment, targeting
     *    `Screen.SearchIndex`/`Screen.SearchResults` (not migrated yet, so not a live BUG today --
     *    but the guard existed to catch exactly this shape the day one of those screens migrates,
     *    and it silently would not have). Fixed by giving every check the shared
     *    [PUT_EXTRA_METHODS] alternation (`putExtras?`) instead of the bare singular name — see its
     *    kdoc for why this cannot also match an unrelated identifier that merely starts with those
     *    characters.
     *  - **A TYPED `also`/`let` lambda parameter** (`.also { intent: Intent -> intent.putExtra(...) }`)
     *    defeated the named-parameter read: the header regex's `\w+` stopped at the `:`, so the
     *    match failed outright and the code silently fell back to `it` (which is not a substring of
     *    `intent`). Fixed by accepting an optional, uncaptured `: <type>` between the parameter name
     *    and `->`.
     *
     * **Hardened again in nav-graph 3/5/6 Task 6** against a fifth fail-open shape -- and this one
     * failed open on a LIVE BUG, not a future risk, which is the worst thing a guard can do:
     *  - **The call as a BRANCH of a conditional whose RESULT is assigned**, i.e.
     *    `val intent = if (needToIndex) { ScreenLauncher.intentFor(a, Screen.SearchIndex) } else {
     *    ScreenLauncher.intentFor(a, Screen.SearchResults) }` followed by `intent.putExtras(...)`.
     *    Both assignment regexes anchor immediately before the call (`(\w+)\s*=\s*$` and its typed
     *    twin), and what sits there is the branch opener `if (needToIndex) {` -- not `intent =` --
     *    so neither matched, no scope-function form applied either, and the scan reported nothing.
     *    `LinkControl.showAllOccurrences` had exactly this shape, and the day Task 4 migrated
     *    `Screen.SearchIndex` it became a real dropped-argument bug (a Strong's link into an
     *    unindexed module indexed the CURRENT page's book instead of the Strong's Bible, then
     *    landed on an empty search form) that this test passed straight over. Fixed by a third
     *    assignment check: find the NEAREST `name = if (` / `name = when (` within
     *    [CONDITIONAL_ASSIGN_LOOKBEHIND_CHARS] behind the call, confirm the call is still INSIDE
     *    that conditional expression, then look ahead for `name.putExtra(s)(...)` exactly as the
     *    plain-assignment check does. Containment is decided from the text between the
     *    conditional's opening `(`/`{` and the call, with comments blanked ([withoutComments]):
     *    more `{` than `}` (a braced branch -- `} else {` nets back to depth 1), or depth 0 ending
     *    in `)` (the brace-less `= if (cond) intentFor(...)` form); and no `;` or local
     *    `val`/`var`, either of which means a new statement began and the conditional is no longer
     *    what we are inside of. Run only when the plain-assignment check did not already fire, so
     *    one offender cannot be reported twice.
     *
     * **Known, deliberate, NOT fixed** (explicitly out of scope for this hardening, so the next
     * person here does not have to rediscover why):
     *  - **A conditional branch that declares a local before the call**
     *    (`val intent = if (x) { val ctx = foo(); ScreenLauncher.intentFor(ctx, Screen.X) } ...`).
     *    The containment check treats a `val`/`var` between the conditional head and the call as
     *    "a new statement started", which is what keeps an unrelated earlier `val foo = if (...)`
     *    from being read as this call's assignment. Trading that miss for the false positives is
     *    deliberate: a false offender here blocks a green branch on a non-bug, and no live caller
     *    in the tree writes the declaring form.
     *  - **A conditional whose result is RETURNED rather than assigned** and whose extras are added
     *    by the caller (`return if (x) intentFor(..) else intentFor(..)`, then
     *    `getSearchIntent(...)?.putExtra(...)` at the call site). There is no local name to follow,
     *    so this is the cross-function case below by another route -- it needs data flow, not a
     *    regex. `SearchControl.getSearchIntent` is precisely this shape; Task 6 verified by hand
     *    that all five of its callers pass the Intent straight to `startActivityForResult` with no
     *    extras added, and rewrote its `Screen.SearchIndex` branch to build a concrete route anyway.
     *  - `with(intentVar) { putExtra(...) }` -- no live caller uses `with` on a `ScreenLauncher
     *    .intentFor(...)` result today; purely theoretical.
     *  - A helper function that receives the built `Intent` and adds extras to it elsewhere (e.g.
     *    `fun addSearchExtras(intent: Intent) { intent.putExtra(...) }` called on the result) -- an
     *    inherent limit of a textual/regex scan; catching it needs real static analysis (a
     *    call-graph/data-flow pass), not a bigger regex.
     *  - The pre-existing looseness of the `apply`/`run` bare-substring check (now
     *    `\bputExtras?\s*\(`, previously `.contains("putExtra(")`): since `apply`/`run` rebind
     *    `this`, the scan cannot tell a bare `putExtra(...)` on the actual `Intent` receiver from
     *    one on some OTHER value the block happens to also touch (e.g. a `someOtherIntent
     *    .putExtra(...)` sitting in the same block) -- a same-block false positive is possible in
     *    principle, predates this hardening round, and is accepted rather than chased.
     *
     * **Hardened again by the whole-branch review's M2** against a fifth fail-open shape -- not a
     * missed syntax this time but a missed CALL SITE. Tasks 6 and 8 (of nav-graph 3+5+6) established
     * `NavHostComposeActivity.intentFor(context, NavRoutes.xyz(...))` as the PRESCRIBED way to reach
     * a route that takes arguments, and at that point the tree had six such production call sites
     * (`BibleJavascriptInterface.kt:570` and `:1094`, `BibleView.kt:482`,
     * `ComposeReadingViewHost.kt:833`, `SearchControl.kt:109`, `LinkControl.kt:428`). Nav-graph
     * slices 2+4 Task 7 added six more, all `NavRoutes.manageLabels(data)` calls, when it deleted the
     * classic `ManageLabelsComposeActivity` host and repointed its six outside callers:
     * `MenuCommandHandler.kt:219`, `OptionsMenuItems.kt:585` and `:610`, `BibleView.kt:628` (so
     * `BibleView.kt` now has two of its own), `CurrentGeneralBookPage.kt:92` and
     * `TextDisplaySettingsComposeActivity.kt:393`. The tree has **twelve** such production call
     * sites today. A `.putExtra(...)` chained onto any of them is dropped exactly as silently as the
     * three defects this scan was written for -- [NavHostComposeActivity] reads
     * [NavHostComposeActivity.EXTRA_ROUTE] plus the handful of extras named in
     * [EXTRAS_THE_NAV_HOST_READS] and nothing else -- yet the scan looked only for the
     * `ScreenLauncher` spelling. Fixed by scanning BOTH markers in the same loop. The direct marker
     * needs no screen-name gate: its route argument IS the whole payload, so any extra on it is wrong
     * regardless of which route it carries. No live call site is an offender today (verified by
     * reading all twelve, and by this scan passing).
     *
     * Known, deliberate bound of the second marker, in the same fail-open direction as the rest: the
     * scan is textual, so the marker also matches inside a STRING LITERAL -- `NavHostComposeActivity`'s
     * own `requireNotNull` message names `NavHostComposeActivity.intentFor()` in prose. That match has
     * an empty argument list and nothing chained after it, so it reports nothing; a literal that DID
     * spell a chained `.putExtra` would be a false positive, which no shipping string does.
     *
     * The general shape is now: find each `intentFor(` call (by either marker) by locating its own
     * matching closing paren ([matchingParenIndex]); read every migrated screen named ANYWHERE in
     * that balanced argument list; then check independently, relative to that call's OWN
     * boundaries, for a chained `.putExtra(s)`, a chained `.apply`/`.also`/`.let`/`.run { ... }`
     * whose body reaches `putExtra(s)(...)` through the receiver form that scope function actually
     * uses, an assignment (typed or not) whose variable later receives a nearby
     * `.putExtra(s)(...)`, or -- when that last one does not fire -- an enclosing `if`/`when` whose
     * own result is assigned to a variable that then receives one.
     *
     * **Known, deliberate bound** (stated rather than overclaimed, matching this file's sibling
     * scans in [ClassicRemovalScan]'s own kdoc style): the assignment shape's forward look for
     * `name.putExtra(` is windowed to the next [ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS] characters, not
     * the rest of the file. Every real occurrence in this repo today has its `.putExtra` within a
     * few lines of the assignment; an unbounded look would risk a same-named variable in a LATER,
     * unrelated function reading as a false offender.
     */
    /**
     * T8b fix round 1, M4: [EXTRAS_THE_NAV_HOST_READS] is fail-closed and cannot silently grow past
     * review, but nothing checked that an entry on it is REAL. This does: every name on the list
     * must have an `intent.<get>Extra("name")`/`hasExtra("name")` read in the host itself, so an
     * entry added to quieten a scan without the host actually reading it goes red here.
     */
    @Test
    fun everyAllowlistedExtraIsReallyReadByTheNavHost() {
        val host = File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")
        assertTrue(host.exists(), "the nav host source moved; this scan is watching nothing")
        val code = ClassicRemovalScan.codeLinesOf(host.path)
        val unread = EXTRAS_THE_NAV_HOST_READS.filterNot { name ->
            Regex("""(?:get\w*Extra|hasExtra)\s*\(\s*"${Regex.escape(name)}"""").containsMatchIn(code)
        }
        assertEquals(
            emptyList<String>(),
            unread.sorted(),
            "an extra is allowlisted out of the dropped-argument scan although NavHostComposeActivity " +
                "never reads it -- either add the read or drop the entry",
        )
    }

    /**
     * Does [window] put an extra on [receiver] that the nav host would DROP?
     *
     * Fail-closed on purpose: a `putExtras(bundle)` or a non-literal key has no readable name, so it
     * is treated as dropped. The same is true of a `.putExtra` chained straight onto the
     * `intentFor(...)` call rather than onto a named variable -- that shape is not filtered at all,
     * so an allowlisted extra written that way would be a FALSE POSITIVE, which is the safe
     * direction and is what a reader should fix by naming the intent.
     */
    private fun hasDroppedPutExtra(window: String, receiver: String): Boolean =
        Regex("""\b${Regex.escape(receiver)}\.$PUT_EXTRA_METHODS\s*\(\s*(?:"([^"]*)")?""")
            .findAll(window)
            .any { it.groupValues[1] !in EXTRAS_THE_NAV_HOST_READS }

    @Test
    fun migratedScreenArgumentIsNeverDroppedByAPutExtra() {
        val migratedScreenNames = ScreenLauncher.MIGRATED.keys.map { it.name }
        assertTrue(migratedScreenNames.isNotEmpty(), "ScreenLauncher.MIGRATED is empty -- this scan would pass vacuously")

        val offenders = mutableListOf<String>()
        for (file in ClassicRemovalScan.appSources()) {
            val text = file.readText()
            val path = file.path.replace('\\', '/')

            for (callMarker in INTENT_FOR_CALL_MARKERS) {
                var searchFrom = 0
                while (true) {
                    val callStart = text.indexOf(callMarker, searchFrom)
                    if (callStart < 0) break

                    val openParenIndex = callStart + callMarker.length - 1
                    val closeParenIndex = matchingParenIndex(text, openParenIndex)
                    if (closeParenIndex == null) {
                        // Truncated/malformed input -- nothing further to find from here either.
                        break
                    }
                    searchFrom = closeParenIndex + 1

                    // Which MIGRATED screens does this call's own argument list name, anywhere in it
                    // (not just as the last argument -- see the named/reordered-arguments hardening
                    // above)? A word boundary keeps e.g. "Screen.RawLlmLog" from matching a longer
                    // hypothetical "Screen.RawLlmLogSomethingElse".
                    val argsText = text.substring(openParenIndex + 1, closeParenIndex)
                    // What this call would silently drop an extra FOR. The ScreenLauncher marker names
                    // one subject per MIGRATED screen its argument list mentions; the direct-host marker
                    // has no screen in it at all -- its route IS the whole payload, so every one of its
                    // call sites is a subject unconditionally.
                    val subjects =
                        if (callMarker == SCREEN_LAUNCHER_CALL_MARKER) {
                            migratedScreenNames.filter { name ->
                                Regex("""Screen\.${Regex.escape(name)}\b""").containsMatchIn(argsText)
                            }.map { "Screen.$it" }
                        } else {
                            listOf(NAV_HOST_CALL_MARKER.removeSuffix("("))
                        }
                    if (subjects.isEmpty()) continue

                    val afterCall = text.substring(closeParenIndex + 1)

                    val chainedPutExtra = Regex("""^\s*\.$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(afterCall.take(200))

                    // apply/also/let/run: apply/run rebind `this` to the receiver, so a real caller's
                    // body calls putExtra(s)(...) bare; also/let do NOT rebind `this` -- the receiver
                    // is only reachable as the implicit `it` or an explicit named lambda parameter, so
                    // those two are checked for `it.putExtra(s)(...)`/`<param>.putExtra(s)(...)` instead.
                    var chainedScopeFunctionDescription: String? = null
                    val chainedScopeMatch = Regex("""^\s*\.(apply|also|let|run)\s*\{""").find(afterCall.take(200))
                    if (chainedScopeMatch != null) {
                        val functionName = chainedScopeMatch.groupValues[1]
                        val braceIndex = closeParenIndex + 1 + chainedScopeMatch.range.last
                        val block = balancedBraceBlock(text, braceIndex)
                        if (block != null) {
                            val body = block.removePrefix("{").removeSuffix("}")
                            val bodyHasPutExtra = when (functionName) {
                                "apply", "run" -> Regex("""\b$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(body)
                                else -> {
                                    // "also"/"let": an explicit named lambda parameter ("intent ->" or
                                    // the typed "intent: Intent ->"), or the implicit "it" when none is
                                    // declared. The optional type annotation is matched but not
                                    // captured -- only the parameter NAME is needed.
                                    val namedParam = Regex("""^\s*(\w+)\s*(?::\s*.+?)?\s*->""")
                                        .find(body)?.groupValues?.get(1)
                                    val receiverName = namedParam ?: "it"
                                    Regex("""\b${Regex.escape(receiverName)}\.$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(body)
                                }
                            }
                            if (bodyHasPutExtra) {
                                chainedScopeFunctionDescription = "chained with .$functionName { ... putExtra(...) ... }"
                            }
                        }
                    }

                    // Is this call the RHS of an assignment -- i.e. does "<name>(: Type)? = " (or
                    // nothing at all) immediately precede "ScreenLauncher.intentFor("? Typed first
                    // (fixes the type-annotation miss above), bare as a fallback.
                    var assignedPutExtraName: String? = null
                    val beforeCall = text.substring(0, callStart)
                    val typedAssignment = Regex("""(\w+)\s*:\s*[^=\n]*=\s*$""").find(beforeCall)
                    val bareAssignment = Regex("""(\w+)\s*=\s*$""").find(beforeCall)
                    val assignedTo = typedAssignment ?: bareAssignment
                    if (assignedTo != null) {
                        val name = assignedTo.groupValues[1]
                        val window = afterCall.take(ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS)
                        if (hasDroppedPutExtra(window, name)) {
                            assignedPutExtraName = name
                        }
                    }

                    // Is this call a BRANCH of a conditional that is itself the RHS of an assignment --
                    // `val name = if (cond) { intentFor(..) } else { intentFor(..) }` -- followed by
                    // `name.putExtra(s)(...)`? Neither regex above can see this shape: what immediately
                    // precedes the call is the branch opener (`if (needToIndex) {`), not `name =`. See
                    // the kdoc's Task 6 hardening entry; live in LinkControl.showAllOccurrences.
                    var conditionalAssignedPutExtraName: String? = null
                    if (assignedPutExtraName == null) {
                        val lookBehind = beforeCall.takeLast(CONDITIONAL_ASSIGN_LOOKBEHIND_CHARS)
                        // The NEAREST preceding conditional assignment (typed or not); an earlier,
                        // already-closed one would be rejected by the containment check below anyway,
                        // but starting from the nearest keeps `between` as short as possible.
                        val conditionalAssignment =
                            Regex("""(\w+)\s*(?::\s*[^=\n]*)?=\s*(?:if|when)\s*[({]""")
                                .findAll(lookBehind)
                                .lastOrNull()
                        if (conditionalAssignment != null) {
                            val name = conditionalAssignment.groupValues[1]
                            // Everything between the conditional's opening `(`/`{` and this call. The
                            // call is still INSIDE that conditional expression when the text in
                            // between opens more braces than it closes (a braced branch, including the
                            // `} else {` hop, which nets back to depth 1), or when it closes the
                            // condition's paren and opens nothing (the brace-less
                            // `= if (cond) intentFor(...)` form). A `;` or a local `val`/`var`
                            // declaration in between means a new statement started, so the conditional
                            // is no longer what we are inside of.
                            val between = withoutComments(lookBehind.substring(conditionalAssignment.range.last + 1))
                            val braceDepth = between.count { it == '{' } - between.count { it == '}' }
                            val stillInsideTheConditional =
                                !between.contains(';') &&
                                    !Regex("""\b(?:val|var)\b""").containsMatchIn(between) &&
                                    (braceDepth >= 1 || (braceDepth == 0 && between.trimEnd().endsWith(')')))
                            if (stillInsideTheConditional) {
                                val window = afterCall.take(ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS)
                                if (hasDroppedPutExtra(window, name)) {
                                    conditionalAssignedPutExtraName = name
                                }
                            }
                        }
                    }

                    val shapeDescriptions = mutableListOf<String>()
                    if (chainedPutExtra) shapeDescriptions.add("chained directly with .putExtra(...)")
                    chainedScopeFunctionDescription?.let { shapeDescriptions.add(it) }
                    assignedPutExtraName?.let { name ->
                        shapeDescriptions.add("assigned to `$name`, then `$name.putExtra(...)` nearby")
                    }
                    conditionalAssignedPutExtraName?.let { name ->
                        shapeDescriptions.add(
                            "a branch of an if/when assigned to `$name`, then `$name.putExtra(...)` nearby",
                        )
                    }

                    for (subject in subjects) {
                        for (description in shapeDescriptions) {
                            offenders.add("$path: $subject $description")
                        }
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "an intent aimed at the nav host still receives .putExtra(...) -- the host reads ONLY " +
                "EXTRA_ROUTE, so the extra is silently dropped. For a ScreenLauncher.intentFor(context, " +
                "Screen.X) on a MIGRATED screen, build the concrete route instead: " +
                "NavHostComposeActivity.intentFor(context, NavRoutes.xyz(...)). For a direct " +
                "NavHostComposeActivity.intentFor(...) call, put the value in the ROUTE -- add the " +
                "argument to NavRoutes' builder and pattern. Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    /**
     * Whole-branch review M3. [everyMigratedScreenHasARouteAndNoneIsBlank] only checks that a
     * MIGRATED value is not blank -- it cannot tell a real route from a typo. A value matching no
     * registered `composable(route = ...)` pattern throws `IllegalArgumentException` at the host's
     * first composition on the LAUNCH path, and is **silently swallowed** on the `onNewIntent` path,
     * where `NavHostComposeActivity.navigateToRoute` catches exactly that exception by design
     * (turning an unroutable extra into a logged no-op). So the cheapest possible typo -- a renamed
     * route constant updated in the graph but not in the map, or the reverse -- surfaces as "the
     * screen just does not open" rather than as a failure.
     *
     * This asserts the whole map against what the graphs actually REGISTER, read out of the
     * `*NavGraph.kt` files in `:sharedUi` (the same cross-module source read
     * `SheetExpansionGuardTest`/`AgentLogHeaderStructureGuardTest` already do) and resolved back to
     * their values through [NavRoutes]' own constants. Matching is on the route BASE -- everything
     * before the `?` -- which is what decides destination resolution: a pattern's query arguments are
     * all declared with `defaultValue`s, so navigation-compose matches a concrete route that omits
     * them, and every MIGRATED value is an argument-free (or default-argument) route by construction.
     *
     * Two structural floors keep it from passing vacuously, both of which would otherwise hide a
     * silently-empty scan: at least as many registered patterns as MIGRATED entries, and EVERY
     * `composable(` occurrence in those files accounted for by the `NavRoutes.`-constant regex -- so
     * a destination registered with a hand-written string literal fails here rather than quietly
     * shrinking the set this test compares against.
     */
    @Test
    fun everyMigratedRouteResolvesToARegisteredDestinationPattern() {
        val registered = registeredRoutePatterns()
        assertTrue(
            registered.size >= ScreenLauncher.MIGRATED.size,
            "only ${registered.size} registered route patterns were found for ${ScreenLauncher.MIGRATED.size} " +
                "MIGRATED screens -- the graph scan is reading less than it should",
        )

        val registeredBases = registered.map { it.substringBefore('?') }.toSet()
        val unroutable = ScreenLauncher.MIGRATED
            .filter { (_, route) -> route.substringBefore('?') !in registeredBases }
            .map { (screen, route) -> "$screen -> \"$route\"" }
        assertEquals(
            emptyList<String>(),
            unroutable.sorted(),
            "a MIGRATED route matches no destination registered by any *NavGraph.kt. The host would " +
                "throw on the launch path and silently no-op on the onNewIntent path. Registered " +
                "bases: ${registeredBases.sorted()}. Offenders:\n${unroutable.joinToString("\n")}",
        )
    }

    /**
     * The graph half of the guard that `noGraphNavigatesToTheReadingProgressRoute` used to be, for
     * the destination that now has that shape. Nav-graph slices 2+4 Task 2 deleted that test when
     * `ReadingProgress` moved onto [NavResultChannel] and no longer needed it -- correctly, per its
     * own kdoc -- but nothing was left forbidding in-graph navigation to a root destination whose
     * channel has NO consumer, and `BOOKMARKS_PATTERN` is exactly that destination now.
     *
     * The mechanism, stated in `BookmarkNavGraph`'s own comment at the bookmarks arm: nothing reads
     * `deps.bookmarkResults.pending`. So if bookmarks were ever pushed onto a NON-EMPTY back stack
     * -- and the host's `onNewIntent` -> `navigateToRoute` always PUSHES, it never replaces -- then
     * `deliver` would see a parent entry, publish the row the user tapped to a flow nobody reads,
     * pop, and do nothing at all. A silently dropped selection, from one added `navigate(` row.
     *
     * Unreachable today: the only inbound edge is `MenuCommandHandler.kt:207`, which launches the
     * host WITH bookmarks as its start destination, so there is never a parent entry.
     *
     * **A slice that genuinely needs an in-graph edge into bookmarks must first add the consuming
     * `LaunchedEffect` to the parent arm -- the shape the `manageLabelsResults` consumption in
     * `BookmarkNavGraph` already uses -- and then DELETE this test as part of that change. Not
     * loosen it, not add an exception to it.**
     */
    @Test
    fun noGraphNavigatesToTheBookmarksRoute() {
        val offenders = mutableListOf<String>()
        for (file in navGraphSources()) {
            val text = withoutComments(file.readText())
            val path = file.path.replace('\\', '/')
            var searchFrom = 0
            while (true) {
                val callStart = text.indexOf(NAVIGATE_CALL_MARKER, searchFrom)
                if (callStart < 0) break
                val openParenIndex = callStart + NAVIGATE_CALL_MARKER.length - 1
                val closeParenIndex = matchingParenIndex(text, openParenIndex) ?: break
                searchFrom = closeParenIndex + 1
                val argsText = text.substring(openParenIndex + 1, closeParenIndex)
                // The pattern constant, or the builder that produces a route matching it.
                if (Regex("""NavRoutes\.BOOKMARKS_PATTERN\b""").containsMatchIn(argsText) ||
                    Regex("""NavRoutes\.bookmarks\s*\(""").containsMatchIn(argsText)
                ) {
                    offenders.add("$path: navigate(${argsText.trim()})")
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "a graph navigates to the bookmarks route from inside the host. Nothing consumes " +
                "bookmarkResults.pending, so the row the user taps would be published to a flow " +
                "nobody reads and silently dropped -- see this test's kdoc. Offenders:\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * Every `*NavGraph.kt` in `:sharedUi`'s `commonMain`. Globbed rather than listed so the slices
     * still queued behind this batch are covered the moment their graph file lands, instead of being
     * silently skipped by a hard-coded list nobody remembers to extend.
     */
    private fun navGraphSources(): List<File> {
        val root = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui")
        assertTrue(root.isDirectory, "cannot find :sharedUi sources at ${root.absolutePath}")
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith("NavGraph.kt") }.toList()
        assertTrue(files.isNotEmpty(), "no *NavGraph.kt found under ${root.absolutePath}")
        return files
    }

    /**
     * Every route pattern the graphs REGISTER, read as `composable(NavRoutes.X)` /
     * `composable(route = NavRoutes.X, ...)` and resolved to `NavRoutes.X`'s value by reflection
     * (a `const val` in a Kotlin `object` is a static field, so `get(null)` reads it).
     *
     * The registration count is asserted against the raw `composable(` count in the same files: a
     * destination registered with anything other than a `NavRoutes` constant -- a string literal, a
     * local val -- would otherwise just shrink this set silently, which is the wrong direction for a
     * test whose whole job is to prove a route resolves.
     */
    private fun registeredRoutePatterns(): List<String> {
        val constantCall = Regex("""\bcomposable\(\s*(?:route\s*=\s*)?NavRoutes\.(\w+)""")
        val anyCall = Regex("""\bcomposable\(""")
        val patterns = mutableListOf<String>()
        for (file in navGraphSources()) {
            val text = withoutComments(file.readText())
            val names = constantCall.findAll(text).map { it.groupValues[1] }.toList()
            assertEquals(
                anyCall.findAll(text).count(),
                names.size,
                "${file.path}: a composable(...) destination is registered with something other than a " +
                    "NavRoutes constant -- this scan cannot resolve its route",
            )
            for (name in names) {
                val field = NavRoutes::class.java.getDeclaredField(name)
                field.isAccessible = true
                patterns.add(field.get(null) as String)
            }
        }
        return patterns
    }

    private companion object {
        /** `ScreenLauncher.intentFor(context, Screen.X)` -- the indirect route into the nav host. */
        const val SCREEN_LAUNCHER_CALL_MARKER = "ScreenLauncher.intentFor("

        /**
         * `NavHostComposeActivity.intentFor(context, NavRoutes.xyz(...))` -- the DIRECT route in, and
         * the prescribed way to reach a destination that takes arguments (nav-graph 3+5+6 Tasks 6
         * and 8). Added by the whole-branch review's M2: the scan was written for the ScreenLauncher
         * shape only, but a `.putExtra(...)` chained onto a direct call is dropped exactly as
         * silently -- the host reads only [NavHostComposeActivity.EXTRA_ROUTE] -- and there are
         * twenty-three such production call sites now (`BibleJavascriptInterface.kt` x3, `BibleView.kt`
         * x3, `ComposeReadingViewHost.kt`, `SearchControl.kt`, `LinkControl.kt`,
         * `MenuCommandHandler.kt` x2, `OptionsMenuItems.kt` x2, `CurrentGeneralBookPage.kt` x2,
         * `TextDisplaySettingsComposeActivity.kt`, `StartupComposeActivity.kt` x3,
         * `StartupActivity.kt` x3, `ChooseDocumentComposeActivity.kt`) -- six of them added by
         * nav-graph slices 2+4 Task 7 when it deleted `ManageLabelsComposeActivity` and repointed
         * its six outside callers at this direct shape, `CurrentGeneralBookPage.kt`'s second one
         * added by nav-graph slice 4 Task 5 when it repointed the `MyDocumentPages` branch the same
         * way, and ten more added by nav-graph slice 4 Task 7b, which repointed every launch edge of
         * `Screen.Download`/the deleted `Screen.FirstDownload` at `NavRoutes.download(...)` instead:
         * one each in `BibleJavascriptInterface.kt`, `BibleView.kt`, `MenuCommandHandler.kt` and
         * `ChooseDocumentComposeActivity.kt` (new to this list), and three each in the newly-added
         * `StartupComposeActivity.kt` and `StartupActivity.kt` -- none of which is an offender today.
         */
        const val NAV_HOST_CALL_MARKER = "NavHostComposeActivity.intentFor("

        val INTENT_FOR_CALL_MARKERS = listOf(SCREEN_LAUNCHER_CALL_MARKER, NAV_HOST_CALL_MARKER)

        /** `navController.navigate(`, matched by its bare tail -- see [noGraphNavigatesToTheBookmarksRoute]. */
        const val NAVIGATE_CALL_MARKER = "navigate("

        /** See [migratedScreenArgumentIsNeverDroppedByAPutExtra]'s kdoc, the assignment shape's known bound. */
        const val ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS = 600

        /**
         * The conditional-RHS shape's backward twin of [ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS]: how far
         * BEHIND a call the scan looks for the `name = if (`/`name = when (` that the call may be a
         * branch of. Bounded for the same reason the forward look is -- an unbounded search would
         * keep finding some conditional assignment eventually, in an unrelated earlier function,
         * and lean entirely on the containment check to reject it.
         */
        const val CONDITIONAL_ASSIGN_LOOKBEHIND_CHARS = 600

        /**
         * The literal method-name alternation every offender check searches for, explicit and
         * shared rather than hand-repeated per call site: `putExtra` (singular, one key/value) OR
         * `putExtras` (plural, an entire `Bundle`) -- both silently dropped the same way by a
         * MIGRATED screen's intent, and `Intent` exposes both. Deliberately NOT a bare `putExtra`
         * prefix match (which would also match unrelated identifiers merely starting with those
         * characters, e.g. a hypothetical `putExtraValidator(...)`): the `s?` alternation matches
         * only the two real `Intent` method names, and every use site additionally requires the
         * immediately following `(` (via `\s*\(` after this fragment), so this can only match an
         * actual method call, not a bare word.
         */
        const val PUT_EXTRA_METHODS = "putExtras?"

        /**
         * The extras `NavHostComposeActivity` genuinely READS, besides
         * [NavHostComposeActivity.EXTRA_ROUTE] -- so a `putExtra` naming one of them is not a
         * silently dropped argument and must not be reported.
         *
         * `"openLink"` is `bootstrapIfNeeded`'s deep link (`NavHostComposeActivity.kt:1105-1106`,
         * `intent.hasExtra("openLink")` / `getStringExtra("openLink")`), the twin of classic
         * `MainBibleActivity.kt:600-601`. It became live for this scan in reading-host re-typing
         * T8b, which repointed `StartupActivity.gotoMainBibleActivity`'s handoff at the nav host;
         * the extra was always read there, the CALLER just used to build a `MainBibleActivity`
         * intent. Keep this list to extras a reader can point at an `intent.get*Extra` for.
         */
        val EXTRAS_THE_NAV_HOST_READS = setOf("openLink")
    }

    /**
     * The `{ ... }` block starting at [text]\[openBraceIndex\] (which must be `'{'`), matched by
     * brace-balance rather than a fixed window, since an `apply` block can itself contain nested
     * braces (e.g. a lambda argument to one of its own calls). Returns `null` if [openBraceIndex]
     * is not a `'{'`, or the braces never balance before the file ends (truncated/malformed input --
     * treated as "no block found" rather than a false positive).
     */
    private fun balancedBraceBlock(text: String, openBraceIndex: Int): String? {
        if (text.getOrNull(openBraceIndex) != '{') return null
        var depth = 0
        for (i in openBraceIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openBraceIndex, i + 1)
                }
            }
        }
        return null
    }

    /**
     * [text] with `//` line comments and `/* ... */` block comments blanked to a space, so the
     * conditional-RHS containment check counts braces in CODE rather than in prose -- a live
     * precedent sits between `LinkControl`'s two branches (`} else { //If an indexed Strong's
     * module is in place ...`), and a comment is exactly where an unbalanced `{` or the word `val`
     * shows up without meaning anything.
     *
     * Known bound, stated rather than hidden: this is a textual strip, not a lexer, so a `//`
     * inside a string literal (a URL, say) blanks the rest of that line. The only consequence is
     * that the containment check may see less text than it should and decline to flag -- the same
     * fail-open direction the rest of this scan's bounds have, never a false positive.
     */
    private fun withoutComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /**
     * The index of the `')'` matching [text]\[openParenIndex\] (which must be `'('`), by
     * paren-balance -- same technique as [balancedBraceBlock], needed here so a call's argument
     * list can be located regardless of how it is line-wrapped (see
     * [migratedScreenArgumentIsNeverDroppedByAPutExtra]'s multi-line-call hardening). Returns
     * `null` if [openParenIndex] is not a `'('`, or the parens never balance before the file ends.
     */
    private fun matchingParenIndex(text: String, openParenIndex: Int): Int? {
        if (text.getOrNull(openParenIndex) != '(') return null
        var depth = 0
        for (i in openParenIndex until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
