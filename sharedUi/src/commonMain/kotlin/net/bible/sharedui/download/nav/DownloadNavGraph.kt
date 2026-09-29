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

package net.bible.sharedui.download.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.sharedcore.download.CustomRepositoryController
import net.bible.sharedcore.download.CustomRepositoryEditorController
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.anySelectedDeletable
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexProgressController
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.cloud.CloudDocumentsScreen
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.download.CustomRepositoriesScreen
import net.bible.sharedui.download.CustomRepositoryEditorScreen
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.search.SearchIndexProgressScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * [CustomRepositoriesScreen]'s platform-supplied slots, ported from classic
 * `CustomRepositoriesComposeActivity`.
 *
 * This is the cluster's ROOT destination: nothing inside any graph navigates to it (it is reached
 * only as the host's start destination, through `ScreenLauncher.MIGRATED`), while it is itself the
 * one that navigates to [CustomRepositoryEditorDeps]'s destination.
 *
 * - [controllerFor] builds the [CustomRepositoryController] around the [onDuplicate] callback the
 *   arm hands it. It is a per-entry factory, not a host-memoised one, for the reason classic used a
 *   fresh Activity every time: this destination is the host's start destination on every live edge,
 *   so there is no sitting-child composition for a memoised controller to survive.
 *
 * - [title] is the WINDOW title, classic's `android:label="@string/custom_repositories"` (one host
 *   now serves every cluster) -- the same string [CustomRepositoriesScreen] draws in its own top bar.
 *
 * - [onDuplicate] is classic `handleResult`'s `ToastEvent(duplicate_custom_repository)` path,
 *   surfaced when [CustomRepositoryController.applyResult] rejects an upsert as a duplicate name. It
 *   is threaded through [controllerFor] rather than set on the controller afterwards, because the
 *   controller's own `onDuplicate` var has to be wired before the first `applyResult` call could
 *   possibly fire it.
 */
class CustomRepositoriesDeps(
    val controllerFor: (onDuplicate: (String) -> Unit) -> CustomRepositoryController,
    val title: String,
    val onDuplicate: (name: String) -> Unit,
)

/**
 * [CustomRepositoryEditorScreen]'s platform-supplied slots, ported from classic
 * `CustomRepositoryEditorComposeActivity`.
 *
 * This is the cluster's CHILD destination, and its only entry is from [CustomRepositoriesDeps]'s
 * `onRowClick`/`onCreate` -- see [DownloadNavDeps.repositoryEditorResults] for what that means for
 * its channel.
 *
 * - [controllerFor] builds the [CustomRepositoryEditorController] around the resolved [initialFor]
 *   payload. It takes **no** `onResult` lambda, unlike every controller factory in the bookmark
 *   cluster: the real constructor is `(service, scope, initial)`
 *   (`CustomRepositoryEditorController.kt:40`), which reports no outcome of its own at all -- the
 *   arm builds `RepositoryResult`s straight off the controller's own
 *   `buildSaveResult()`/`buildDeleteResult()`/`buildCancelResult()` and hands them to the channel
 *   itself, so an `onResult` slot here would never be called.
 *
 * - [title] is the WINDOW title, the same `@string/custom_repositories` classic's manifest gives
 *   BOTH Activities in this cluster (`AndroidManifest.xml`), and the same string
 *   [CustomRepositoryEditorScreen] draws in its own top bar.
 *
 * - [initialFor] resolves the route's OPTIONAL id (plan D9) to the row being edited: `null` is a
 *   brand-new repository -- classic's `newItem()`, a blank `RepositoryResult()` -- and a non-null id
 *   is looked up through the SAME `CustomRepositoryService` the list uses
 *   (`CoreModule.kt:88`), never a JSON payload carried on the route. `RepositoryResult` is a
 *   `:sharedCore` data class with no serializer, and `NavResults.kt`'s closing note forbids adding
 *   one -- an id is the only thing `NavRoutes.customRepositoryEditor` can carry, so this is the seam
 *   that turns it back into the row.
 *
 * - [readClipboard] is classic `paste()`: the clipboard's primary text clip, or `null` when there is
 *   none. `commonMain` has no `ClipboardManager`, so reading it is host work; what the arm does with
 *   the result (`controller.setUrl`) is unchanged from classic.
 */
class CustomRepositoryEditorDeps(
    val controllerFor: (initial: RepositoryResult) -> CustomRepositoryEditorController,
    val title: String,
    val initialFor: suspend (id: Long?) -> RepositoryResult,
    val readClipboard: () -> String?,
)

/**
 * `PROGRESS_STATUS_PATTERN`'s platform-supplied slots, ported from classic
 * `ProgressStatusComposeActivity`. Modelled on [net.bible.sharedui.search.nav.SearchIndexProgressDeps]
 * (`SearchNavGraph.kt:301-306`), minus the search-specific members -- this destination is a plain
 * multi-job viewer with an OK button, not tied to a single document's index status.
 *
 * - [requestNotificationPermission] is classic `onResume`'s
 *   `CommonUtils.requestNotificationPermission(this)` (`ProgressStatusComposeActivity.kt:84`), which
 *   needs a real `Activity`. It is a plain `() -> Unit` here; the host launches the suspend call in
 *   its own scope.
 * - [observeJobs] is the `onResume`/`onPause` PAIR (`:82-98`) as one call: it runs classic's initial
 *   `refreshJobs()`, registers the `WorkListener`, and returns the un-registration. The pair is
 *   mandatory -- a leaked listener holds `onJobs`, which closes over this destination's controller,
 *   so leaking it leaks the destination. The destination drives it from `LifecycleResumeEffect`,
 *   whose `onPauseOrDispose` is exactly classic's `onPause`, plus the disposal case an Activity never
 *   had to think about.
 *
 *   **Takes ONE callback, unlike [net.bible.sharedui.search.nav.SearchIndexProgressDeps.observeJobs]'s
 *   two** (`SearchNavGraph.kt:303`): its second callback drives the search graph's "indexing
 *   finished" routing, and `ProgressStatus` has no such routing -- it only ever refreshes its job
 *   list. The narrowing is deliberate, not an oversight.
 * - [title] is `R.string.progress_status`, screen and window. `Strings.kt` has no entry for it, so
 *   it stays an `R.string` feed for now.
 */
class ProgressStatusDeps(
    val title: String,
    val requestNotificationPermission: () -> Unit,
    val observeJobs: (onJobs: (List<ProgressJob>) -> Unit) -> () -> Unit,
)

