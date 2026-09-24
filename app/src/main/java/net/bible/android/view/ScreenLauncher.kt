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

import android.content.Context
import android.content.Intent
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.android.view.activity.backup.BackupComposeActivity
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.StartupComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity

/**
 * The screens [ScreenLauncher] routes to. Named for what they ARE, not for how they are built --
 * this used to be "screens that have both a classic (XML) and a new (Compose) implementation", and
 * after Batch Z-late phase 1 none of them has a classic half left.
 *
 * Not every screen is an Activity, so not every screen is in here. `History` and `BibleSpeak` both
 * left the enum in the epilogue: each is now a sheet drawn over the reading view by
 * `ComposeReadingViewHost`, so a routing target for either would name an Activity that no longer
 * exists. Reaching them is a host call (`showHistorySheet()`, `showSpeakTransport()`), not a launch.
 */
enum class Screen { Calculator, SearchIndexProgress, SearchIndex, SearchResults, ReadingPlanSelector, DailyReadingList, ReadingPlan, ChooseGeneralBookKey, ChooseMapKey, ChooseDictionaryWord, GridChoosePassageBook, ChooseDocument, Download, Search, EpubSearch, EpubSearchResults, MyDocuments, MyDocumentPages, CloudDocuments, WorkspaceSelector, AiConnectionSettings, AiProviders, AiModels, AiPrompts, PromptEdit, GlobalToolPermissions, ToolInfo, AiDocumentFilter, RawLogHistory, RawLlmLog, LabelEdit, ManageLabels, Bookmarks, ReadingProgress, Settings, ReadingProgressSettings, SyncSettings, Startup, InstallZip, TextDisplaySettings, CustomRepositories, CustomRepositoryEditor, Backup, ProgressStatus }

/**
 * Central routing indirection, and all that survives of the old/new Strangler Fig: [targetFor] is a
 * plain [Screen] to `Class` map, and there is no longer any second implementation for it to choose
 * between. Batch Z-late phase 1 deleted every classic screen slice by slice, and its epilogue
 * (spec 10.1) removed the settings flag that used to pick a side, so what is left is one place that
 * answers "which Activity IS this screen" -- the seed of the future CMP navigation graph, which
 * replaces this map rather than the flag.
 *
 * Keeping the indirection after the choice disappeared is deliberate: callers name a [Screen], not
 * an Activity class, so the whole tree does not have to be edited again when a screen's host class
 * changes -- which is exactly what the navigation graph will change it to.
 */
