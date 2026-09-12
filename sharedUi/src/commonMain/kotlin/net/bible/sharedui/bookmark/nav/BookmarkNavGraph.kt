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

package net.bible.sharedui.bookmark.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedcore.bookmark.LabelEditController
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.LabelEditResult as NavLabelEditResult
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop

// ——————————————————————————————————————————————————————————————————————————————————————————————
// A NOTE ON THE TWO `LabelEditResult`s
//
// [net.bible.sharedcore.nav.LabelEditResult] -- imported here as [NavLabelEditResult] -- is the NAV
// result (Saved / Cancelled) this graph delivers through [BookmarkNavDeps.labelEditResults].
// [net.bible.sharedcore.bookmark.LabelEditResult] is a DIFFERENT type: the controller's own outcome
// (Save / Delete / Cancel), which [LabelEditController] hands to the `onFinish` it was built with.
// Only the nav one is named in this file, and it is aliased rather than imported bare so that no
// reader of `deliver(navController, it)` has to work out which of the two `it` is. Mapping the
// controller's three onto the nav type's two is the HOST's job, inside
// [LabelEditDeps.controllerFor] -- see that field's kdoc.
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * [LabelEditScreen]'s platform-supplied slots, ported from classic `LabelEditComposeActivity`.
 *
 * - [controllerFor] builds the (per-back-stack-entry) [LabelEditController] around the route's own
 *   `data` payload -- same factory shape as [net.bible.sharedui.ai.nav.RawLlmLogDeps.controllerFor],
 *   and a factory rather than a host-held instance for that field's reason: navigation-compose
 *   disposes this destination's composition whenever another destination sits on top of it, and a
 *   controller shared across entries would edit the wrong label.
 *
 *   It also carries the OUTCOME MAPPING. The controller reports three outcomes and the nav result
 *   has two ([NavLabelEditResult]); classic's `onFinish` (`LabelEditComposeActivity.kt:234-247`)
 *   already collapsed them the same way -- `Save` and `Delete` both end at `finishWithData`, which
 *   builds the identical `Intent().putExtra("data", updated.toJSON())`, and `Delete` differs only in
 *   setting `delete`/`deleteOrphanedBookmarks` on the payload FIRST. The collapse stays host-side
 *   because it runs through `LabelEditMapper.applyToData` and `LabelEditContract.LabelData`, two
 *   `:app` types that embed Room entities and cannot cross into `commonMain` (see `NavResults.kt`'s
 *   closing note). What this graph receives is therefore already a [NavLabelEditResult], and its
 *   only job is to hand it to the channel.
 *
 * - [title] is the WINDOW title. Classic carried it as `android:label="@string/edit_label"` on the
 *   host Activity; one host now serves every cluster, so it is set per destination instead. It is
 *   the same string [LabelEditScreen] draws in its own top bar (`strings.editLabelTitle`), which is
 *   why there is no second, on-screen title slot here.
 *
 * - [iconKeys], [iconSlot] and [actions] are the three slots [LabelEditScreen]'s FROZEN signature
 *   requires and `commonMain` cannot fill: every one of them ends in `painterResource(R.drawable.*)`
 *   (`LabelEditComposeActivity.kt:285-294` and its `LabelEditActions` block). They move across
 *   verbatim.
 *
 *   [actions] is the interesting one, because the top bar's buttons act on the controller this GRAPH
 *   owns, not on anything the host holds: the arm passes the controller's own `save`/`requestDelete`
 *   in, and passes [state] so the "is this a special label?" branch (which hides Delete) and the
 *   export action can read it. [data] is passed for the export action alone -- classic's
 *   `shareLabel` (`:227-232`) applies the CURRENT, still-unsaved state onto the label payload before
 *   handing it to `exportStudyPads`, so it needs both halves. Export deliberately has no deps slot
 *   of its own: it runs through the host's `destinationRequest` chooser sheet, which stays host-side
 *   with the other platform dialogs.
 *
 * - [deletePromptSlot] renders [LabelEditController.deletePrompt]'s two confirmations, classic's
 *   `DeletePromptDialog` (`:190-224`). NOT in the task brief's deps list, and it has to be: without
 *   it `requestDelete()` would set a prompt nothing draws, and the delete button would silently do
 *   nothing. Host-side because both dialogs are built from formatted Android string resources
 *   (`delete_label_confirmation`, `confirm_delete_orphaned_bookmarks`, ...) that have no
 *   `LocalStrings` entries; giving them entries is a `Strings.kt`/`AndroidStrings.kt` change of its
 *   own, not part of a navigation move. The label name is passed in from the LIVE controller state
 *   for classic's stated reason: a name typed but not yet saved must not show stale in the
 *   confirmation.
 *
 * - [confirmDiscard] is classic's `requestUp` discard-changes `android.app.AlertDialog`
 *   (`:256-271`). It stays a host lambda: converting platform dialogs to Compose is a separate,
 *   queued port goal, and doing it here would change behaviour under cover of a navigation change.
 *   It takes the "yes, discard" continuation, so the arm keeps the decision about what discarding
 *   MEANS (`controller.cancel()`, i.e. the same exit the up-arrow takes when nothing is dirty).
 */