/**
 * [NavRoutes.DOWNLOAD_PATTERN]'s platform-supplied slots, ported from classic
 * `DownloadComposeActivity` -- the cluster's largest host, and the only one whose destination is
 * BOTH a document list and a live download manager.
 *
 * - [controllerFor] builds the screen's [DocumentSelectionController] around the type filter the arm
 *   resolved from the route (classic `onCreate`'s `controller.setTypeFilter(initialTypeFilter())`,
 *   `DownloadComposeActivity.kt:228`). It is HOST-MEMOISED, not per-entry: the controller is only
 *   half of this screen's state -- the host also holds the loaded `Book` list, the repoIdentity
 *   maps and the `DocRow` mirror that every JSword seam below reads -- and `CustomRepositories`
 *   sits on top of this destination, disposing its composition. Same shape as
 *   [net.bible.sharedui.mydocuments.nav.MyDocumentsDeps.controllerFor].
 *
 * - [title] is BOTH the window title and the title [DocumentSelectionScreen] draws, the way
 *   [ProgressStatusDeps.title] is: classic fed the screen `strings.downloadDocuments` (`:280`) and
 *   the window the manifest's `android:label`, which are the same string.
 *
 * - [topBarActions] is the overflow menu, host-composed because every row of it is host work
 *   (`R.drawable` icons, the `hasErrors` flags, an `awaitIntent` for Install zip). It takes
 *   `firstDownload` because classic's menu HIDES the Install-zip row in that mode (`:857`), and
 *   `firstDownload` is a ROUTE argument only the arm can read.
 *
 * - [askIfWantToProceed] is the "do you want to download over mobile data" gate (`:460-478`), a
 *   platform `AlertDialog` that can answer NO. Classic ran it before `setContent`; an arm cannot
 *   refuse to compose, so it runs as an effect and LEAVES on a no (plan D3 -- "compose, then maybe
 *   leave"). Everything after it in classic's `onCreate` coroutine is sequenced behind it here too.
 *
 * - [refreshCatalogue] is classic's `downloadDocJson()` + `loadDocuments(refresh)` +
 *   `updateLastRepoRefreshDate()` block (`:239-243`) as one suspend call. [refresh] means FORCE a
 *   repository re-fetch (the pull-to-refresh gesture); the host still applies its own
 *   `isRepoBookListOld` staleness cache when it is false, which is why the arm's first call passes
 *   `false` rather than trying to read `settings` from `commonMain`.
 *
 * - [onAutoDownload] is `handleAutoDownloadExtras()` (`:757-786`): the `documentIds` JSON payload
 *   and the `downloadRecommended` defaults list, both now route arguments rather than Intent extras.
 *
 * - [reloadCatalogueIfRequested] is classic `onCustomRepositories()`'s second half (`:899-905`):
 *   `awaitIntent(...)` then `loadDocuments(true)`. The hop is now an in-graph `navigate` -- an
 *   Intent would launch the host at itself, since `CustomRepositories` is a destination of this very
 *   graph -- and an in-graph hop has no result to await, so the host ARMS the reload when its menu
 *   row navigates and this call performs it on the way back. The arm drives it from an effect that
 *   is deliberately NOT one-shot: every (re)composition of this entry is exactly a return to it.
 *
 * - [onCancelDownload] is the per-row cancel button (`:344`), which resolves the row's docId back to
 *   a `Book` through the host's map and calls `downloadControl.cancelDownload`. **The plan's deps
 *   list omits it**; the screen takes an `onCancel` slot regardless, and there is nothing in
 *   `commonMain` that could fill it.
 *
 * - [hasBible] drives the `firstDownload` OK gate's enabled state (`:353`). It LATCHES host-side --
 *   once a Bible is installed the button stays enabled -- so it is a flow the host owns, not arm
 *   state.
 *
 * - [subscribeDownloadProgress] is classic's `lifecycleScope.launch { bridge.statuses.collect {
 *   applyProgress(it) } }` (`:253-255`), scoped to this destination's composition instead of the
 *   Activity, and returning the unsubscribe. **It carries no payload callback**, unlike the plan's
 *   `(onStatuses: (Map<String, RowDownloadStatus>) -> Unit) -> () -> Unit`: `RowDownloadStatus` is an
 *   `:app` type (`DownloadProgressBridge.kt:29`) that `commonMain` cannot name, and applying a
 *   status needs `booksById` and the `currentRows` mirror, which are host state -- classic's
 *   `applyProgress` (`:565`) is host code, and [DocumentSelectionController] has no `applyProgress`
 *   of its own to hand the map to. So the arm owns the SUBSCRIPTION WINDOW and the host owns the
 *   payload. The narrowing is deliberate, the same kind [ProgressStatusDeps.observeJobs] documents.
 *
 * - [subscribeMonitoring] is the whole `onStart`/`onStop` pair (`:376-394`) as one call: register
 *   the progress bridge, start `downloadControl`'s monitoring, and -- only when `firstDownload` --
 *   add the `JobManager` work listener that flips [hasBible]. It is keyed on `firstDownload`
 *   because that extra listener is conditional on it, and STARTED-scoped rather than
 *   composition-scoped because that is what classic's pair was.
 *
 * - [persistTypeFilter] is the `selected_document_filter_no` write classic did inline in
 *   `onTypeFilterChange` (`:311`), and [initialTypeFilter] its read, plus the `addons` branch. The
 *   `"type"` extra classic also read is deliberately NOT part of it (plan D1): the tree's only
 *   `putExtra("type"` is gone, so the argument would be dead.
 *
 * **Fields the plan's list named that are not here.** `confirmDownload`, `confirmDelete`,
 * `confirmDeleteIndex`, `onAbout` and `onUnlock` are the [DocumentSelectionController]'s OWN
 * constructor seams (`onSelect`/`onDelete`/`onDeleteIndex`/`onAbout`/`onUnlock`), so the host wires
 * them into the controller it builds in [controllerFor] and the arm never names them; `showErrors`
 * and `onInstallZip` are rows of [topBarActions], which the host composes. Adding deps fields the
 * arm cannot call would be scaffolding, not a seam -- the platform dialogs behind those names are
 * exactly as host-side as the plan says, just reached through the two slots above.
 */