object ScreenLauncher {
    /**
     * Screens that now live in the Compose navigation graph rather than in an Activity of their
     * own, mapped to the route that opens them. [intentFor] aims these at
     * [NavHostComposeActivity]; every other screen still resolves through [targetFor].
     *
     * This is the coexistence seam for the nav-graph migration: every outside call site reaches a
     * screen through [intentFor] or [open] rather than [targetFor] directly -- nav-graph slice 4
     * Task 9 deleted the tree's last direct [targetFor] caller
     * (`MyDocumentsComposeActivity.kt:215`, inside `openDocument`) along with the class that hosted
     * it -- so moving a screen into the graph changes NO caller. Entries are added one cluster at a
     * time.
     */
    val MIGRATED: Map<Screen, String> = mapOf(
        Screen.ToolInfo to NavRoutes.AI_TOOL_INFO,
        Screen.AiDocumentFilter to NavRoutes.AI_DOCUMENT_FILTER,
        Screen.GlobalToolPermissions to NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS,
        Screen.AiModels to NavRoutes.AI_MODELS,
        Screen.AiConnectionSettings to NavRoutes.AI_CONNECTION_SETTINGS,
        Screen.AiProviders to NavRoutes.aiProviders(startEasySetup = false),
        Screen.AiPrompts to NavRoutes.AI_PROMPTS,
        Screen.PromptEdit to NavRoutes.promptEdit(),
        // Screen.RawLlmLog is deliberately NOT here (whole-branch review M2): unlike
        // Screen.PromptEdit, an argument-less RawLlmLog route has no "new X" meaning — both ids
        // null just renders an empty screen with no log to show. NavRoutes.rawLlmLog() would be
        // silently wrong for a caller reaching this route via a bare Screen. Every real edge into
        // this screen builds NavRoutes.rawLlmLog(...) directly with a real id, bypassing this map,
        // so a stray ScreenLauncher.open(ctx, Screen.RawLlmLog) now falls through to targetFor,
        // which throws loudly instead of opening a screen with nothing to show.
        Screen.RawLogHistory to NavRoutes.AI_RAW_LOG_HISTORY,
        // — slice 3: the reading plan cluster —
        Screen.ReadingPlanSelector to NavRoutes.READING_PLAN_SELECTOR,
        Screen.DailyReadingList to NavRoutes.READING_PLAN_DAY_LIST,
        // The ARGUMENT-FREE daily-reading route, deliberately: the classic host branched on
        // `extras.containsKey(ReadingPlanKeys.PLAN/DAY)`, so "no plan, no day" is a real state
        // meaning "the current plan's current day" — not a missing argument. Callers that DO know a
        // plan/day (HistoryManager's stored intent) build NavRoutes.dailyReading(plan, day)
        // directly, bypassing this map.
        Screen.ReadingPlan to NavRoutes.dailyReading(),
        // — slice 5: the search cluster (part A) —
        // The ARGUMENT-FREE search form: classic SearchComposeActivity opened with no extras IS
        // the empty Find screen, so this is a real state rather than a missing argument. Callers
        // that know a query/type/section (HistoryManager's stored route) build
        // NavRoutes.searchForm(...) directly, bypassing this map.
        Screen.Search to NavRoutes.searchForm(),
        // Likewise argument-free: SearchIndexComposeActivity.kt:46 falls back to the CURRENT page's
        // document when SEARCH_DOCUMENT is absent, so "index the book I am reading" is a defined
        // meaning. Callers that name a document build NavRoutes.searchIndex(searchDocument = ...).
        Screen.SearchIndex to NavRoutes.searchIndex(),
        // Screen.SearchIndexProgress is deliberately NOT here, for the same reason as
        // Screen.RawLlmLog above: an argument-free index-progress route watches nothing and has
        // nowhere to route when indexing completes. Every real edge into it (the graph's
        // SEARCH_INDEX_PATTERN arm, classic SearchIndexComposeActivity.kt:72) builds a route
        // carrying the chain's five arguments.
        // — slice 5: the search cluster (part B) —
        // Argument-free by construction: classic EpubSearchComposeActivity read NO extras at all
        // and took its document from the current page, so NavRoutes.EPUB_SEARCH is the whole route.
        // Both of its callers (SearchControl.getSearchIntent, classic
        // SearchIndexProgressComposeActivity) build it with no extras.
        Screen.EpubSearch to NavRoutes.EPUB_SEARCH,
        // Screen.SearchResults and Screen.EpubSearchResults are deliberately NOT here, again for
        // Screen.RawLlmLog's reason: a results route with no searchText renders an empty search
        // with nothing to run (and NavRoutes.epubSearchResults cannot even be called without one —
        // its searchText parameter is non-null). Every real edge builds
        // NavRoutes.searchResults(...)/epubSearchResults(...) with a query: the graph's own arms,
        // and BibleView/LinkControl once Task 6 rewrites them.

        // — slice 6: the settings cluster (part A) —
        // The REFRESH_DISPLAY_ON_FINISH edge survives this row untouched: MenuCommandHandler.kt:182-186
        // launches Screen.Settings with that request code through startActivityForResult, and the
        // intent still names an Activity — the nav host — so the code still comes back to
        // MainBibleActivity.onActivityResult when the host finishes.
        Screen.Settings to NavRoutes.SETTINGS,
        Screen.SyncSettings to NavRoutes.SYNC_SETTINGS,
        // — slice 6: the settings cluster (part B) —
        // The ARGUMENT-FREE reading-progress route, deliberately: classic
        // ReadingProgressComposeActivity.kt:76-82 defaulted an ABSENT ReadingProgressKeys.EXTRA_TAB
        // to the persisted `reading_progress_last_tab`, so "no tab" is the real state "the tab the
        // user was last on" rather than a missing argument. The one caller that DOES name a tab
        // (BibleJavascriptInterface.openReadingProgress) builds NavRoutes.readingProgress(tab)
        // directly, bypassing this map.
        //
        // This screen is the batch's one OUTBOUND result producer: it sets
        // ActivityResultKind.ReadingProgress and MainBibleActivity dispatches on that extra rather
        // than on the result Intent's component class, so the edge survives the move untouched.
        Screen.ReadingProgress to NavRoutes.readingProgress(),
        Screen.ReadingProgressSettings to NavRoutes.READING_PROGRESS_SETTINGS,
        // — slice 2: the bookmark cluster —
        // Screen.LabelEdit is deliberately NOT here, for Screen.RawLlmLog's reason above:
        // NavRoutes.LABEL_EDIT_PATTERN's `data` argument carries the whole LabelEditContract
        // .LabelData payload, so an argument-free route would open the editor with no label to
        // edit — and NavRoutes.labelEdit(data) cannot even be called without one, its parameter
        // being non-null. Its only caller is in-graph: the ManageLabels arm builds
        // NavRoutes.labelEdit(data) directly. The classic LabelEditComposeActivity that targetFor
        // used to resolve this screen to for outside callers is gone (nav-graph slices 2+4 Task 7);
        // there never was an outside caller for it.
        //
        // Screen.ManageLabels is deliberately NOT here either, for the same reason and one more of
        // its own: NavRoutes.MANAGE_LABELS_PATTERN's `data` argument carries the whole
        // ManageLabelsContract.ManageLabelsData payload, whose `mode` field is what decides WHICH of
        // the four screens (StudyPads / assign / workspace auto-assign / hide-labels) is drawn — an
        // argument-free route could not even pick one. All eight consumption sites build the payload
        // as JSON: MenuCommandHandler.kt:207, OptionsMenuItems.kt:572 and :602, BibleView.kt:618,
        // CurrentGeneralBookPage.kt:80 and TextDisplaySettingsComposeActivity.kt:379 (six outside the
        // graph) build `NavHostComposeActivity.intentFor(context, NavRoutes.manageLabels(data))`
        // directly, bypassing this map and targetFor entirely (nav-graph slices 2+4 Task 7 -- until
        // then they went through targetFor to the classic ManageLabelsComposeActivity); the
        // Bookmarks arm's two in-graph navigate calls build NavRoutes.manageLabels(data) the same
        // way (slice 2 Task 6).
        //
        // Screen.Bookmarks, by contrast, IS here, and the difference is the one Screen.ReadingPlan
        // and Screen.ReadingProgress above turn on: NavRoutes.BOOKMARKS_PATTERN's `labelNo` argument
        // is OPTIONAL and its ABSENCE is a real state rather than a missing argument -- classic
        // BookmarksComposeActivity.kt:73-77 (deleted, nav-graph slices 2+4 Task 7) branched on
        // `intent.extras?.containsKey(BookmarkControl.LABEL_NO_EXTRA)`, and "no key" meant "no label
        // filter" (filter index 0); the graph's Bookmarks arm reproduces that. The one live caller,
        // MenuCommandHandler.kt:205, passes no labelNo at all and so goes through this map; a caller
        // that knew one would build NavRoutes.bookmarks(labelNo) directly, bypassing it.
        Screen.Bookmarks to NavRoutes.bookmarks(),
        // — nav-graph slice 4, Task 3: the custom-repository pair —
        // The ARGUMENT-FREE custom-repositories route, and correctly so:
        // NavRoutes.CUSTOM_REPOSITORIES_PATTERN takes no argument at all. Task 3's coexistence seam
        // (classic DownloadComposeActivity.onCustomRepositories() passing through this map) was
        // replaced by Task 7b's in-graph `navController.navigate(NavRoutes.customRepositories())`
        // (NavHostComposeActivity's DownloadOverflowMenu), and DownloadComposeActivity itself is
        // gone (nav-graph slice 4 Task 9) -- this entry now backs `targetFor`'s throwing arm below
        // for the same reason every other graph-only screen keeps one.
        //
        // Screen.CustomRepositoryEditor is deliberately NOT here, for Screen.LabelEdit's reason:
        // NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN's repositoryId argument is OPTIONAL, and its
        // ABSENCE means "new repository" -- a real state, but one only the CustomRepositories arm's
        // own navigate(customRepositoryEditor(null)) call can mean deliberately. A bare
        // ScreenLauncher.open(ctx, Screen.CustomRepositoryEditor) has no such context, so it stays
        // absent here and falls through to targetFor instead, which now throws (nav-graph slice 4
        // Task 9 deleted classic CustomRepositoryEditorComposeActivity).
        Screen.CustomRepositories to NavRoutes.customRepositories(),
        // — nav-graph slice 4, Task 4: the notification-launched progress screen —
        // The ARGUMENT-FREE progress-status route, correctly so: classic
        // ProgressStatusComposeActivity read no extras at all, and its only inbound edge --
        // ProgressNotificationManager's getActivity PendingIntent -- cannot carry one either. This
        // is the whole point of the seam: intentFor now returns a nav-host Intent carrying
        // EXTRA_ROUTE instead of an Intent naming the classic Activity directly, with no change to
        // ProgressNotificationManager itself.
        Screen.ProgressStatus to NavRoutes.progressStatus(),
        // — nav-graph slice 4, Task 6: the My-Documents list —
        // The ARGUMENT-FREE My-Documents route, correctly so: NavRoutes.MY_DOCUMENTS_PATTERN takes
        // no argument at all, and classic MyDocumentsComposeActivity read no extras either. This
        // used to be the last screen whose Activity ScreenLauncher.targetFor was still reached
        // through directly (MyDocumentsComposeActivity.kt:215, inside openDocument); that direct
        // call is gone along with the class itself (nav-graph slice 4 Task 9) -- see
        // NavHostRoutingGuardTest's `no ScreenLauncher.targetFor call remains in app/src/main` guard.
        Screen.MyDocuments to NavRoutes.myDocuments(),
        // — nav-graph slice 4, Task 8: the gated cloud-documents destination —
        // The ARGUMENT-FREE cloud-documents route, correctly so: NavRoutes.CLOUD_DOCUMENTS_PATTERN
        // takes no argument at all, and classic CloudDocumentsComposeActivity read none either.
        // `targetFor`'s own CloudDocuments arm returned the real CloudDocumentsComposeActivity class
        // until nav-graph slice 4 Task 9 deleted it and pointed the arm at targetForMigratedScreen,
        // the same shape Screen.CustomRepositories/Screen.ProgressStatus now share.
        Screen.CloudDocuments to NavRoutes.cloudDocuments(),
        // — nav-graph slice 8, Task B7: slice 7's seven destinations —
        // Every live caller builds its route directly (B3-B6); these rows are the routing default for
        // a bare Screen.X. Each argument-free form is a real state, not a missing argument:
        //  - GridChoosePassageBook: isScripture=false is the grid's non-scripture mode; the scripture
        //    callers (CurrentBiblePage/CurrentCommentaryPage) pass gridChoosePassage(isScripture = true).
        //  - ChooseDocument: no type means "the saved filter" (chooseDocumentInitialTypeFilter).
        //  - TextDisplaySettings: no scope means GLOBAL, exactly what a bare classic launch opened
        //    (TextDisplaySettingsComposeActivityBareIntentTest).
        // Their targetFor arms keep the Activity bodies until Task F6 deletes the Activities.
        Screen.ChooseGeneralBookKey to NavRoutes.CHOOSE_GENERAL_BOOK_KEY,
        Screen.ChooseMapKey to NavRoutes.CHOOSE_MAP_KEY,
        Screen.ChooseDictionaryWord to NavRoutes.CHOOSE_DICTIONARY_WORD,
        Screen.GridChoosePassageBook to NavRoutes.gridChoosePassage(),
        Screen.ChooseDocument to NavRoutes.chooseDocument(),
        Screen.WorkspaceSelector to NavRoutes.WORKSPACE_SELECTOR,
        Screen.TextDisplaySettings to NavRoutes.textDisplaySettings(),
        // — nav-graph slice 8, Task C3 —
        // Backup's only callers are BackupControl.backupPopup (in-graph on the host, cross-Activity from
        // StartupActivity); the row is the routing default for a bare Screen.Backup.
        Screen.Backup to NavRoutes.BACKUP,
        // — nav-graph slice 8, Task D3 — InstallZipComposeActivity is now only the exported redirect; in-app
        // callers use NavHostComposeActivity.openInstallZip (never awaitIntent -- InstallZipAwaitGuardTest).
        Screen.InstallZip to NavRoutes.installZip(),
    )

