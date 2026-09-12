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
 *   as `getString(data.titleId)` (`ManageLabelsComposeActivity.kt:170`): the title is an Android
 *   string RESOURCE ID carried inside the route's payload (`ManageLabelsContract.kt`'s `titleId`,
 *   one of four depending on the mode), and no `commonMain` arm can resolve one. It is also the
 *   title [ManageLabelsScreen] draws in its own top bar, exactly as classic passed the same string
 *   to both.
 *
 * - [onLabelEditResult] is where the child editor's result lands: classic's `editLabel` continuation
 *   (`:499-551`), which reconciles the edit into `labelsById`, the controller and the workspace
 *   override table. It takes the whole [NavLabelEditResult] -- including `Cancelled`, which classic
 *   handled as an early return on `RESULT_CANCELED` -- so the ported body keeps its shape rather than
 *   splitting the cancel branch up into the arm. The host does not need the controller passed back: it
 *   holds the memoised session that owns it.
 *
 * - [initialSearchMode] / [persistSearchMode] are classic's `labels_list_search_mode` setting, seeded
 *   before `setContent` (`:153-156`) and written in `saveAndExit` (`:576-578`). Both are ints
 *   (a [SearchMode] ordinal) because `CommonUtils.settings` is a host type. The arm applies the
 *   STUDYPAD gate classic applied, and writes ON CHANGE rather than on exit -- see the arm's comment.
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
    val persistSearchMode: (ordinal: Int) -> Unit,
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
 * **All three result channels are declared here now, with only one destination built.** The cluster
 * has three result-producing destinations and this is the file that owns them; creating the channels
 * one per task would mean every later task WIDENING this type, and a widened deps class is the kind
 * of change that quietly leaves a caller behind. The two nested deps holders those destinations need
 * are a different matter and are added with the destinations themselves -- an unused
 * `NavResultChannel` field costs one line, an unused deps class costs a whole unverified port.
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
     * How the bookmark LIST hands back the row the user picked. Its destination arrives with the
     * `BOOKMARKS_PATTERN` arm; the channel exists now for the reason [BookmarkNavDeps]' kdoc gives.
     */
    val bookmarkResults: NavResultChannel<BookmarkResult>,
    /**
     * How the label MANAGER hands back its edited `ManageLabelsData`, in either of the two ways it
     * can be entered -- see [NavResultChannel]'s own kdoc. Today only the outside entry is live
     * (six classic callers reach it as the host's START destination), so every result takes the
     * exit branch; `Bookmarks` makes the in-graph entry live too, and the arm needs no change for
     * it, because the branch is [NavResultChannel.deliver]'s to take at runtime.
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
 * Today it holds two: [NavRoutes.MANAGE_LABELS_PATTERN], the label manager, and
 * [NavRoutes.LABEL_EDIT_PATTERN], the label editor it navigates to. Neither is reachable through
 * `ScreenLauncher.MIGRATED` and that is correct rather than an oversight -- both take a REQUIRED
 * `data` argument, so an argument-free route would open a screen with nothing to show (the
 * `Screen.RawLlmLog` precedent), and every real edge builds `NavRoutes.manageLabels(data)` /
 * `NavRoutes.labelEdit(data)` directly. Live traffic from outside therefore still goes to the
 * classic Activities through the coexistence seam until a later task deletes them; the edge that is
 * live TODAY is the in-graph one, manager -> editor.
 *
 * **Almost every exit here carries a RESULT**, which is why [popOrExit] is used in exactly one
 * place. The up-arrow, Back, Save, Delete and the StudyPad selection all go through a
 * [NavResultChannel], and [NavResultChannel.deliver] is what chooses between popping to a parent and
 * exiting the host; binding any of them to a plain `popOrExit` would leave without a result at all,
 * which classic never did. The exception is a destination reached with NO payload at all, which
 * cannot construct a result to leave with -- see the `MANAGE_LABELS_PATTERN` arm's guard.
 */
fun NavGraphBuilder.bookmarkNavGraph(navController: NavHostController, deps: BookmarkNavDeps) {
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

        // Classic's `labels_list_search_mode` round trip (`ManageLabelsComposeActivity.kt:153-156`
        // and `:576-578`), with one deliberate difference: it is written ON CHANGE rather than in
        // `saveAndExit`. Classic could write it at the exit because every exit went through
        // saveAndExit; here the exits are the HOST's (they need Room), so an arm that only wrote on
        // its own up-arrow would silently miss the StudyPad-selection exit -- which is the one exit
        // that only STUDYPAD mode, the only mode this setting applies to, can even take. Writing on
        // change persists the same value the user last chose and cannot miss a path.
        //
        // rememberSaveable, NOT remember: this arm's composition is DISPOSED while the label editor
        // sits on top of it (the reason SettingsNavGraph's search filter is saveable too), and a
        // plain remember would re-run the seed below on every return. `null` means "not seeded yet",
        // which is also what stops the write-back effect firing before the seed has happened.
        var persistedSearchMode by rememberSaveable { mutableStateOf<Int?>(null) }
        LaunchedEffect(controller) {
            if (!isStudyPad) return@LaunchedEffect
            val seed = persistedSearchMode ?: d.initialSearchMode().also { persistedSearchMode = it }
            val mode = SearchMode.entries.getOrElse(seed) { SearchMode.NAME_START }
            // Guarded because setSearchMode re-dispatches the search; classic could seed
            // unconditionally only because it ran once, before the first collection.
            if (controller.searchMode.value != mode) controller.setSearchMode(mode)
        }
        LaunchedEffect(searchMode) {
            val seeded = persistedSearchMode ?: return@LaunchedEffect
            if (searchMode.ordinal == seeded) return@LaunchedEffect
            persistedSearchMode = searchMode.ordinal
            d.persistSearchMode(searchMode.ordinal)
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

        // Classic's `onBackPressed` override (`:319-326`): back closes the search bar if it is open,
        // and otherwise SAVES -- this screen has no cancel, and reading these two the wrong way
        // round would silently discard the user's label edits.
        //
        // Two handlers rather than one branch, matching SettingsNavGraph's search handler, and the
        // ORDER is load-bearing: back is dispatched to the most recently registered ENABLED handler
        // (Compose's documented "inner-most consumes"), so the always-enabled save must be declared
        // FIRST and the conditional search one SECOND. PlatformBackHandler, never
        // androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = true) { controller.save() }
        PlatformBackHandler(enabled = searchModeActive) { controller.closeSearch() }

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