class DownloadDeps(
    val controllerFor: (initialTypeFilter: DocTypeFilter) -> DocumentSelectionController,
    /**
     * Identity of the session [controllerFor] is memoised on -- a value the host regenerates ONLY
     * when it builds a NEW session, and never otherwise. Read AFTER [controllerFor] in the arm, so
     * a first composition sees the token of the session that call just built.
     *
     * The arm's one-shots are keyed on this and on the route's own arguments (see
     * [downloadSeedKey]) rather than on a bare boolean, because "this entry has been set up" is not
     * the proposition they need to encode -- "this entry has been set up AGAINST THIS SESSION, WITH
     * THESE ARGUMENTS" is. The two halves close two distinct symptoms of the same defect, found by
     * slice 4's final whole-batch review:
     *
     * - **Session identity.** The host's session is a plain Activity field and is NOT saved state,
     *   while the one-shots are `rememberSaveable`. Any recreation that preserves saved state --
     *   a system dark-mode toggle or a font-size change (the host's manifest `configChanges` covers
     *   neither `uiMode` nor `fontScale`), "Don't keep activities", an ordinary low-memory kill, or
     *   process death -- restores the flags `true` over a session rebuilt EMPTY, and a bare boolean
     *   then skips [askIfWantToProceed], [requestNotificationPermission], [refreshCatalogue] and
     *   [onAutoDownload] alike, leaving a blank, unrefreshed catalogue. Classic re-ran `onCreate`
     *   and recovered; the token restores that. The worst instance is first-run onboarding, where
     *   the dropped call IS the auto-download the screen exists to perform.
     *
     * - **Argument signature.** Every inbound route reaches a live host through
     *   `NavHostComposeActivity.navigateToRoute`, i.e. `navigate(route) { launchSingleTop = true }`.
     *   When `Download` is already top that takes `launchSingleTopInternal`, which rebuilds the
     *   entry from the old one with the SAME id and the SAME saved state and does not dispose the
     *   arm -- so a bare boolean stays `true` and the new route's `addons`, `search` and
     *   `documentIds` are silently dropped. Keying on the arguments makes the re-delivery behave
     *   like the fresh launch classic got. An IDENTICAL re-delivery still changes nothing, which is
     *   the other edge of the same contract.
     */
    val sessionToken: () -> String,
    val title: String,
    val topBarActions: @Composable (firstDownload: Boolean) -> Unit,
    val askIfWantToProceed: suspend () -> Boolean,
    val requestNotificationPermission: () -> Unit,
    val refreshCatalogue: suspend (refresh: Boolean) -> Unit,
    val onAutoDownload: suspend (documentIds: String?, downloadRecommended: Boolean) -> Unit,
    val reloadCatalogueIfRequested: suspend () -> Unit,
    val onCancelDownload: (docId: String) -> Unit,
    val hasBible: StateFlow<Boolean>,
    val subscribeDownloadProgress: () -> () -> Unit,
    val subscribeMonitoring: (firstDownload: Boolean) -> () -> Unit,
    val persistTypeFilter: (DocTypeFilter) -> Unit,
    val initialTypeFilter: (addons: Boolean) -> DocTypeFilter,
)

/**
 * [NavRoutes.CLOUD_DOCUMENTS_PATTERN]'s platform-supplied slots, ported from classic
 * `CloudDocumentsComposeActivity` -- the batch's LAST arm, and its only GATED one: the destination
 * composes, then may leave (plan D3), the way [DownloadDeps.askIfWantToProceed] already does.
 *
 * - [controllerFor] builds the [CloudDocumentsController] around the sync/action/arrangement seams
 *   the host wires. It is a PER-ENTRY factory, [CustomRepositoriesDeps.controllerFor]'s idiom rather
 *   than [DownloadDeps.controllerFor]'s host-memoised one (task-8 fix round 1): this destination has
 *   TWO distinct entry points (`Download`'s overflow row, Settings' sync row) and no covering child,
 *   so a genuine leave-and-reopen within one host session is a real, reachable path, and classic reset
 *   every filter/selection/search/arrangement on each such open by getting a fresh Activity and a
 *   fresh controller -- a memoised singleton would let all of that silently survive a round trip
 *   instead, a user-visible behaviour change from classic. [openOrGate] and [refreshFromNetwork] are
 *   `suspend () -> Boolean`/`suspend () -> Unit` with no controller parameter of their own (their
 *   signatures are frozen the same way a screen's are), so the host reaches the CURRENT entry's
 *   controller through a reassignable var [controllerFor]'s own implementation updates, the same
 *   idiom [CustomRepositoriesDeps.controllerFor]'s `.apply { this.onDuplicate = onDuplicate }` uses
 *   to hand a per-entry callback to a per-entry controller. This also reproduces classic's own
 *   observable shape verbatim: classic's `isRefreshing` is hard-coded `false` and the loading spinner
 *   is driven entirely by `controller.busy` (`pushBusy`/`pushBusy` pairs inside
 *   `openOrGate`/`refreshFromNetwork` themselves), which only works when those functions already
 *   hold the controller they push busy onto -- the CURRENT one, via that var, not a fixed one.
 *
 * - [title] is BOTH the window title and the string [CloudDocumentsScreen] draws in its own top bar
 *   (classic `R.string.document_sync_manage_title`, both the manifest label and the screen's own
 *   title argument).
 *
 * - [topBarActions] is the overflow menu (Sync now / Re-scan / Help) -- classic's `OverflowMenu()`
 *   (`:346-375`): three `painterResource` icons, `CommonUtils.showHelpDialog`, and a `CloudSync
 *   .signedIn` read at composition, all host-side. `@Composable RowScope.() -> Unit`, exactly
 *   [CloudDocumentsScreen.topBarActions]'s own type -- unlike [DownloadDeps.topBarActions] it takes
 *   no argument, because this destination has no `firstDownload`-shaped route argument to gate a row
 *   on.
 *
 * - [openOrGate] is classic `openOrGate()` in full (`:193-208`) -- the sign-in gate, the initial
 *   cache seed and the conditional network refresh are ALL host-side and ALL sequenced inside this
 *   one suspend call, because every one of them needs either an `ActivityBase` (`CloudSync.signIn`)
 *   or the shared controller. `false` means it already toasted and wants the arm to leave (plan D3);
 *   the arm's whole job on that branch is `navController.popOrExit(deps.exitHost)`. `true` means the
 *   controller is already correctly seeded (and, if warranted, a network refresh already kicked off)
 *   -- there is nothing further for the arm to do on that branch either.
 *
 * - [seedItems] is classic's cache-only scan+flatten (`DocumentSync.scanCached(...).map {
 *   toCloudDocItem() }`, the shared middle of `openOrGate` `:203-206` and `renderFromCache`
 *   `:226-232`). It is PURE -- no controller mutation of its own -- deliberately: `renderFromCache`
 *   is reached from `onShowRemovedChange`, which [CloudDocumentsController.setShowRemoved] invokes
 *   UNCONDITIONALLY on every call, so a [seedItems] that itself called `setShowRemoved` again would
 *   recurse forever through that callback. [openOrGate]'s own host implementation calls this same
 *   function for its one scan, so the two reload paths (initial gate, show-removed toggle) can never
 *   silently drift apart.
 *
 * - [refreshFromNetwork] is classic `refreshFromNetwork()` (`:212-218`): a network
 *   `DocumentSync.scan(...)`, flattened and pushed onto the shared controller with a `pushBusy`
 *   pair around it. Called by [openOrGate]'s own conditional refresh, by the arm's pull-to-refresh,
 *   and by the post-transfer collector [subscribeProgress] drives.
 *
 * - [subscribeProgress] is classic's `bridge.register()`/`unregister()` PLUS the
 *   `bridge.running.drop(1).collect { ... }` body (`:105`, `:180`, `:111-116`) as one subscribe/stop
 *   pair: it owns the whole `CloudSyncProgressBridge` lifetime and reports every POST-`drop(1)`
 *   transfer-running transition to [onRunning]. The arm's own job on each callback is exactly
 *   classic's collector body: flip `controller.setTransferRunning` and, on the false (transfer
 *   finished) edge, call [refreshFromNetwork].
 *
 * - [statusFilterLabels]/[categoryFilterLabels] are classic's own `statusFilterLabels`/
 *   `categoryFilterLabels` (`:387-406`) with the `getString` calls kept and the `CloudDocFilter`/
 *   `DocCategory` pairing stripped -- `Strings` has no cloud-filter equivalent, so these stay
 *   `R.string` feeds, and the arm zips the returned labels back onto the fixed filter ORDER classic's
 *   spinners used (see `cloudStatusFilters`/`cloudCategoryFilters` below). [statusFilterLabels]
 *   takes `showRemoved` because the REMOVED label is only the eighth one when it is true, exactly
 *   like classic's own conditional `add`.
 *
 * - Classic `confirmRemove`/`confirmPurge` (`:281-305`) are GONE from here (Task 16/17 run-2 plan):
 *   the question they asked now lives in `CloudDocumentsController.dialog`
 *   (`CloudDocumentsDialog.ConfirmRemove`/`ConfirmPurge`, plan Task 17), rendered by
 *   `CloudDocumentsScreen` itself and answered through `controller.confirmDialog()`/`dismissDialog()`.
 *   `onConfirm` -- classic's positive button (`DocumentSyncService.start` +
 *   `applyRemoval`/`applyPurge` + `clearSelection`) -- is now `CloudDocumentsController`'s own
 *   `onConfirmRemove`/`onConfirmPurge` constructor callbacks, bound once inside `controllerFor`
 *   rather than rebuilt per call.
 *
 * - [countLabel] is classic `countLabel` (`:339-343`), the fourth plural call site (design §2.4
 *   counted three; there are FOUR -- `:285` branches between two, plus `:297`/`:341`/`:342`), used by the
 *   Sync-now preview the overflow menu's "Sync now" row builds -- also host-only (needs
 *   `Formatter.formatShortFileSize` and `resources.getQuantityString`).
 *
 * **Every field above is reached one way or another from this destination's own composition or from
 * [CloudDocumentsController]'s constructor closures the host builds around [controllerFor] -- none is
 * scaffolding for a caller that does not exist.**
 */