    /** The Activity class implementing [screen]. */
    fun targetFor(screen: Screen): Class<*> = when (screen) {
        Screen.Calculator -> CalculatorComposeActivity::class.java
        Screen.SearchIndexProgress -> targetForMigratedScreen(screen)
        Screen.SearchIndex -> targetForMigratedScreen(screen)
        Screen.SearchResults -> targetForMigratedScreen(screen)
        Screen.ReadingPlanSelector -> targetForMigratedScreen(screen)
        Screen.DailyReadingList -> targetForMigratedScreen(screen)
        Screen.ReadingPlan -> targetForMigratedScreen(screen)
        Screen.ChooseGeneralBookKey -> ChooseGeneralBookKeyComposeActivity::class.java
        Screen.ChooseMapKey -> ChooseMapKeyComposeActivity::class.java
        Screen.ChooseDictionaryWord -> ChooseDictionaryWordComposeActivity::class.java
        Screen.GridChoosePassageBook -> GridChoosePassageComposeActivity::class.java
        Screen.ChooseDocument -> ChooseDocumentComposeActivity::class.java
        Screen.Download -> targetForMigratedScreen(screen)
        Screen.Search -> targetForMigratedScreen(screen)
        Screen.EpubSearch -> targetForMigratedScreen(screen)
        Screen.EpubSearchResults -> targetForMigratedScreen(screen)
        Screen.MyDocuments -> targetForMigratedScreen(screen)
        Screen.MyDocumentPages -> targetForMigratedScreen(screen)
        Screen.CloudDocuments -> targetForMigratedScreen(screen)
        Screen.WorkspaceSelector -> WorkspaceSelectorComposeActivity::class.java
        // The ten classic AI-cluster *ComposeActivity classes were deleted in nav-graph Task 10;
        // the reading-plan, search and settings clusters followed in nav-graph 3/5/6 Task 9, the
        // bookmark cluster (LabelEdit/ManageLabels/Bookmarks) in nav-graph slices 2+4 Task 7, and
        // MyDocuments/MyDocumentPages/Download/CustomRepositories/CustomRepositoryEditor/
        // ProgressStatus/CloudDocuments in nav-graph slice 4 Task 9. See targetForMigratedScreen
        // below for why all 33 of those arms are kept, one per screen.
        Screen.AiConnectionSettings -> targetForMigratedScreen(screen)
        Screen.AiProviders -> targetForMigratedScreen(screen)
        Screen.AiModels -> targetForMigratedScreen(screen)
        Screen.AiPrompts -> targetForMigratedScreen(screen)
        Screen.PromptEdit -> targetForMigratedScreen(screen)
        Screen.GlobalToolPermissions -> targetForMigratedScreen(screen)
        Screen.ToolInfo -> targetForMigratedScreen(screen)
        Screen.AiDocumentFilter -> targetForMigratedScreen(screen)
        Screen.RawLogHistory -> targetForMigratedScreen(screen)
        Screen.RawLlmLog -> targetForMigratedScreen(screen)
        Screen.LabelEdit -> targetForMigratedScreen(screen)
        Screen.ManageLabels -> targetForMigratedScreen(screen)
        Screen.Bookmarks -> targetForMigratedScreen(screen)
        Screen.ReadingProgress -> targetForMigratedScreen(screen)
        Screen.Settings -> targetForMigratedScreen(screen)
        Screen.ReadingProgressSettings -> targetForMigratedScreen(screen)
        Screen.SyncSettings -> targetForMigratedScreen(screen)
        Screen.Startup -> StartupComposeActivity::class.java
        Screen.InstallZip -> InstallZipComposeActivity::class.java
        Screen.TextDisplaySettings -> TextDisplaySettingsComposeActivity::class.java
        Screen.CustomRepositories -> targetForMigratedScreen(screen)
        Screen.CustomRepositoryEditor -> targetForMigratedScreen(screen)
        Screen.Backup -> BackupComposeActivity::class.java
        Screen.ProgressStatus -> targetForMigratedScreen(screen)
    }