class LabelEditDeps(
    val controllerFor: (data: String, onResult: (NavLabelEditResult) -> Unit) -> LabelEditController,
    val title: String,
    val iconKeys: List<String?>,
    val iconSlot: @Composable (String?, Color) -> Unit,
    val actions: @Composable RowScope.(
        data: String,
        state: LabelEditState,
        onSave: () -> Unit,
        onDelete: () -> Unit,
    ) -> Unit,
    val deletePromptSlot: @Composable (
        prompt: DeletePrompt,
        labelName: String,
        onConfirm: (deleteOrphaned: Boolean) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    val confirmDiscard: (onConfirm: () -> Unit) -> Unit,
)

/**
 * [BookmarksScreen]'s platform-supplied slots, ported from classic `BookmarksComposeActivity`.
 *
 * This is the cluster's ROOT destination: nothing inside any graph navigates to it (it is reached
 * only as the host's start destination, through `ScreenLauncher.MIGRATED`), and it is the only one
 * of the three that navigates OUT to a sibling -- twice, both times to [NavRoutes.MANAGE_LABELS_PATTERN].
 *
 * - [controllerFor] builds the [BookmarksController] around the route's own `labelNo`, already
 *   clamped by the arm. It is a HOST-MEMOISED factory, not a per-entry one, for exactly
 *   [ManageLabelsDeps.controllerFor]'s reason: this destination NAVIGATES to the label manager,
 *   navigation-compose disposes this arm's composition while that child is on top, and the
 *   controller holds view state classic's merely-PAUSED Activity kept -- the multi-selection the
 *   user is about to assign labels to, the search query, the expanded rows, the loaded list. A
 *   per-entry factory would clear the user's selection at the exact moment they used it.
 *
 *   Its two lambdas are the halves only the graph can supply. `onSelectBookmark` is the channel
 *   delivery -- the host builds the [BookmarkResult] (it needs the Room entity behind the row) and
 *   the arm decides how it LEAVES. `navigateToManageLabels` is the single
 *   `navController.navigate(NavRoutes.manageLabels(payload))` the host cannot make; the payload is
 *   the host's, because both of classic's round trips build a `ManageLabelsContract.ManageLabelsData`
 *   out of Room labels and the workspace settings, and that is also asynchronous work
 *   (`Dispatchers.IO`) -- hence a callback rather than a return value.
 *
 *   Everything else classic's controller was built with stays inside this factory, host-side and
 *   invisible here: the CSV export/import (`bookmarkControl.exportBookmarksToCSV` wants an
 *   `Activity`), the delete confirmation (an `android.app.AlertDialog`; converting platform dialogs
 *   is a separate, queued port goal) and the `bookmarks-last-used` setting classic wrote in
 *   `onCreate`. Each of them ends by refreshing the controller it was built around, which only the
 *   factory that built it can reach -- splitting them into deps slots of their own would buy the arm
 *   nothing but a self-reference.
 *
 * - [title] is the WINDOW title, classic's `android:label` and the same string
 *   [BookmarksScreen] draws in its own top bar (`R.string.bookmarks_and_mynotes_title`), exactly as
 *   classic passed the one string to both.
 *
 * - [onManageLabelsResult] is where BOTH round trips land: classic's `assignLabels` continuation
 *   (`BookmarksComposeActivity.kt:215-222`) and its `manageLabels` one (`:266-270`). They share one
 *   channel, and the host tells them apart from the request it recorded when it built the payload --
 *   not the arm, which has nothing to tell them apart WITH (a `ManageLabelsResult` carries only the
 *   returned JSON). Both bodies are Room work (`changeLabelsForBookmark`, `workspaceSettings
 *   .updateFrom`) plus a controller refresh, so the whole of both stays host-side.
 *
 *   There is deliberately no cancelled branch: [NavResultChannel] only ever carries the payload
 *   classic's `RESULT_OK` carried, and the label manager has no cancel path at all (its Back press
 *   SAVES). A user who backs out of it is delivering a result, and classic's `if (resultCode ==
 *   RESULT_OK)` guard was therefore always true on this edge.
 *
 * - [subscribeSyncEvents] is classic's `ABEventBus.register(this) { onMain<BookmarksUpdatedViaSyncEvent>
 *   { controller.refresh() } }` / `unregister` pair (`:99`, `:149`), as a `DisposableEffect` in this
 *   arm -- the seam shape [net.bible.sharedui.readingplan.nav.DailyReadingDeps.subscribeEvents]
 *   established, and route-scoped for its reason: the token is per-subscription, so an unsubscribe
 *   can never take another cluster's listeners down with it.
 */
class BookmarksDeps(
    val controllerFor: (
        initialFilterIndex: Int,
        onSelectBookmark: (BookmarkResult) -> Unit,
        navigateToManageLabels: (payload: String) -> Unit,
    ) -> BookmarksController,
    val title: String,
    val onManageLabelsResult: (ManageLabelsResult) -> Unit,
    val subscribeSyncEvents: (onBookmarksChanged: () -> Unit) -> () -> Unit,
)

/**
 * [ManageLabelsScreen]'s platform-supplied slots, ported from classic `ManageLabelsComposeActivity`.
 *
 * This destination is the cluster's DUAL-ENTRY one: six callers outside the graph launch it and read
 * its result from an `Intent`, and `Bookmarks` will navigate to it from inside. Neither the deps nor
 * the arm branch on which happened -- [NavResultChannel.deliver] decides that at runtime -- so
 * everything below is written once and serves both.
 *
 * - [controllerFor] builds the [ManageLabelsController] around the route's own `data` payload. It is
 *   the ONE controller in these graphs the host must MEMOISE rather than build afresh per call, and
 *   the reason is this destination's shape rather than a preference: it NAVIGATES to a child
 *   ([NavRoutes.LABEL_EDIT_PATTERN]), navigation-compose disposes this arm's composition while that
 *   child is on top, and the controller holds the user's whole unsaved working set -- ticked labels,
 *   favourites, pending deletes, the auto-assign primary. A per-entry factory (the shape
 *   [LabelEditDeps.controllerFor] and [net.bible.sharedui.ai.nav.RawLlmLogDeps.controllerFor] use,
 *   for their own good reasons) would throw all of that away every time the user opened the editor,
 *   which classic -- whose Activity was merely PAUSED behind the editor -- never did. The host keys
 *   the memo on `data` and drops it when the result is delivered, so a later entry with a different
 *   payload, or a re-entry after an exit, still gets a fresh, freshly-seeded instance.
 *
 *   Its two lambdas are the halves only the graph can supply. `onEditLabel` receives a FINISHED
 *   `LabelEditContract.LabelData` JSON payload -- the host builds it, because it needs `labelsById`,
 *   the workspace DAO and `BookmarkEntities.Label`, none of which cross into `commonMain` -- and the
 *   arm's job is the single `navController.navigate(NavRoutes.labelEdit(payload))` the host cannot
 *   make. `onResult` is the channel delivery; classic's `saveAndExit`/`reset`/`studyPadSelected` all
 *   end in it, and all three of those stay host-side for the same Room reasons.
 *
 * - [titleFor] is the WINDOW title, and it is a lambda rather than a `String` because classic read it
 *   as `getString(data.titleId)` (`ManageLabelsComposeActivity.kt:170`). `titleId` is not a stored
 *   field on the payload: it is a computed getter on `ManageLabelsContract.ManageLabelsData`
 *   (`ManageLabelsContract.kt:90`) that maps the payload's `mode` onto one of four Android string
 *   RESOURCE IDS -- and neither the `:app` mode enum nor an Android resource id can be resolved from
 *   `commonMain`, which is why the whole two-step is a host lambda over the raw payload string. It is
 *   also the title [ManageLabelsScreen] draws in its own top bar, exactly as classic passed the same
 *   string to both.
 *
 * - [onLabelEditResult] is where the child editor's result lands: classic's `editLabel` continuation
 *   (`:499-551`), which reconciles the edit into `labelsById`, the controller and the workspace
 *   override table. It takes the whole [NavLabelEditResult] -- including `Cancelled`, which classic
 *   handled as an early return on `RESULT_CANCELED` -- so the ported body keeps its shape rather than
 *   splitting the cancel branch up into the arm. The host does not need the controller passed back: it
 *   holds the memoised session that owns it.
 *
 * - [initialSearchMode] is the READ half of classic's `labels_list_search_mode` setting, seeded
 *   before `setContent` (`:153-156`); it is an int (a [SearchMode] ordinal) because
 *   `CommonUtils.settings` is a host type, and the arm applies the STUDYPAD gate classic applied.
 *   There is deliberately no `persistSearchMode` twin: the WRITE half stays exactly where classic
 *   put it, inside the host's `saveAndExit` (`:576-578`), which every exit this screen has --
 *   up-arrow, Back and the StudyPad selection alike -- already routes through. An arm-side write
 *   would be a second writer of the same setting buying nothing.
 *
 * - [iconSlot], [actions] and [searchActions] are the three slots [ManageLabelsScreen]'s FROZEN
 *   signature requires and `commonMain` cannot fill: every one of them ends in
 *   `painterResource(R.drawable.*)`. [iconSlot] takes `studyPadMode` because classic's
 *   `ManageLabelIcon` picks its default drawable from it (`:692-693`) and the arm, not the host,
 *   knows the live mode. [actions] and [searchActions] take the whole [ManageLabelsController]
 *   rather than a fistful of callbacks the way [LabelEditDeps.actions] does -- classic's
 *   `ManageLabelsActions` reads `mode`, `styleTagsVisible` and five commands off it, and threading
 *   nine parameters through a top-bar slot would hide rather than reveal what the bar does. The
 *   controller is a `:sharedCore` type, so this costs the deps class nothing in platform coupling.
 *   The export sheet, the StudyPad-import `InstallZip` round trip and the help dialog those actions
 *   raise all stay HOST-side: each ends in a platform call (`awaitIntent`, an `AlertDialog`, a
 *   formatted `getString`), and converting them is a separate, queued port goal.
 */
class ManageLabelsDeps(
    val controllerFor: (
        data: String,
        onEditLabel: (labelEditPayload: String) -> Unit,
        onResult: (ManageLabelsResult) -> Unit,
    ) -> ManageLabelsController,
    val titleFor: (data: String) -> String,
    val onLabelEditResult: (result: NavLabelEditResult) -> Unit,
    val initialSearchMode: () -> Int,
    val iconSlot: @Composable (customIcon: String?, tint: Color, studyPadMode: Boolean) -> Unit,
    val actions: @Composable RowScope.(controller: ManageLabelsController) -> Unit,
    val searchActions: @Composable RowScope.(controller: ManageLabelsController) -> Unit,
)

/**
 * Platform-supplied slots the bookmark cluster's destinations need but `commonMain` cannot provide.
 * Same shape as [net.bible.sharedui.settings.nav.SettingsNavDeps] and
 * [net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps]: [exitHost] and [setWindowTitle] are
 * graph-wide and sit at the top, one nested holder per destination below.
 *
 * **All three result channels were declared here at once, before their destinations existed.** The
 * cluster has three result-producing destinations and this is the file that owns them; creating the
 * channels one per task would have meant every later task WIDENING this type, and a widened deps
 * class is the kind of change that quietly leaves a caller behind. The three nested deps holders
 * were a different matter and arrived with the destinations themselves -- an unused
 * `NavResultChannel` field costs one line, an unused deps class costs a whole unverified port. As of
 * slice 2, Task 6 all three destinations are built.
 */
class BookmarkNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) -- not the on-screen top-bar title a screen
     * draws for itself. One host serves every cluster, so the manifest's static `android:label`
     * cannot be right for all of them. Called from each destination's `LaunchedEffect(title)`,
     * keyed on the VALUE so a state-derived title cannot go stale, and never from a screen
     * composable: screen signatures are frozen for this migration.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * How the bookmark LIST hands back the row the user picked. Only ONE of
     * [NavResultChannel]'s two branches can ever run for it: `BOOKMARKS_PATTERN` is a root
     * destination -- nothing in any graph navigates to it -- so there is never a parent entry and
     * `deliver` always exits the host. It is a channel rather than a plain exit lambda anyway,
     * because that decision belongs to [NavResultChannel] rather than to a destination's own
     * knowledge of who opened it, and the bookmark list would otherwise be the only result producer
     * in these graphs that had to be told.
     */
    val bookmarkResults: NavResultChannel<BookmarkResult>,
    /**
     * How the label MANAGER hands back its edited `ManageLabelsData`, in either of the two ways it
     * can be entered -- see [NavResultChannel]'s own kdoc. BOTH entries are live as of slice 2,
     * Task 6: six classic callers still reach it as the host's START destination (exit branch), and
     * the `BOOKMARKS_PATTERN` arm navigates to it from inside (publish-and-pop branch, consumed
     * there). The arm needed no change for the second one, because the branch is
     * [NavResultChannel.deliver]'s to take at runtime.
     */
    val manageLabelsResults: NavResultChannel<ManageLabelsResult>,
    /**
     * How the label EDITOR hands back its result, in either of the two ways it can be entered -- see
     * [NavResultChannel]'s own kdoc.
     *
     * Both branches are real for this destination, which is why it gets a channel rather than a
     * parent-supplied lambda: the label manager navigates to it from INSIDE this graph (publish and
     * pop), and `ScreenLauncher`'s six-plus classic callers reach it as the host's START destination
     * (exit the host with the result). **The `MANAGE_LABELS_PATTERN` arm is what collects
     * [NavResultChannel.pending] here** -- the first and so far only parent in any of these graphs.
     * Without that collection an edit made from inside the graph would pop with the user's changes
     * silently dropped, and no test outside `NavResultChannelGuardTest` would notice, because every
     * other path into the editor still takes the exit branch.
     */
    val labelEditResults: NavResultChannel<NavLabelEditResult>,
    // — BOOKMARKS —
    val bookmarks: BookmarksDeps,
    // — MANAGE LABELS —
    val manageLabels: ManageLabelsDeps,
    // — LABEL EDIT —
    val labelEdit: LabelEditDeps,
)