class CloudDocumentsDeps(
    val controllerFor: () -> CloudDocumentsController,
    val title: String,
    val topBarActions: @Composable RowScope.() -> Unit,
    val openOrGate: suspend () -> Boolean,
    val seedItems: suspend () -> List<CloudDocItem>,
    val refreshFromNetwork: suspend () -> Unit,
    val subscribeProgress: (onRunning: (Boolean) -> Unit) -> () -> Unit,
    val statusFilterLabels: (showRemoved: Boolean) -> List<String>,
    val categoryFilterLabels: () -> List<String>,
    val countLabel: (count: Int, bytes: Long?) -> String,
)

/**
 * Platform-supplied slots the Documents/downloads cluster's destinations need but `commonMain`
 * cannot provide. Same top-level shape as [net.bible.sharedui.bookmark.nav.BookmarkNavDeps]:
 * [exitHost] and [setWindowTitle] are graph-wide, one nested holder per destination below.
 *
 * **This class GREW, one task at a time, and Task 8 is the last growth.** Slice 4 migrated the
 * cluster's destinations across four tasks, each adding its own nested deps field here and its own
 * arm to [downloadNavGraph], the same way [net.bible.sharedui.bookmark.nav.BookmarkNavDeps] grew its
 * three nested holders. Task 3 built [customRepositories] and [customRepositoryEditor], Task 4
 * [progressStatus], Task 7a [download], Task 8 [cloudDocuments] -- the cluster's seventh and final
 * destination. Nothing further grows this class. Only [repositoryEditorResults] was declared up
 * front, for [net.bible.sharedui.bookmark.nav.BookmarkNavDeps]'s own reason: this is the file that
 * owns it, and creating it later would widen this class's constructor for every caller that already
 * built one. A per-destination deps field cannot be front-loaded the same way -- each task introduces
 * the very type it would have to name -- so building one early would have been scaffolding for a
 * destination that did not exist yet.
 */
class DownloadNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) -- not the on-screen top-bar title a screen
     * draws for itself. Called from each destination's `LaunchedEffect(title)`, keyed on the VALUE
     * so a state-derived title cannot go stale, and never from a screen composable: screen
     * signatures are frozen for this migration.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * How the repository EDITOR hands its result back to the LIST -- see [NavResultChannel]'s own
     * kdoc for the two branches the channel can take.
     *
     * **Only the IN-GRAPH branch is reachable, and it is the only one that ever will be.** The
     * editor is entered from exactly one place, [CustomRepositoriesDeps]'s arm
     * (`navController.navigate(NavRoutes.customRepositoryEditor(id))` / `(null)`), it is absent from
     * `ScreenLauncher.MIGRATED` (its payload argument means there is no argument-free route to give
     * it), and `NavRoutes.customRepositoryEditor` is called from nowhere else in the tree. So, unlike
     * every other channel in these graphs, this one's `exitWithResult` lambda is a hard `error(...)`
     * on the HOST side (`NavHostComposeActivity.repositoryEditorResults`) rather than a real Intent
     * packing -- see that field's own kdoc for the argument in full. Reaching that branch from this
     * graph would mean the editor had been made externally entrable without ever being given a
     * result contract of its own, which is a defect worth failing loudly on rather than packing an
     * Intent nobody defined.
     */
    val repositoryEditorResults: NavResultChannel<RepositoryResult>,
    // — CUSTOM REPOSITORIES —
    val customRepositories: CustomRepositoriesDeps,
    // — CUSTOM REPOSITORY EDITOR —
    val customRepositoryEditor: CustomRepositoryEditorDeps,
    // — PROGRESS STATUS —
    val progressStatus: ProgressStatusDeps,
    // — DOWNLOAD —
    val download: DownloadDeps,
    // — CLOUD DOCUMENTS —
    val cloudDocuments: CloudDocumentsDeps,
)

/**
 * Pop to the parent destination, or -- when there is none, i.e. this destination is the host's start
 * destination -- leave the host. The same one-line extension every other cluster graph carries; the
 * boolean branch itself is [popOrExitOnFailedPop], shared so that "a failed pop still leaves" is
 * decided in one place.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

/**
 * The `Download` arm's one-shot key: WHICH session, with WHICH route arguments -- see
 * [DownloadDeps.sessionToken] for why both halves are needed and what each one closes.
 *
 * The two nullable strings are LENGTH-PREFIXED rather than interpolated plainly, so an absent
 * argument and a present-but-empty (or literally `"null"`) one cannot collide into the same key.
 * [sessionToken] itself is opaque to this function; the host guarantees only that it changes exactly
 * when a new session is built.
 */
private fun downloadSeedKey(
    sessionToken: String,
    firstDownload: Boolean,
    downloadRecommended: Boolean,
    search: String?,
    addons: Boolean,
    documentIds: String?,
): String {
    fun enc(value: String?) = if (value == null) "-" else "${value.length}:$value"
    return "$sessionToken|$firstDownload|$downloadRecommended|$addons|${enc(search)}|${enc(documentIds)}"
}

/**
 * Fix batch 1 §2.8 (F81/F101): the gate's key is the route's ARGUMENTS only. [downloadSeedKey]'s
 * session half exists because a recreate rebuilds the session EMPTY and it must be re-seeded
 * (refresh, auto-download); the user's YES is not session state -- a recreate must not re-ask it.
 */