    /**
     * The body of every arm above whose screen now lives wholly in the Compose navigation graph and
     * has no Activity of its own: the ten AI screens (nav-graph Task 10), the reading-plan, search
     * and settings clusters (nav-graph 3/5/6 Tasks 1-8), the bookmark cluster --
     * `Bookmarks`/`ManageLabels`/`LabelEdit` (nav-graph slices 2+4 Task 7) -- and the seven nav-graph
     * slice 4 destinations whose classic Activities Task 9 deleted: `MyDocuments` (Task 6's arm was
     * already pointed here; the class itself was deleted only now), `MyDocumentPages`, `Download`,
     * `CustomRepositories`, `CustomRepositoryEditor`, `ProgressStatus` and `CloudDocuments`.
     * Twenty-four of these 33 arms reach the graph through [MIGRATED] via [intentFor]/[open], so
     * [intentFor] never falls through to [targetFor] for them; the other NINE -- `RawLlmLog`,
     * `SearchResults`, `EpubSearchResults`, `SearchIndexProgress`, `LabelEdit`, `ManageLabels`,
     * `MyDocumentPages`, `Download` and `CustomRepositoryEditor` -- are deliberately absent from
     * [MIGRATED]. The first six: an argument-free route would render an empty screen with nothing
     * to show (the first four) or the argument is required and non-null (the label pair, whose
     * `data`/`ManageLabelsData` payload has no meaningful empty default). The last three, added by
     * Task 9's deletion: `MyDocumentPages`'s route takes three REQUIRED arguments
     * (documentId/documentInitials/documentName) with no empty default; `Download`'s route takes
     * five arguments and every real caller needs to choose among them (firstDownload/addons/search/
     * etc.), so a bare argument-free entry has no caller that actually wants it; and
     * `CustomRepositoryEditor`'s `repositoryId` is OPTIONAL but its absence means "new repository",
     * a meaning only the `CustomRepositories` arm's own `navigate(customRepositoryEditor(null))`
     * call may supply deliberately. So reaching any of the nine by a bare [Screen] throws here
     * instead. The label pair's six outside callers
     * (`MenuCommandHandler`, `OptionsMenuItems` x2, `BibleView`, `CurrentGeneralBookPage`,
     * `TextDisplaySettingsComposeActivity`) build
     * `NavHostComposeActivity.intentFor(context, NavRoutes.manageLabels(data))` directly instead of
     * going through [intentFor], for the same reason `RawLlmLog`'s callers already did (see
     * `BibleJavascriptInterface.kt:1094`, `LinkControl.kt:428`, `SearchControl.kt:109`).
     *
     * This throws rather than returning a real class so that misuse (a future caller reintroducing
     * a direct [targetFor] call for a graph-only screen) fails loudly instead of returning a class
     * that no longer exists.
     *
     * The arms are kept as 33 SEPARATE arms -- never merged into one combined
     * `Screen.A, Screen.B -> ...` -- because the Classic*RemovalGuardTest family text-scans this
     * file for a literal "Screen.X ->" per screen and treats a missing arm as an offender.
     */
    private fun targetForMigratedScreen(screen: Screen): Nothing =
        error("$screen is fully migrated into the Compose nav graph; use MIGRATED/intentFor, not targetFor")

    /**
     * Intent for [screen], routed old/new. Callers that need the result (the calculator's PIN
     * unlock uses `startActivityForResult` / `awaitIntent`) build on this instead of [open].
     */
    fun intentFor(context: Context, screen: Screen): Intent =
        MIGRATED[screen]
            ?.let { route -> NavHostComposeActivity.intentFor(context, route) }
            ?: Intent(context, targetFor(screen))

    fun open(context: Context, screen: Screen) {
        context.startActivity(intentFor(context, screen))
    }
}
