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
import androidx.compose.runtime.remember
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
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.LabelEditResult as NavLabelEditResult
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.nav.NavResultChannel

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
     * How the label MANAGER hands back its edited `ManageLabelsData`. Its destination arrives with
     * the `MANAGE_LABELS_PATTERN` arm; see [BookmarkNavDeps]' kdoc for why the channel is here
     * already.
     */
    val manageLabelsResults: NavResultChannel<ManageLabelsResult>,
    /**
     * How the label EDITOR hands back its result, in either of the two ways it can be entered -- see
     * [NavResultChannel]'s own kdoc.
     *
     * Both branches are real for this destination, which is why it gets a channel rather than a
     * parent-supplied lambda: the label manager navigates to it from INSIDE this graph (publish and
     * pop), and `ScreenLauncher`'s six-plus classic callers reach it as the host's START destination
     * (exit the host with the result). Nothing collects [NavResultChannel.pending] on it yet --
     * `ManageLabels` is the only in-graph consumer and it is not migrated yet -- so until then every
     * result takes the exit branch. The arm that migrates `ManageLabels` MUST collect `pending`, or
     * an edit made from inside the graph will pop with the user's changes silently dropped.
     */
    val labelEditResults: NavResultChannel<NavLabelEditResult>,
    // — LABEL EDIT —
    val labelEdit: LabelEditDeps,
)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The bookmark cluster's destinations. Registered into the app's single `NavHost` by the host
 * Activity.
 *
 * Today it holds one: [NavRoutes.LABEL_EDIT_PATTERN], the label editor. It is registered but not yet
 * REACHABLE in-graph, and that is correct rather than an oversight -- its only in-graph consumer is
 * the label manager, which is not migrated yet, and `Screen.LabelEdit` is deliberately absent from
 * `ScreenLauncher.MIGRATED` (its `data` argument is required, so an argument-free route would open
 * an editor with nothing to edit -- the `Screen.RawLlmLog` precedent). Live traffic therefore still
 * goes to the classic `LabelEditComposeActivity` through the coexistence seam until a later task
 * deletes it.
 *
 * **There is no `NavHostController.popOrExit` extension in this file yet**, unlike the other three
 * cluster graphs. The one destination here produces a RESULT, so it never leaves by a bare pop:
 * every exit -- the up-arrow, Back, Save, Delete -- goes through [BookmarkNavDeps.labelEditResults],
 * and [NavResultChannel.deliver] is what chooses between popping to a parent and exiting the host.
 * Binding `onUp` to a plain `popOrExit` here would leave without a result at all, which classic
 * never did (its up-arrow sets `RESULT_CANCELED`). The destinations that DO leave without one bring
 * the helper with them.
 */
fun NavGraphBuilder.bookmarkNavGraph(navController: NavHostController, deps: BookmarkNavDeps) {
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