private fun downloadGateKey(
    firstDownload: Boolean,
    downloadRecommended: Boolean,
    search: String?,
    addons: Boolean,
    documentIds: String?,
): String = downloadSeedKey(sessionToken = "", firstDownload, downloadRecommended, search, addons, documentIds)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The Documents/downloads cluster's destinations. Registered into the app's single `NavHost` by the
 * host Activity. This task builds the batch's simplest shape -- a parent and a child, one channel
 * between them, no external entry to the child -- and the arm below is the template the later
 * `ProgressStatus`/`Download`/`CloudDocuments` arms (Tasks 4, 7a, 8) follow.
 *
 * [NavRoutes.CUSTOM_REPOSITORIES_PATTERN] is reachable through `ScreenLauncher.MIGRATED`;
 * [NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN] deliberately is not, because its `repositoryId`
 * argument is OPTIONAL and an argument-free `Screen.CustomRepositoryEditor` entry would be
 * ambiguous about which id-less state it means -- the same reasoning `Screen.LabelEdit` and
 * `Screen.ManageLabels` document in `ScreenLauncher.MIGRATED`'s own comments, except here the
 * argument-free route DOES have a defined meaning (a new repository) and is simply never reachable
 * except from this graph's own `onCreate` call.
 *
 * Between this task and Task 7b, `CustomRepositories` is reached from `DownloadComposeActivity
 * .onCustomRepositories()`, which still launches it as an Activity through the classic
 * `awaitIntent`/`ScreenLauncher` path -- the coexistence seam working as designed, since that method
 * keeps working unchanged (`ScreenLauncher.intentFor` now answers through `MIGRATED` instead of a
 * classic Activity class). Task 7b replaces it with an in-graph `navigate` once `Download` itself
 * is a destination in this same file.
 */