/**
 * Pop to the parent destination, or -- when there is none, i.e. this destination is the host's start
 * destination -- leave the host. The same one-line extension the other three cluster graphs carry;
 * the boolean branch itself is [popOrExitOnFailedPop], shared so that "a failed pop still leaves"
 * is decided in one place.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The bookmark cluster's destinations. Registered into the app's single `NavHost` by the host
 * Activity.
 *
 * It holds three: [NavRoutes.BOOKMARKS_PATTERN], the bookmark list, and the two it navigates to --
 * [NavRoutes.MANAGE_LABELS_PATTERN], the label manager, and [NavRoutes.LABEL_EDIT_PATTERN], the
 * label editor the manager in turn navigates to.
 *
 * Only the FIRST is reachable through `ScreenLauncher.MIGRATED`, and that asymmetry is correct
 * rather than an oversight: the other two take a REQUIRED `data` argument, so an argument-free
 * route would open a screen with nothing to show (the `Screen.RawLlmLog` precedent), whereas the
 * list's `labelNo` is OPTIONAL and its absence means something real ("no label filter"), the
 * `Screen.ReadingPlan` precedent. Every real edge into the other two builds
 * `NavRoutes.manageLabels(data)` / `NavRoutes.labelEdit(data)` directly.
 *
 * The in-graph edges are live as of slice 2, Task 6: `Bookmarks` navigates to the manager instead of
 * launching an Intent, and the manager navigates to the editor. The OUTSIDE edges into the manager
 * and the editor still go to the classic Activities through the coexistence seam, and stay there
 * until the task that deletes those hosts.
 *
 * **Almost every exit in the two LABEL destinations carries a RESULT.** Their up-arrow, Back, Save,
 * Delete and StudyPad selection all go through a [NavResultChannel], and [NavResultChannel.deliver]
 * is what chooses between popping to a parent and exiting the host; binding any of them to a plain
 * `popOrExit` would leave without a result at all, which classic never did. The exception there is a
 * destination reached with NO payload, which cannot construct a result to leave with -- see the
 * `MANAGE_LABELS_PATTERN` arm's guard.
 *
 * The bookmark LIST is the opposite shape and uses [popOrExit] for both of its own exits: its result
 * exists only when a row was PICKED, so leaving by the up-arrow or by Back is classic's plain
 * `finish()` with no result at all (`RESULT_CANCELED`), which is not something a
 * [net.bible.sharedcore.nav.BookmarkResult] can express.
 */