fun NavGraphBuilder.downloadNavGraph(navController: NavHostController, deps: DownloadNavDeps) {
    // ——— CUSTOM REPOSITORIES ———
    composable(route = NavRoutes.CUSTOM_REPOSITORIES_PATTERN) {
        val d = deps.customRepositories
        val controller = remember { d.controllerFor(d.onDuplicate) }
        val state by controller.state.collectAsState()

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // The child's result, consumed once and cleared -- the shape ManageLabels uses for
        // LabelEdit (BookmarkNavGraph.kt:641-646). Classic did this with awaitIntent + a `!!`
        // on a "data" extra (CustomRepositoriesComposeActivity.kt:94-96); the typed channel
        // removes both the Intent and the `!!` rather than preserving them (design §7.4).
        val pendingEditor by deps.repositoryEditorResults.pending.collectAsState()
        LaunchedEffect(pendingEditor) {
            if (pendingEditor == null) return@LaunchedEffect
            val result = deps.repositoryEditorResults.consume() ?: return@LaunchedEffect
            controller.applyResult(result)
        }

        CustomRepositoriesScreen(
            state = state,
            // The route argument is the repository's ID, not its JSON. Classic had to serialise the
            // whole row because an Intent extra was the only channel it had
            // (CustomRepositoriesComposeActivity.kt:91-92); a route can carry the id and let the
            // editor re-read the row from the same service the list uses. A NEW repository has no
            // id, so the argument is simply OMITTED (plan D9) -- absent, never present-and-empty.
            onRowClick = { id -> navController.navigate(NavRoutes.customRepositoryEditor(id)) },
            onCreate = { navController.navigate(NavRoutes.customRepositoryEditor(null)) },
            onUp = { navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— CUSTOM REPOSITORY EDITOR ———
    composable(
        route = NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_REPOSITORY_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val d = deps.customRepositoryEditor
        // ABSENT means NEW (plan D9). Classic asserted its payload with `!!` in the lazy `initial`
        // (CustomRepositoryEditorComposeActivity.kt:63); there is nothing to assert here, and
        // there is deliberately NO pop-on-null backstop -- null is the create-new state, so popping
        // on it would make the "create repository" button silently do nothing.
        val id = backStackEntry.arguments
            ?.read { getStringOrNull(NavRoutes.ARG_REPOSITORY_ID) }
            ?.toLongOrNull()

        // The id resolves to a row through the same service the list uses; null means a blank
        // RepositoryData(), classic's `newItem()`. Null while it loads, so the arm renders nothing
        // rather than building a controller around a half-known payload.
        val initial by produceState<RepositoryResult?>(initialValue = null, id) {
            value = d.initialFor(id)
        }
        val resolved = initial ?: return@composable
        // No `onResult` lambda: the controller's constructor is (service, scope, initial)
        // (CustomRepositoryEditorController.kt:40), and every exit below delivers explicitly.
        val controller = remember(id, resolved) { d.controllerFor(resolved) }
        val state by controller.state.collectAsState()
        var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
        val strings = LocalStrings.current

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's BackHandler (:78-81), which routed EVERY back press through the dirty gate.
        // PlatformBackHandler, never androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = true) {
            if (state.isDirty) showDiscardConfirm = true
            else deps.repositoryEditorResults.deliver(navController, controller.buildCancelResult())
        }

        CustomRepositoryEditorScreen(
            state = state,
            onUrlChange = controller::setUrl,
            onPaste = { d.readClipboard()?.let(controller::setUrl) },
            onPackageDirChange = controller::setPackageDir,
            onSave = { deps.repositoryEditorResults.deliver(navController, controller.buildSaveResult()) },
            onDelete = { deps.repositoryEditorResults.deliver(navController, controller.buildDeleteResult()) },
            onUp = { deps.repositoryEditorResults.deliver(navController, controller.buildCancelResult()) },
        )

        if (showDiscardConfirm) {
            AbConfirmDialog(
                title = null,
                message = strings.discardChangesConfirmation,
                confirmText = strings.yes,
                dismissText = strings.no,
                onConfirm = {
                    showDiscardConfirm = false
                    deps.repositoryEditorResults.deliver(navController, controller.buildCancelResult())
                },
                onDismiss = { showDiscardConfirm = false },
            )
        }
    }

    // ——— PROGRESS STATUS ———
    composable(route = NavRoutes.PROGRESS_STATUS_PATTERN) {
        val d = deps.progressStatus
        val strings = LocalStrings.current
        // onHide used to be `setResult(RESULT_OK) + finish()` (ProgressStatusComposeActivity:123-126).
        // The result was dead — the only inbound edge is a getActivity PendingIntent, which cannot
        // receive one (design §7.3) — so hiding is now an ordinary exit.
        val controller = remember { SearchIndexProgressController(onHide = { navController.popOrExit(deps.exitHost) }) }
        val jobs by controller.jobs.collectAsState()
        val noTasks by controller.noTasks.collectAsState()
        val error by controller.error.collectAsState()

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's onResume/onPause pair (:82-94): request the permission, subscribe to JobManager
        // while resumed, and reveal "no tasks" only after ~4s in case a just-launched job has not
        // registered yet. LifecycleResumeEffect is the shape SearchNavGraph:747-755 already uses,
        // and it is already imported in commonMain (SearchNavGraph.kt:30).
        //
        // The reveal is keyed on the RESUME, not on Unit. Classic posts it INSIDE onResume (:93),
        // so it re-arms every time the screen comes back; a LaunchedEffect(Unit) would fire once
        // per composition and lose that parity. SearchNavGraph:756-762 keeps the same parity with
        // the same explicit resumeTicks counter.
        var resumeTicks by remember { mutableStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumeTicks += 1
            d.requestNotificationPermission()
            val unsubscribe = d.observeJobs { controller.setJobs(it) }
            onPauseOrDispose { unsubscribe() }
        }
        LaunchedEffect(resumeTicks) {
            delay(4000)
            controller.revealNoTasksIfIdle()
        }

        SearchIndexProgressScreen(
            title = d.title,
            jobs = jobs,
            noTasks = noTasks,
            error = error,
            onHide = controller::hide,
            onDismissError = controller::dismissError,
            message = strings.taskKillWarning,
            buttonLabel = strings.okay,
        )
    }

    // ——— DOWNLOAD ———
    composable(
        route = NavRoutes.DOWNLOAD_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_FIRST_DOWNLOAD) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_DOWNLOAD_RECOMMENDED) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_DOWNLOAD_SEARCH) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_DOWNLOAD_ADDONS) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_DOCUMENT_IDS) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val d = deps.download
        val strings = LocalStrings.current
        val scope = rememberCoroutineScope()

        // Five arguments, all OPTIONAL, all read plainly -- never through NavRoutes.decodeArg, which
        // would double-decode a value the library already decoded (SearchNavGraph's searchArgs()
        // kdoc documents that trap). A flag is emitted only when true (plan D2), so "true" is the
        // whole truth test and an ABSENT argument is false.
        val args = backStackEntry.arguments
        val firstDownload = args?.read { getStringOrNull(NavRoutes.ARG_FIRST_DOWNLOAD) } == "true"
        val downloadRecommended = args?.read { getStringOrNull(NavRoutes.ARG_DOWNLOAD_RECOMMENDED) } == "true"
        val search = args?.read { getStringOrNull(NavRoutes.ARG_DOWNLOAD_SEARCH) }
        val addons = args?.read { getStringOrNull(NavRoutes.ARG_DOWNLOAD_ADDONS) } == "true"
        val documentIds = args?.read { getStringOrNull(NavRoutes.ARG_DOCUMENT_IDS) }

        // Classic :228-231, before setContent. The controller is HOST-memoised (see
        // DownloadDeps.controllerFor), so it outlives this entry -- which is why the route's own
        // initial state is applied by the effect below rather than only at construction.
        val controller = remember { d.controllerFor(d.initialTypeFilter(addons)) }

        // WHICH session, with WHICH arguments -- the proposition both one-shots below encode. Read
        // AFTER the `remember` above, so a first composition sees the token of the session that call
        // just built. [DownloadDeps.sessionToken]'s kdoc carries the whole argument; in short, "this
        // entry has been set up" is not a strong enough claim for a flag that outlives both the
        // session it was set up against (an Activity recreation) and the arguments it was set up
        // from (a `launchSingleTop` re-delivery).
        val seedKey = downloadSeedKey(
            sessionToken = d.sessionToken(),
            firstDownload = firstDownload,
            downloadRecommended = downloadRecommended,
            search = search,
            addons = addons,
            documentIds = documentIds,
        )

        // The route's initial state, applied ONCE PER (ENTRY, SESSION, ARGUMENTS). rememberSaveable,
        // never a plain remember: this entry's composition is DISPOSED while CustomRepositories
        // (reached from this screen's own overflow menu) sits on top of it, and a plain remember
        // would re-apply the route's `addons` filter and `search` over whatever the user has
        // filtered or typed since -- the exact failure BookmarkNavGraph.kt:610-622 documents for its
        // own seed. That round trip changes neither the session nor the arguments, so the key holds
        // and nothing re-applies.
        //
        // A genuinely fresh launch re-applies, which is the other half of the contract: the host is
        // singleTop, so `NavRoutes.download(addons = true)` arriving while a Download entry is
        // already open must show the ADDON filter and this route's search state, not the previous
        // entry's -- and it arrives through `launchSingleTop`, which keeps this entry's saved state
        // alive, so only the ARGUMENT half of the key can tell the two apart. The effect is KEYED on
        // seedKey, not on Unit, precisely because that re-delivery does not dispose the arm: an
        // effect keyed on Unit would never restart to notice the new arguments at all.
        // `closeSearch()` clears the query as well as closing the bar
        // (SearchModeController.clearOnClose), which is exactly a fresh launch's state.
        var seededRouteStateFor by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(seedKey) {
            if (seededRouteStateFor == seedKey) return@LaunchedEffect
            seededRouteStateFor = seedKey
            controller.setTypeFilter(d.initialTypeFilter(addons))
            if (search != null) {
                controller.setQuery(search)
                controller.openSearch()
            } else {
                controller.closeSearch()
            }
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic :233-246: the gate is already "compose, then maybe leave" (plan D3), so it stays
        // an arm-side effect rather than moving ahead of the navigate. The three calls after it are
        // sequenced BEHIND the gate exactly as classic's single onCreate coroutine sequenced them:
        // a user who says no downloads nothing and is asked for no permission.
        //
        // ONCE PER (ENTRY, SESSION, ARGUMENTS), for [seededRouteStateFor]'s reason and more sharply:
        // this is classic's onCreate, and that round trip did not recreate the Activity. Re-running
        // it on every return would re-ask the download question, re-fire the permission request,
        // re-fetch the catalogue -- and re-enqueue every requested/recommended book, since
        // onAutoDownload's downloadRequestedBooks has no BEING_INSTALLED guard of its own. The key
        // is stamped BEFORE the first suspension point on purpose: the gate is a modal dialog, so
        // nothing can navigate away underneath it, and a flag set only on success would re-ask after
        // a refusal.
        //
        // The session half of the key matters MOST here: a recreation rebuilds the session empty and
        // this block is what fills it (`refreshCatalogue`, and on onboarding `onAutoDownload`), so a
        // bare boolean restoring `true` is exactly a blank, unrefreshed catalogue with the
        // onboarding auto-download silently dropped.
        //
        // The GATE has its own, argument-only flag (fix batch 1 §2.8, F81/F101): the user's YES is
        // not session state, so a recreate re-seeds the rebuilt session but does not re-ask an
        // already-answered gate. Only a YES is remembered; a refusal leaves the screen, and a gate
        // never answered (recreated while showing) is asked again.
        val gateKey = downloadGateKey(firstDownload, downloadRecommended, search, addons, documentIds)
        var gateAnsweredFor by rememberSaveable { mutableStateOf<String?>(null) }
        var ranEntrySetupFor by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(seedKey) {
            if (ranEntrySetupFor == seedKey) return@LaunchedEffect
            ranEntrySetupFor = seedKey
            if (gateAnsweredFor != gateKey) {
                if (!d.askIfWantToProceed()) {
                    navController.popOrExit(deps.exitHost)
                    return@LaunchedEffect
                }
                gateAnsweredFor = gateKey
            }
            d.requestNotificationPermission()
            // false = do not FORCE a repository re-fetch; the host still honours its own
            // isRepoBookListOld staleness cache (classic :241-243). Positional because Kotlin
            // forbids named arguments on function types.
            d.refreshCatalogue(false)
            d.onAutoDownload(documentIds, downloadRecommended)
        }

        // Classic onCustomRepositories()'s `loadDocuments(true)` follow-up (:899-905), re-armed on
        // the way back from the in-graph hop -- see DownloadDeps.reloadCatalogueIfRequested. NOT
        // one-shot, deliberately, and that is the whole point of it: a (re)composition of this entry
        // IS the return to it. The host answers "nothing pending" on a first entry.
        LaunchedEffect(Unit) { d.reloadCatalogueIfRequested() }

        // Classic :253-255: bridge.statuses -> applyProgress, for the lifetime of the screen.
        DisposableEffect(Unit) {
            val stop = d.subscribeDownloadProgress()
            onDispose { stop() }
        }

        // Classic onStart/onStop (:376-394): bridge.register()/unregister() and
        // downloadControl.startMonitoringDownloads()/stop..., PLUS -- only when firstDownload --
        // JobManager.addWorkListener(downloadCompletionListener) and a re-updateHasBible().
        // STARTED-scoped, not composition-scoped, and keyed on firstDownload because the extra
        // listener is conditional on it.
        LifecycleStartEffect(firstDownload) {
            val stop = d.subscribeMonitoring(firstDownload)
            onStopOrDispose { stop() }
        }

        val loading by controller.loading.collectAsState()
        val displayed by controller.displayed.collectAsState()
        val grouped by controller.grouped.collectAsState()
        val languages by controller.languages.collectAsState()
        val selectedLanguage by controller.selectedLanguage.collectAsState()
        val selectedTypeFilter by controller.selectedTypeFilter.collectAsState()
        val query by controller.query.collectAsState()
        val resultCount by controller.resultCount.collectAsState()
        val selectionMode by controller.selectionMode.collectAsState()
        val selectedIds by controller.selectedIds.collectAsState()
        val error by controller.error.collectAsState()
        val dialog by controller.dialog.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val arrangement by controller.arrangement.collectAsState()
        val repositories by controller.repositories.collectAsState()
        val rememberArrangement by controller.rememberArrangement.collectAsState()
        val arrangementIsDefault by controller.arrangementIsDefault.collectAsState()
        val bibleInstalled by d.hasBible.collectAsState()

        // The pull-to-refresh spinner, classic's host `refreshing` MutableStateFlow (:163). Plain
        // remember, not rememberSaveable: it is the in-flight state of a coroutine this composition
        // owns, so it must NOT survive the composition -- a restored `true` would spin forever.
        var isRefreshing by remember { mutableStateOf(false) }

        // Classic's onBackPressed override (:396-403): back dismisses what is visually on top, so
        // the selection bar goes before the search bar (AbSelectionScaffold's precedence), and
        // anything else is the plain leave the NavHost already does. ONE gated handler with the
        // branch inside it, never two stacked ones -- two would work only by declaration ORDER
        // (back goes to the most recently registered ENABLED callback), the invisible dependency
        // BookmarkNavGraph.kt:652-659 argues against. PlatformBackHandler, never
        // androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = selectionMode || searchModeActive) {
            if (selectionMode) controller.clearSelection() else controller.closeSearch()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            DocumentSelectionScreen(
                title = d.title,
                downloadMode = true,
                loading = loading,
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            controller.closeSearch()
                            d.refreshCatalogue(true)
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                grouped = grouped,
                languages = languages,
                selectedLanguage = selectedLanguage,
                typeFilters = typeFilterLabels(strings),
                selectedTypeFilter = selectedTypeFilter,
                query = query,
                resultCount = strings.docFilterResults(resultCount),
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                error = error,
                dialog = dialog,
                topBarActions = { d.topBarActions(firstDownload) },
                onQueryChange = controller::setQuery,
                searchModeActive = searchModeActive,
                onOpenSearch = controller::openSearch,
                onCloseSearch = controller::closeSearch,
                onLanguageChange = controller::setLanguage,
                onTypeFilterChange = { d.persistTypeFilter(it); controller.setTypeFilter(it) },
                arrangement = arrangement,
                groupKeys = controller.groupKeys,
                repositories = repositories,
                rememberArrangement = rememberArrangement,
                arrangementIsDefault = arrangementIsDefault,
                onMoveSort = controller::moveSortCriterion,
                onToggleSortDirection = controller::toggleSortDirection,
                onGroupByChange = controller::setGroupBy,
                onRepositoryChange = controller::setRepositoryFilter,
                onRememberChange = controller::setRememberArrangement,
                onResetArrangement = controller::resetArrangement,
                onRowClick = { row ->
                    if (selectionMode) controller.toggle(row.docId) else controller.select(row.docId)
                },
                onRowLongClick = { row ->
                    controller.enterSelection()
                    controller.toggle(row.docId)
                },
                onDownload = { controller.select(it.docId) },
                onCancel = { row -> d.onCancelDownload(row.docId) },
                onSelectionAbout = controller::about,
                onSelectionDelete = controller::delete,
                onSelectionDeleteIndex = controller::deleteIndex,
                onSelectionUnlock = controller::unlock,
                unlockVisible = displayed.firstOrNull { it.docId in selectedIds }?.enciphered == true,
                deleteVisible = anySelectedDeletable(displayed, selectedIds),
                onDismissError = controller::dismissError,
                onConfirmDialog = controller::confirmDialog,
                onDismissDialog = controller::dismissDialog,
                onConfirmProceed = controller::confirmProceed,
                onDismissProceed = controller::dismissProceed,
                onNavigateUp = { navController.popOrExit(deps.exitHost) },
                onExitSelection = controller::clearSelection,
            )

            // FirstDownload onboarding OK gate, a sibling in the same Box exactly as classic
            // (:351-364): a bottom button, enabled once a Bible is installed. Classic returned
            // DownloadKeys.DOWNLOAD_FINISH here; every one of its consumers ignored the code, so
            // OK is now the ordinary exit this destination already has (plan D7).
            if (firstDownload) {
                Button(
                    onClick = { navController.popOrExit(deps.exitHost) },
                    enabled = bibleInstalled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(strings.okay)
                }
            }
        }
    }

    // ——— CLOUD DOCUMENTS ———
    composable(route = NavRoutes.CLOUD_DOCUMENTS_PATTERN) {
        val d = deps.cloudDocuments
        val controller = remember { d.controllerFor() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic openOrGate() in full (:193-208): the sign-in gate, the initial cache seed and the
        // conditional network refresh are ALL inside deps.openOrGate (plan D3 -- "compose, then
        // maybe leave"). Plain `remember`, deliberately NOT `rememberSaveable`: nothing in this graph
        // ever covers this entry's composition -- CloudDocuments has no children of its own, unlike
        // Download (covered by CustomRepositories) or the bookmark siblings -- so the only way this
        // composition is torn down and rebuilt is a genuinely fresh back-stack entry or a real
        // process/host rebuild, and the `controller` val above (from a PER-ENTRY controllerFor --
        // see CloudDocumentsDeps.controllerFor's kdoc) is rebuilt exactly then too. A rememberSaveable
        // flag would restore `true` after process death while that fresh controller came back empty,
        // silently skipping the gate and the seed -- see this task's report for the fuller account of
        // this carry-forward.
        var ranOpenOrGate by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (ranOpenOrGate) return@LaunchedEffect
            ranOpenOrGate = true
            if (!d.openOrGate()) navController.popOrExit(deps.exitHost)
        }

        // Classic bridge.register()/unregister() plus the running.drop(1) collector (:105, :180,
        // :111-116): deps.subscribeProgress owns the whole EventBus bridge lifetime and reports every
        // post-drop(1) transition; the arm's job is exactly the collector's old body.
        DisposableEffect(Unit) {
            val stop = d.subscribeProgress { running ->
                controller.setTransferRunning(running)
                if (!running) scope.launch { d.refreshFromNetwork() }
            }
            onDispose { stop() }
        }

        val grouped by controller.grouped.collectAsState()
        val statusFilter by controller.statusFilter.collectAsState()
        val categoryFilter by controller.categoryFilter.collectAsState()
        val query by controller.query.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val selectionMode by controller.selectionMode.collectAsState()
        val selectedIds by controller.selectedIds.collectAsState()
        val busy by controller.busy.collectAsState()
        val transferRunning by controller.transferRunning.collectAsState()
        val showRemoved by controller.showRemoved.collectAsState()
        val dialog by controller.dialog.collectAsState()
        val arrangement by controller.arrangement.collectAsState()
        val rememberArrangement by controller.rememberArrangement.collectAsState()
        val arrangementIsDefault by controller.arrangementIsDefault.collectAsState()

        // Classic's onBackPressed override (:183-189): the selection bar goes before the search bar
        // (AbSelectionScaffold's precedence) before an ordinary leave. ONE gated PlatformBackHandler
        // with the branch inside, the shape BookmarkNavGraph.kt:494 uses -- never two stacked
        // handlers (BookmarkNavGraph.kt:652-659 argues why). PlatformBackHandler, never
        // androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = selectionMode || searchModeActive) {
            if (selectionMode) controller.clearSelection() else controller.closeSearch()
        }

        CloudDocumentsScreen(
            title = d.title,
            loading = busy || transferRunning,
            isRefreshing = false,
            onRefresh = { scope.launch { d.refreshFromNetwork() } },
            grouped = grouped,
            statusFilters = cloudStatusFilters(showRemoved, d.statusFilterLabels(showRemoved)),
            selectedStatusFilter = statusFilter,
            categoryFilters = cloudCategoryFilters(d.categoryFilterLabels()),
            selectedCategoryFilter = categoryFilter,
            query = query,
            selectionMode = selectionMode,
            selectedIds = selectedIds,
            syncEnabled = controller.syncEnabled(),
            dialog = dialog,
            topBarActions = d.topBarActions,
            onQueryChange = controller::setQuery,
            searchModeActive = searchModeActive,
            onOpenSearch = controller::openSearch,
            onCloseSearch = controller::closeSearch,
            onStatusFilterChange = controller::setStatusFilter,
            onCategoryFilterChange = controller::setCategoryFilter,
            arrangement = arrangement,
            groupKeys = controller.groupKeys,
            rememberArrangement = rememberArrangement,
            arrangementIsDefault = arrangementIsDefault,
            onMoveSort = controller::moveSortCriterion,
            onToggleSortDirection = controller::toggleSortDirection,
            onGroupByChange = controller::setGroupBy,
            onRememberChange = controller::setRememberArrangement,
            onResetArrangement = controller::resetArrangement,
            showRemoved = showRemoved,
            onShowRemovedChange = controller::setShowRemoved,
            onRowClick = { if (selectionMode) controller.toggle(it.initials) },
            onRowLongClick = { controller.enterSelection(); controller.toggle(it.initials) },
            onRowAction = { item, action -> controller.performAction(item, action) },
            onBulkAction = { controller.performBulk(it) },
            onSyncNowConfirm = controller::confirmSyncNow,
            onSyncNowDismiss = controller::dismissSyncNow,
            onConfirmDialog = controller::confirmDialog,
            onDismissDialog = controller::dismissDialog,
            onNavigateUp = {
                if (selectionMode) controller.clearSelection() else navController.popOrExit(deps.exitHost)
            },
            onExitSelection = controller::clearSelection,
        )
    }
}

/**
 * Zips [labels] (classic `statusFilterLabels()`'s `getString` results, `Strings` having no
 * cloud-filter equivalent) onto the fixed [CloudDocFilter] order classic's own status spinner used
 * (`CloudDocumentsComposeActivity.kt:387-396`), POSITIONALLY: REMOVED is the list's eighth entry
 * only when [showRemoved] is true, exactly matching [labels]' own conditional length.
 */
private fun cloudStatusFilters(showRemoved: Boolean, labels: List<String>): List<Pair<CloudDocFilter, String>> {
    val order = buildList {
        add(CloudDocFilter.ALL)
        add(CloudDocFilter.INSTALLED)
        add(CloudDocFilter.CLOUD)
        add(CloudDocFilter.UPDATES)
        add(CloudDocFilter.BLOCKED)
        add(CloudDocFilter.DEVICE_ONLY)
        add(CloudDocFilter.CLOUD_ONLY)
        if (showRemoved) add(CloudDocFilter.REMOVED)
    }
    return order.zip(labels)
}

/**
 * Zips [labels] (classic `categoryFilterLabels()`'s `getString` results) onto the fixed
 * [DocCategory] order classic's own category spinner used (`CloudDocumentsComposeActivity.kt:398-406`),
 * POSITIONALLY -- the "all" row is `null`, never [DocCategory.OTHER].
 */