fun NavGraphBuilder.bookmarkNavGraph(navController: NavHostController, deps: BookmarkNavDeps) {
    // ——— BOOKMARKS ———
    composable(
        route = NavRoutes.BOOKMARKS_PATTERN,
        arguments = listOf(
            // A String, not NavType.IntType, for DAILY_READING_PATTERN's and READING_PROGRESS_PATTERN's
            // reason: an ABSENT optional Int has no representation this library can express (IntType is
            // not nullable and any sentinel default is a value the route could legitimately carry), and
            // absence is exactly what has to stay distinguishable here -- see NavRoutes.bookmarks.
            navArgument(NavRoutes.ARG_LABEL_NO) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.bookmarks

        // Classic `BookmarksComposeActivity.initialFilterIndex` (`:73-77`), both halves of it: an
        // ABSENT argument means "no label filter" and reads as 0, and a NEGATIVE one is CLAMPED to 0
        // rather than passed on. The clamp lives here, not in `NavRoutes.bookmarks`, and deliberately
        // -- see that builder's kdoc: it is destination BEHAVIOUR, not route DATA. Losing it would
        // hand BookmarksController a negative index, which its own `coerceIn(0, ...)` would absorb
        // silently today, so nothing downstream would report the loss.
        val labelNo = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_LABEL_NO) }?.toIntOrNull() ?: -1
        val initialFilterIndex = if (labelNo >= 0) labelNo else 0

        // remember(initialFilterIndex) over a HOST-MEMOISED factory -- see BookmarksDeps.controllerFor.
        // The remember key and the host's memo key are the same value on purpose: a route with a
        // different labelNo is a different list, and anything else is the same one being re-composed
        // after the label manager was closed.
        val controller = remember(initialFilterIndex) {
            d.controllerFor(
                initialFilterIndex,
                { result -> deps.bookmarkResults.deliver(navController, result) },
                // The one line the host cannot write: the payload is entirely the host's (Room
                // labels, the workspace settings), turning it into a destination is this graph's.
                { payload -> navController.navigate(NavRoutes.manageLabels(payload)) },
            )
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's ABEventBus.register/unregister pair (`:99`, `:149`), route-scoped -- see
        // BookmarksDeps.subscribeSyncEvents. Keyed on the controller so a rebuilt controller is the
        // one a sync refreshes.
        DisposableEffect(controller) {
            val unsubscribe = d.subscribeSyncEvents { controller.refresh() }
            onDispose { unsubscribe() }
        }

        // ——— the channel's IN-GRAPH branch, consumed ———
        // This is where ManageLabels' in-graph ENTRY mode first runs in production: before this arm
        // existed, every entry into the label manager was the host's start destination, so
        // `deliver` always took the exit branch. Both of classic's round trips -- assign-labels
        // (`BookmarksComposeActivity.kt:206`) and manage-labels (`:258`) -- come back through this
        // one effect; the host tells them apart from the request it recorded when it built the
        // payload, because a ManageLabelsResult carries only the returned JSON.
        //
        // Same shape as the MANAGE_LABELS_PATTERN arm's consumption of `labelEditResults`, and for
        // the same reasons: `consume()` clears the channel in the same breath as reading it, so a
        // recomposition cannot apply the same result twice -- and clearing re-triggers this effect
        // with `null`, which is the early return.
        val pendingManageLabels by deps.manageLabelsResults.pending.collectAsState()
        LaunchedEffect(pendingManageLabels) {
            if (pendingManageLabels == null) return@LaunchedEffect
            val result = deps.manageLabelsResults.consume() ?: return@LaunchedEffect
            d.onManageLabelsResult(result)
        }

        val rows by controller.rows.collectAsState()
        val filterLabels by controller.filterLabels.collectAsState()
        val selectedFilterIndex by controller.selectedFilterIndex.collectAsState()
        val sortMode by controller.sortMode.collectAsState()
        val searchText by controller.searchText.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val showNotes by controller.showNotes.collectAsState()
        val selection by controller.selection.collectAsState()
        val expandedIds by controller.expandedIds.collectAsState()
        val loading by controller.loading.collectAsState()

        // Classic's `onBackPressed` override (`:153-161`), line for line: back dismisses what is
        // visually on top -- the selection bar covers the search bar (AbSelectionScaffold's
        // precedence), so selection goes first; closing search underneath a visible selection bar
        // would clear the query and re-filter the list invisibly.
        //
        // GATED, unlike the two label arms' always-enabled handlers, and the difference is classic's:
        // their third branch is a SAVE that must not be replaced by a bare pop, while this one is
        // `super.onBackPressed()` -- a plain leave with no result, which is exactly what the NavHost
        // does on its own. SettingsNavGraph's search handler is gated for the same reason.
        // PlatformBackHandler, never androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = selection.isNotEmpty() || searchModeActive) {
            if (selection.isNotEmpty()) controller.clearSelection() else controller.closeSearch()
        }

        BookmarksScreen(
            title = d.title,
            rows = rows,
            filterLabels = filterLabels,
            selectedFilterIndex = selectedFilterIndex,
            sortMode = sortMode,
            searchText = searchText,
            showNotes = showNotes,
            selection = selection,
            expandedIds = expandedIds,
            loading = loading,
            onSelectFilter = controller::setFilter,
            onCycleSort = controller::cycleSort,
            onSearch = controller::setSearch,
            searchModeActive = searchModeActive,
            onOpenSearch = controller::openSearch,
            onCloseSearch = controller::closeSearch,
            onToggleShowNotes = controller::toggleShowNotes,
            onRowClick = controller::selectRow,
            onRowLongClick = controller::enterSelection,
            onToggleSelected = controller::toggleSelection,
            onToggleExpand = controller::toggleExpanded,
            onAssignSelected = controller::assignSelected,
            onDeleteSelected = controller::deleteSelected,
            onClearSelection = controller::clearSelection,
            onManageLabels = controller::manageLabels,
            onExportCsv = controller::exportCsv,
            onImportCsv = controller::importCsv,
            // Classic's `onUp = { finish() }`: a plain leave with NO result. This destination is the
            // host's start destination in every live edge, so popOrExit's boolean lands on the exit
            // branch -- but it is written as popOrExit rather than as `deps.exitHost()` so that a
            // future in-graph caller pops back to itself instead of killing the host under it.
            onUp = { navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— MANAGE LABELS ———
    composable(
        route = NavRoutes.MANAGE_LABELS_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_MANAGE_LABELS_DATA) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.manageLabels

        // Read plainly -- NO NavRoutes.decodeArg, for the reason the LABEL EDIT arm below states at
        // length: the navigation library has ALREADY percent-decoded this, and this payload is dense
        // JSON, so a second pass would corrupt any label name containing a literal `%`.
        val data = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_MANAGE_LABELS_DATA) }

        // The argument is REQUIRED in every sense that matters (see the graph kdoc), but is DECLARED
        // nullable-with-default like every other query argument in these graphs. This is the runtime
        // backstop. Unlike the editor's equivalent guard it leaves WITHOUT a result, and it has to:
        // `ManageLabelsResult` carries the edited payload and nothing else -- there is no cancelled
        // variant and classic had no cancel path at all -- so with no payload to start from there is
        // no result to construct. Leaving quietly is strictly better than delivering a made-up one
        // to a caller that would then write it back over the user's settings.
        if (data == null) {
            LaunchedEffect(Unit) { navController.popOrExit(deps.exitHost) }
            return@composable
        }

        // Keyed on `data` so a different payload is a different controller; the HOST memoises on the
        // same key, which is what makes this survive the LabelEdit round trip -- see
        // ManageLabelsDeps.controllerFor, where the whole argument lives.
        val controller = remember(data) {
            d.controllerFor(
                data,
                // The one line the host cannot write. Everything that goes INTO the payload is the
                // host's (Room entities, the workspace override table); turning it into a
                // destination is this graph's.
                { payload -> navController.navigate(NavRoutes.labelEdit(payload)) },
                { result -> deps.manageLabelsResults.deliver(navController, result) },
            )
        }

        // remember(data), not a bare call: titleFor parses the payload to reach its `titleId`, and
        // this arm recomposes on every keystroke in the search field.
        val title = remember(data) { d.titleFor(data) }
        LaunchedEffect(title) { deps.setWindowTitle(title) }

        val rows by controller.rows.collectAsState()
        val searchText by controller.searchText.collectAsState()
        val searchMode by controller.searchMode.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val styleTagsVisible by controller.styleTagsVisible.collectAsState()
        val filters by controller.filters.collectAsState()

        // Classic gated the search-mode setting AND the row-click behaviour on
        // `data.mode == ManageLabelsContract.Mode.STUDYPAD`, an `:app` enum. The controller's own
        // `mode` is the `:sharedCore` twin of it (`ManageLabelsMapper.toMode` maps the four values
        // one for one), so the arm can read the same gate without the payload's type crossing over.
        val isStudyPad = controller.mode == ManageLabelsMode.STUDYPAD

        // The SEED half of classic's `labels_list_search_mode` round trip
        // (`ManageLabelsComposeActivity.kt:153-156`). The WRITE half stays where classic put it, in
        // the host's `saveAndExit` (`:576-578`) -- every exit this screen has, the StudyPad
        // selection included, routes through `controller.save()`, so there is no path for an
        // exit-time write to miss and no reason for the arm to write at all.
        //
        // rememberSaveable, NOT remember: this arm's composition is DISPOSED while the label editor
        // sits on top of it (the reason SettingsNavGraph's search filter is saveable too), so a
        // plain remember would re-read the setting and re-seed the controller on every return from
        // the editor -- overwriting a mode the user changed since. `null` means "not seeded yet".
        var persistedSearchMode by rememberSaveable { mutableStateOf<Int?>(null) }
        LaunchedEffect(controller) {
            if (!isStudyPad) return@LaunchedEffect
            val seed = persistedSearchMode ?: d.initialSearchMode().also { persistedSearchMode = it }
            val mode = SearchMode.entries.getOrElse(seed) { SearchMode.NAME_START }
            // Guarded because setSearchMode re-dispatches the search; classic could seed
            // unconditionally only because it ran once, before the first collection.
            if (controller.searchMode.value != mode) controller.setSearchMode(mode)
        }

        // ——— the channel's IN-GRAPH branch, consumed ———
        // This is the only place in any of these graphs that reads a NavResultChannel's `pending`,
        // and it is the half of NavResultChannel's contract that had never run before this arm
        // existed: the editor is reached from HERE with a parent entry on the stack, so
        // `deliver` publishes and pops instead of exiting the host. `consume()` clears the channel
        // in the same breath as reading it, so a recomposition cannot apply the same edit twice --
        // and clearing re-triggers this effect with `null`, which is the early return above.
        // Cancelled results come through here too; classic's continuation began with exactly that
        // early return on RESULT_CANCELED, so the whole shape stays in the ported host body.
        val pendingLabelEdit by deps.labelEditResults.pending.collectAsState()
        LaunchedEffect(pendingLabelEdit) {
            if (pendingLabelEdit == null) return@LaunchedEffect
            val result = deps.labelEditResults.consume() ?: return@LaunchedEffect
            d.onLabelEditResult(result)
        }

        // Classic's `onBackPressed` override (`:319-326`), line for line: back closes the search bar
        // if it is open, and otherwise SAVES -- this screen has no cancel, so reading the two the
        // wrong way round would silently discard the user's label edits.
        //
        // ONE handler with the branch inside it, not two gated handlers. Two would work only because
        // back is dispatched to the most recently registered ENABLED callback, i.e. correct by
        // declaration ORDER -- an invisible dependency guarding a bug (back saving and exiting out
        // of search mode) that no test here could catch. SettingsNavGraph has one handler too; its
        // `enabled = searchModeActive` gating is right THERE because its non-search back is the
        // NavHost's own pop, which needs no handler at all. Here it is a save, so the handler must
        // always be enabled. PlatformBackHandler, never androidx.activity.compose.BackHandler: this
        // is commonMain.
        PlatformBackHandler(enabled = true) {
            if (searchModeActive) controller.closeSearch() else controller.save()
        }

        ManageLabelsScreen(
            title = title,
            rows = rows,
            mode = controller.mode,
            styleTagsVisible = styleTagsVisible,
            searchText = searchText,
            searchMode = searchMode,
            onSearch = controller::setSearch,
            onSetSearchMode = controller::setSearchMode,
            filters = filters,
            onToggleFilter = controller::toggleFilter,
            searchModeActive = searchModeActive,
            onCloseSearch = controller::closeSearch,
            onRowClick = { id ->
                if (isStudyPad) {
                    // A content-search hit carries its own firstMatchEntryId; a plain name-filtered
                    // Item row has none (navigates to the StudyPad start). Verbatim from classic.
                    val entryId = (
                        rows.find { it is ManageLabelsRow.SearchResult && it.labelId == id }
                            as? ManageLabelsRow.SearchResult
                        )?.firstMatchEntryId
                    controller.selectStudyPad(id, entryId)
                } else {
                    controller.editLabel(id)
                }
            },
            onRowLongClick = { id -> controller.editLabel(id) },
            onToggleChecked = controller::toggleChecked,
            onToggleFavourite = controller::toggleFavourite,
            onSetPrimary = controller::setPrimary,
            onToggleAutoAssign = controller::toggleAutoAssign,
            // The up-arrow SAVES, exactly as classic's `onUp = { saveAndExit() }` did. It routes
            // through the controller rather than a deps lambda of its own so that the host's
            // saveAndExit keeps exactly one caller-visible entry point (`onSave`), the same one the
            // controller already owned.
            onUp = { controller.save() },
            iconSlot = { customIcon, tint -> d.iconSlot(customIcon, tint, isStudyPad) },
            actions = { d.actions(this, controller) },
            // The New (+) icon lives in the search bar too, not just in `actions`: a search that
            // finds nothing has no other reachable "create it with this name" action once the normal
            // bar is replaced. Classic's comment at `:210-215`.
            searchActions = { d.searchActions(this, controller) },
        )
    }

    // ——— LABEL EDIT ———
    composable(
        route = NavRoutes.LABEL_EDIT_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_LABEL_DATA) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.labelEdit

        // Read plainly -- NO NavRoutes.decodeArg. The navigation library has ALREADY percent-decoded
        // this by the time it reaches `arguments`, and a second pass would corrupt any payload whose
        // JSON contains a literal `%` (see SearchNavGraph's searchArgs() kdoc, which documents the
        // double-decode trap at length). `NavRoutes.labelEdit(data)` encodes exactly once.
        val data = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_LABEL_DATA) }

        // The argument is REQUIRED in every sense that matters -- `NavRoutes.labelEdit` takes it
        // non-null and `Screen.LabelEdit` is kept out of MIGRATED so no argument-free route can be
        // launched -- but it is DECLARED nullable-with-default, matching RAW_LLM_LOG_PATTERN and
        // every other query argument in these graphs. This is the runtime backstop for that gap: an
        // editor with no label to edit leaves the way the user's Back would, rather than building a
        // controller around a payload that is not there.
        if (data == null) {
            LaunchedEffect(Unit) {
                deps.labelEditResults.deliver(navController, NavLabelEditResult.Cancelled)
            }
            return@composable
        }

        // One controller per back-stack entry AND per payload -- see LabelEditDeps.controllerFor.
        // Its `onResult` is already a NavLabelEditResult (the host mapped the controller's three
        // outcomes onto the nav type's two), so the only thing left to decide is HOW it leaves, and
        // that is precisely what NavResultChannel.deliver decides.
        val controller = remember(data) {
            d.controllerFor(data) { result -> deps.labelEditResults.deliver(navController, result) }
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's `requestUp()` (`LabelEditComposeActivity.kt:256-266`), unchanged in behaviour:
        // a dirty editor asks before throwing the edits away, a clean one just leaves. Both branches
        // end at `controller.cancel()` rather than at a direct `deliver(Cancelled)` -- so the
        // controller stays the single producer of outcomes and the Save/Delete/Cancel mapping keeps
        // living in exactly one place (the host's controllerFor). The dialog itself is host-side;
        // see LabelEditDeps.confirmDiscard.
        fun requestUp() {
            if (controller.isDirty()) d.confirmDiscard { controller.cancel() } else controller.cancel()
        }

        // Classic's `onBackPressed` override (`:268-271`), which routed EVERY back press through
        // requestUp -- hence `enabled = true` rather than a dirty-only handler: a clean editor must
        // still leave with RESULT_CANCELED, which a disabled handler would turn into a bare pop.
        // PlatformBackHandler, never androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = true) { requestUp() }

        val state by controller.state.collectAsState()
        val deletePrompt by controller.deletePrompt.collectAsState()

        LabelEditScreen(
            state = state,
            onName = controller::setName,
            onColor = controller::setColor,
            onCustomIcon = controller::setCustomIcon,
            onSelectionStyle = controller::setSelectionStyle,
            onWholeVerseStyle = controller::setWholeVerseStyle,
            onToggleFavourite = controller::toggleFavourite,
            onToggleSelected = controller::toggleThisBookmarkSelected,
            onTogglePrimary = controller::toggleThisBookmarkPrimary,
            onToggleAutoAssign = controller::toggleAutoAssign,
            onToggleAutoAssignPrimary = controller::toggleAutoAssignPrimary,
            onOverrideMode = controller::setOverrideMode,
            onUp = { requestUp() },
            iconKeys = d.iconKeys,
            iconSlot = d.iconSlot,
            // `this` is the top bar's RowScope; the two callbacks are the graph-owned controller's,
            // which is why this slot takes them rather than being a closed lambda on the host.
            actions = { d.actions(this, data, state, controller::save, controller::requestDelete) },
        )

        // Classic rendered this beside LabelEditScreen in the same setContent (`:126`), and it stays
        // beside it here: the prompt is driven by the controller, so only the arm that owns the
        // controller can raise it.
        deletePrompt?.let { prompt ->
            d.deletePromptSlot(
                prompt,
                state.name,
                controller::confirmDelete,
                controller::dismissDeletePrompt,
            )
        }
    }
}