private fun cloudCategoryFilters(labels: List<String>): List<Pair<DocCategory?, String>> {
    val order: List<DocCategory?> = listOf(
        null,
        DocCategory.BIBLE,
        DocCategory.COMMENTARY,
        DocCategory.DICTIONARY,
        DocCategory.GENERAL_BOOK,
        DocCategory.MAPS,
        DocCategory.AND_BIBLE,
    )
    return order.zip(labels)
}

/**
 * The seven type-filter rows [DocumentSelectionScreen] shows, classic
 * `DownloadComposeActivity.typeFilterLabels` (`:918-926`). Pure [Strings] lookup with no platform
 * dependency, so it is arm code rather than a deps slot.
 */
private fun typeFilterLabels(strings: Strings): List<Pair<DocTypeFilter, String>> = listOf(
    DocTypeFilter.ALL to strings.docTypeAll,
    DocTypeFilter.BIBLE to strings.docTypeBible,
    DocTypeFilter.COMMENTARY to strings.docTypeCommentary,
    DocTypeFilter.DICTIONARY to strings.docTypeDictionary,
    DocTypeFilter.GENERAL_BOOK to strings.docTypeGeneralBook,
    DocTypeFilter.MAPS to strings.docTypeMaps,
    DocTypeFilter.ADDON to strings.docTypeAddon,
)
