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

package net.bible.sharedui.workspaces.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.search.SearchModeController
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.KEY_OPEN_GLOBAL_SETTINGS
import net.bible.sharedcore.settings.KEY_OPEN_WORKSPACE_SETTINGS
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsScreenState
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.workspaces.WorkspaceSelectorController
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.NavSessionMemo
import net.bible.sharedui.nav.popOrExitOnFailedPop
import net.bible.sharedui.settings.BackgroundImageChooserLabels
import net.bible.sharedui.settings.BackgroundImageChooserScreen
import net.bible.sharedui.settings.ColorSettingsLabels
import net.bible.sharedui.settings.ColorSettingsScreen
import net.bible.sharedui.settings.TextDisplaySettingsScreen
import net.bible.sharedui.settings.TextDisplaySettingsScreenLabels
import net.bible.sharedui.workspaces.WorkspaceSelectorScreen

/**
 * [WorkspaceSelectorScreen]'s platform-supplied slots, ported from classic
 * `WorkspaceSelectorComposeActivity` (still in the tree, unreachable, until nav-graph slice 7
 * Task 13 deletes it -- the phase's standing rule, which is what keeps a mid-batch bisect possible).
 *
 * - [controllerFor] is a **HOST-MEMOISED** factory, not a per-entry one, and this destination is the
 *   second in these graphs to need that (the first is
 *   [net.bible.sharedui.bookmark.nav.ManageLabelsDeps.controllerFor]; see its kdoc, which argues the
 *   general case). The reason is this screen's shape rather than a preference: it NAVIGATES to
 *   [NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN], navigation-compose disposes this arm's composition
 *   while that child is on top, and [WorkspaceSelectorController] holds the user's entire unsaved
 *   working set -- the reordering, the renames, the clones and the freshly CREATED workspaces, which
 *   exist in the database already and are hard-deleted again on cancel. A per-entry factory would
 *   throw all of it away at the exact moment the user came back from editing a workspace's settings,
 *   which classic -- whose Activity was merely PAUSED behind the editor -- never did.
 *
 *   Classic's two `onCreate` side effects (`service.saveCurrentIntoDb()` and `controller.load()`)
 *   belong INSIDE this factory for the same reason, and that is not a detail: they ran once per
 *   Activity, and the in-graph equivalent of "once per Activity" is "once per memoised controller",
 *   not "once per composition". A `LaunchedEffect` in the arm would re-run on every return from the
 *   settings editor and `load()` would reset `working`/`dirty`/`created` -- silently discarding the
 *   very edit the round trip just delivered.
 *
 *   Its three lambdas are the halves only the graph can supply: how a result LEAVES (the channel),
 *   what cancelling means in a graph (a plain pop, nothing published), and the one
 *   `navController.navigate(...)` the host cannot write.
 *
 * - [settingsBundleJson] is `WorkspaceService.settingsBundleJson(id)`, the payload the detached
 *   editor route carries. It is a deps slot rather than a `controller.settingsBundleJson(id)` call in
 *   the arm only because the ROUTE is built where the navigate happens and the JSON is the host's
 *   (it serializes `SettingsBundle`, a Room-backed `:app` type); the controller exposes the same
 *   call, and either would do.
 *
 * - [workspaceIdOf] reads the workspace id back OUT of a returned bundle's JSON, which is how
 *   classic's `onActivityResult` did it and deliberately not from host-side state: the id must
 *   survive the host process being killed while the editor was foregrounded, and a plain field would
 *   not.
 *
 * - [title] is both the WINDOW title (classic's `android:label="@string/workspace_selector_title"`)
 *   and the string the screen draws in its own top bar, exactly as classic passed the one string to
 *   both.
 *
 * - [onHelp] is `CommonUtils.showHelp(activity, ...)`, which wants an `Activity`.
 *
 * **One classic member is deliberately NOT ported HERE: `onDetachedFromWindow`'s
 * `if (!finished) controller.cancel()`.** It is an Activity-lifetime hook, and the nearest in-graph
 * shape -- a `DisposableEffect`'s `onDispose` in THIS arm -- is actively WRONG, because
 * navigation-compose disposes this arm's composition every time the settings editor is pushed on
 * top of it. Porting it literally would fire `cancel()` mid-round-trip, and `cancel()` calls
 * `service.deleteCreated(...)`: it would hard-delete the workspaces this visit CLONED, in the middle
 * of editing one of them. (Cloned, not created -- `WorkspaceSelectorController.createNew` tracks
 * nothing in `created`; only `clone` does, because classic's own `createNewWorkspace` did not stage
 * its workspace either. An earlier version of this note said "created", which named the one case
 * `cancel()` does NOT delete.)
 *
 * **Its consequence is covered on the HOST instead, and had to be**, which is the fix-round-1
 * correction to this paragraph. `clone` inserts into the database immediately and the memo that
 * tracks the clone lives only as long as the host Activity; the host's `configChanges` does not
 * list `uiMode`/`fontScale`/`density`, so a dark-mode flip or a font-scale change destroys and
 * recreates the host mid-visit. Before the host-side hook, that left the clone as a permanent
 * workspace no `created` set tracked any more -- not even an explicit Cancel afterwards could
 * remove it. `NavHostComposeActivity.onDestroy` now calls
 * `WorkspaceSelectorController.discardCreated()` on a memo that is still non-null, i.e. exactly on a
 * visit that ended without a result, which is what classic's guard covered. It is a HOST hook and
 * not an arm one for the reason above: this arm's dispose is precisely the event that must not fire
 * it.
 */
class WorkspaceSelectorDeps(
    val controllerFor: (
        onResult: (workspaceId: String?, changed: Boolean) -> Unit,
        onCancel: () -> Unit,
        onEditSettings: (workspaceId: String) -> Unit,
    ) -> WorkspaceSelectorController,
    val title: String,
    val settingsBundleJson: (workspaceId: String) -> String,
    val workspaceIdOf: (settingsBundleJson: String) -> String,
    val onHelp: () -> Unit,
)

/**
 * Everything one ENTRY into the text-display-settings destination needs that `commonMain` cannot
 * build, resolved in one call from the route's own arguments -- see
 * [TextDisplaySettingsDeps.sessionFor].
 *
 * It is a returned object rather than five more lambdas on the deps class because all five depend on
 * the same host-side decision: whether this entry is a DETACHED (selector-originated) edit. Classic
 * held that decision in three coupled `by lazy` fields -- `detachedEdit`, `service` and the
 * `finish()` override that reads them -- and every one of the members below is built against
 * whichever service that choice produced. Passing the route arguments to five independent lambdas
 * would make five host-side lookups of the same answer and leave nothing holding them together.
 *
 * - [initialScope] is classic's `scopeFromIntent`, ported to route arguments as `scopeFromRoute`.
 *   The precedence defect design §3.2 item 2 records dies with the Intent: a scope level and a
 *   detached bundle are two NAMED arguments now, and the host's global-settings row sets only the
 *   first.
 * - [controllerFor] builds one [TextDisplaySettingsController] per [SettingsScope], against this
 *   entry's service. The arm CACHES what it returns, exactly as classic's `controllerCache` did, so
 *   a pop back to a shallower scope reuses (and refreshes) the same instance instead of losing edits
 *   made while drilled deeper. The `onNavigate` lambda is the arm's own drill-up handler, which the
 *   host cannot write.
 * - [colorControllerFor] is NOT cached, and that asymmetry is classic's own: a
 *   [ColorSettingsController] loads its state once in its constructor, and a whole-scope reset from
 *   the text-settings list can change colours behind the colours screen's back while it is not
 *   shown, so a cached instance would reopen showing pre-reset values. It also closes over the
 *   host's photo picker (`registerForActivityResult(PickVisualMedia)`), which stays an Activity
 *   registration by instruction -- it is why this is a host factory at all.
 * - [hideLabelsPayload] and [applyHideLabelsResult] are the two halves of classic's `openHideLabels`
 *   bridge, split apart because the hop between them is now an IN-GRAPH navigation rather than an
 *   `awaitIntent`. The first builds the `ManageLabelsData` JSON out of Room labels (classic
 *   `HideLabelsPreference.openDialog`'s payload); the second is classic's `RESULT_OK` branch, line
 *   for line -- a `reset` answer reverts the row, anything else updates the workspace's recent
 *   labels and hands the chosen ids to the controller.
 *
 *   **This is the fix round 1 correction to the shape Task 5 shipped**, and it was a live defect,
 *   not a latent one. What stood here was one `openHideLabels` lambda that did
 *   `awaitIntent(intentFor(this, NavRoutes.manageLabels(data)))` -- a `startActivityForResult`
 *   aimed at the host ITSELF, which is `singleTop` and is the activity on top. The system answers
 *   such a launch with `onNewIntent`, so the host navigated its LIVE graph to `manageLabels` and the
 *   pending request never resolved OK; the chosen labels were dropped, and the label manager then
 *   sat ABOVE `settings/textDisplay`, so its own exit took the in-graph branch and published into
 *   `manageLabelsResults.pending` -- which nothing in this arm was collecting. Unlike
 *   `ChooseDocument`'s Download row (dormant until Task 8, which is why Task 4 was allowed to leave
 *   its `awaitIntent` alone), `settings/textDisplay` is reachable today from the live `settings`
 *   destination, and the Hide-labels row is drawn at every non-window scope.
 * - [resultOnLeave] is the whole of classic's `finish()` override, and the condition inside it is
 *   load-bearing: it returns non-null **only when the detached edit actually changed**
 *   (`DetachedWorkspaceEdit.changed`, i.e. dirty or reset -- plan D3), and null on every other exit,
 *   including every non-detached one. Null means "pop with nothing published", which is what classic
 *   did by simply not calling `setResult`.
 */
class TextDisplaySettingsSession(
    val initialScope: SettingsScope,
    val controllerFor: (
        scope: SettingsScope,
        onNavigate: (key: String) -> Unit,
    ) -> TextDisplaySettingsController,
    val colorControllerFor: (scope: SettingsScope) -> ColorSettingsController,
    val hideLabelsPayload: (scope: SettingsScope) -> String,
    val applyHideLabelsResult: (resultJson: String, controller: TextDisplaySettingsController) -> Unit,
    val resultOnLeave: () -> TextSettingsResult?,
)

/**
 * [TextDisplaySettingsScreen]'s platform-supplied slots, ported from classic
 * `TextDisplaySettingsComposeActivity` -- the most internally-navigated destination in these graphs,
 * because it keeps its own WINDOW -> WORKSPACE -> GLOBAL drill-up stack and its own colours and
 * background-image sub-destinations rather than making any of them nav-graph destinations.
 *
 * **That internal stack stays internal, deliberately.** Classic implements the drill-up with a
 * `List<SettingsScope>` it pushes and pops (itself a port of the even older `onNewIntent`
 * re-launches), and the colours/chooser pair with two nullable fields. Turning them into four
 * routes would be a redesign of the screen, not a move of it -- and it would have to answer what a
 * drill-up route's arguments are before this slice can compile. The arm reproduces the stack as
 * remembered state; see [textDisplaySettingsNavState].
 *
 * - [sessionFor] resolves the whole entry in one host-side call; see [TextDisplaySettingsSession].
 * - [navStateMemo] is what keeps ONE visit's working set alive across a child destination, and it is
 *   the fix-round-1 answer to a hazard Task 5 wrote about the SELECTOR and then reproduced here.
 *   That commit's own argument -- navigation-compose disposes an arm's composition while a child
 *   sits on top of it, so anything held in a plain `remember` is rebuilt from the route on the way
 *   back -- applies verbatim to this destination, whose entire working set (the drill-up `navStack`,
 *   the per-scope controller cache, `colorsScope`, the hoisted search state and, worst, the
 *   `DetachedWorkspaceEdit` inside [TextDisplaySettingsSession]) was a plain `remember(args)`. With
 *   the Hide-labels row now pushing a real child (see [TextDisplaySettingsSession.hideLabelsPayload])
 *   the loss is not hypothetical: edit a workspace's settings from the selector, open Hide labels,
 *   come back, leave -- and `resultOnLeave` sees a FRESH edit whose `changed` is false, so nothing is
 *   published and the selector never learns of the edit. Silent data loss, no error anywhere.
 *
 *   It is a HOST-owned object passed in (like the two result channels) rather than something the arm
 *   could create, for [NavSessionMemo]'s stated reason: a memo `remember`ed in the arm would be
 *   disposed by the very event it exists to survive. The arm DROPS it on both branches of `leave()`
 *   -- result delivered and popped-with-nothing alike -- so a re-entry starts a fresh visit rather
 *   than resuming a finished one, exactly as `NavHostComposeActivity.manageLabelsSession` is nulled
 *   on every exit.
 * - [windowTitle] is classic's `android:label="@string/text_display_settings_activity_title"`. It is
 *   a separate slot from anything the screen draws, for [net.bible.sharedui.navigation.nav
 *   .GridChoosePassageDeps.windowTitle]'s reason in reverse: here the screen's own top-bar title is
 *   SCOPE-dependent and comes from the controller state, so the window title cannot be taken from it.
 * - [activeWorkspaceId] is `windowRepository.id`, read at the moment the "workspace settings" link is
 *   tapped rather than captured: the host always edits the ACTIVE workspace, and classic re-read it
 *   on every tap.
 * - [screenLabels], [colorSettingsLabels] and [backgroundImageChooserLabels] are the three resolved
 *   string bundles the three screens take -- ~90 `getString` calls between them, which is why they
 *   are FUNCTIONS and not values. Fix round 1: they were plain values, and this kdoc claimed the
 *   host built them lazily. It does (three `by lazy` fields), but a value slot is read where the
 *   deps object is CONSTRUCTED, inside the host's one `remember { }`, so every one of those ~90
 *   `getString` calls ran on every host launch no matter which of the host's four clusters the route
 *   asked for. A `() -> Labels` slot is read where it is USED -- in this arm, i.e. only when this
 *   destination is actually shown -- which is what the claim always said. The `by lazy` behind them
 *   still means the strings are resolved at most once per host.
 * - [inheritedFromWorkspace]/[inheritedFromGlobal] are the two strings classic's `badgeLabel` chose
 *   between. The `when` itself lives in the arm ([badgeLabel] below) -- it is ordinary `:sharedCore`
 *   enum work -- and only the two resolved strings need the host.
 * - [thumbnailFor] is `BackgroundThumbnailResolver.resolve`: `BitmapFactory` plus `AndBibleAddons`,
 *   neither of which crosses into `commonMain`.
 */
class TextDisplaySettingsDeps(
    val sessionFor: (args: TextDisplaySettingsArgs) -> TextDisplaySettingsSession,
    val navStateMemo: NavSessionMemo<TextDisplaySettingsArgs, TextDisplaySettingsNavState>,
    val windowTitle: String,
    val activeWorkspaceId: () -> String,
    val screenLabels: () -> TextDisplaySettingsScreenLabels,
    val colorSettingsLabels: () -> ColorSettingsLabels,
    val backgroundImageChooserLabels: () -> BackgroundImageChooserLabels,
    val inheritedFromWorkspace: String,
    val inheritedFromGlobal: String,
    val thumbnailFor: (token: String) -> ImageBitmap?,
)

/**
 * The workspace cluster's two destinations and their platform slots. Same top-level shape as
 * [net.bible.sharedui.navigation.nav.ChooserNavDeps]: [exitHost] and [setWindowTitle] are
 * graph-wide, the result channels next, then one nested holder per destination.
 *
 * **Two destinations in one graph because they are one round trip** (design §6.2, plan §5.4):
 * `TextDisplaySettings` sets a result for `WorkspaceSelector` and for nothing else -- request code
 * 999 in classic, the only consumer in the tree -- so migrating either alone would leave a result
 * crossing the graph boundary in the middle of a slice.
 */
class WorkspaceNavDeps(
    /**
     * Leave the host. Reachable only through [popOrExit] below, i.e. only if a pop somehow fails --
     * both destinations are entered from inside the graph, so there is always a parent to pop to.
     * The same deliberate asymmetry with the channels' `exitWithResult` that
     * [net.bible.sharedui.navigation.nav.ChooserNavDeps.exitHost] documents: losing a RESULT
     * silently is worth failing loudly on; a bare up-press has nothing to lose.
     */
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack), not the on-screen top-bar title a screen
     * draws for itself. Called from each destination's `LaunchedEffect(title)`, keyed on the VALUE
     * so a state-derived title cannot go stale. Both destinations had an `android:label` in the
     * manifest, and each passes the string its own label carried.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * `WorkspaceSelector`'s channel. Design §1.1: both of this graph's destinations are entered only
     * from inside the graph, so the host's `exitWithResult` for this channel is a hard `error(...)`
     * -- a destination reached with no parent fails loudly instead of silently packing an Intent for
     * a caller that does not exist.
     */
    val workspaceResults: NavResultChannel<WorkspaceResult>,
    /**
     * `TextDisplaySettings`' channel -- the same in-graph-only contract as [workspaceResults], and
     * the one channel in this graph that is both PRODUCED and CONSUMED inside it: the settings
     * destination delivers, and the `WORKSPACE_SELECTOR` arm collects [NavResultChannel.pending].
     */
    val textSettingsResults: NavResultChannel<TextSettingsResult>,
    /**
     * **The label manager's channel, which this graph does not own** -- it belongs to
     * `BookmarkNavGraph`, whose `MANAGE_LABELS_PATTERN` arm produces it, and the HOST passes the very
     * same [NavResultChannel] instance to both graphs. It is here because the text-display-settings
     * destination's Hide-labels row navigates to that destination in-graph (fix round 1, see
     * [TextDisplaySettingsSession.hideLabelsPayload]) and therefore has to collect the answer, which
     * is the first time a destination outside the bookmark cluster consumes a bookmark-cluster
     * result.
     *
     * Two arms in two graphs collect the same channel, and that is safe for one reason worth stating:
     * navigation-compose composes only the destination on TOP, so at most one of them is live when a
     * result lands. The text-settings arm additionally refuses to consume unless it is the one that
     * asked (`TextDisplaySettingsNavState.awaitingHideLabels`), so it cannot swallow a result the
     * bookmark list was waiting for.
     */
    val manageLabelsResults: NavResultChannel<ManageLabelsResult>,
    // — WORKSPACE SELECTOR —
    val workspaceSelector: WorkspaceSelectorDeps,
    // — TEXT DISPLAY SETTINGS —
    val textDisplaySettings: TextDisplaySettingsDeps,
)

/**
 * Pop to the parent destination, or -- when there is none -- leave the host. The same one-line
 * extension every other cluster graph carries, this cluster's own private copy; the boolean branch
 * itself is [popOrExitOnFailedPop], shared so that "a failed pop still leaves" is decided in one
 * place.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The text-display-settings destination's internal navigation
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * Classic `TextDisplaySettingsComposeActivity`'s internal navigation state, moved off the Activity
 * and onto one remembered object: the drill-up `navStack`, the colours/chooser sub-destinations, the
 * per-scope controller cache and the hoisted search state.
 *
 * ONE class rather than eight loose `remember`s in the arm, because they are one machine: [pop]
 * reads and writes five of them, and splitting them would put the machine's whole branch table in
 * the composable body. It also removes the mutual forward reference Kotlin would not allow between
 * two local functions -- [controllerFor] builds a controller whose `onNavigate` calls [onNavigate],
 * which in turn resolves a controller through [controllerFor].
 *
 * [searchQuery]/[searchMode] are held HERE rather than inside the composition for the reason
 * classic hoisted them to Activity level (Task 7 fix round 1): the back handler has to reach them,
 * and they must OUTLIVE pushing the colours/chooser sub-destinations. That is also why
 * [shouldCloseSearchOnBack]'s gate is `atListDestination && searchActive` and not `searchActive`
 * alone -- those sub-destinations render no search UI, so a back press from inside one must always
 * [pop], never close an invisible search bar and swallow the press.
 *
 * **Held by [TextDisplaySettingsDeps.navStateMemo], not by the arm's `remember`** (fix round 1): one
 * instance is one VISIT to this destination, and a visit now outlives a child destination being
 * pushed on top of it. Public rather than private only so the host can name the memo's type; nothing
 * outside this file constructs one.
 */
class TextDisplaySettingsNavState internal constructor(
    val session: TextDisplaySettingsSession,
    /**
     * True for a `startAtColors` route, where the colours destination is the ROOT rather than
     * something pushed on top of the list -- so backing out of it must LEAVE, not reveal a list the
     * user never opened. Classic's `startedAtColors`.
     */
    val startedAtColors: Boolean,
    val activeWorkspaceId: () -> String,
    /**
     * `navController.navigate(NavRoutes.manageLabels(payload))`, the one line only the arm can write.
     * Supplied by the arm that first builds this state and retained with it: everything it closes
     * over (the host's `NavHostController` and its deps object) is host-lifetime, so the lambda
     * cannot go stale while this visit lasts.
     */
    private val navigateToHideLabels: (payload: String) -> Unit,
) {
    var navStack by mutableStateOf(listOf(session.initialScope))
        private set

    var colorsScope by mutableStateOf(if (startedAtColors) session.initialScope else null)
        private set

    var chooserNight by mutableStateOf<Boolean?>(null)
        private set

    val searchQuery = MutableStateFlow("")
    val searchMode = SearchModeController(onClearQuery = { searchQuery.value = "" })

    private val controllerCache = mutableMapOf<SettingsScope, TextDisplaySettingsController>()

    /**
     * The scope whose controller is waiting for the label manager's answer, i.e. non-null exactly
     * while `manageLabels` is the destination this visit pushed. Deliberately NOT snapshot state:
     * nothing renders from it, and a recomposition must not be able to observe it changing.
     *
     * It is the arm's gate as well as its memory. The `manageLabelsResults` channel is shared with
     * `BookmarkNavGraph`, so an arm that consumed whatever was pending could steal a result the
     * bookmark list had asked for; the arm consumes only while [awaitingHideLabels].
     */
    private var hideLabelsScope: SettingsScope? = null

    val awaitingHideLabels: Boolean get() = hideLabelsScope != null

    /** Classic's `controllerFor` (`controllerCache.getOrPut`), cache and all. */
    fun controllerFor(scope: SettingsScope): TextDisplaySettingsController =
        controllerCache.getOrPut(scope) {
            session.controllerFor(scope) { key -> onNavigate(scope, key) }
        }

    fun selectBackgroundImageSlot(night: Boolean?) { chooserNight = night }

    /** Classic's `onNavigate(scope, key)`, unchanged. */
    fun onNavigate(scope: SettingsScope, key: String) {
        when (key) {
            // The workspace link only appears at WINDOW scope; the host always edits the active
            // workspace, so its id is the window repository's -- read now, not captured.
            KEY_OPEN_WORKSPACE_SETTINGS -> {
                // The pushed scope is rendered fresh; a query left over from the scope being left
                // would silently filter a list it was never typed against.
                searchMode.close()
                navStack = navStack + SettingsScope.Workspace(activeWorkspaceId())
            }
            KEY_OPEN_GLOBAL_SETTINGS -> {
                searchMode.close()
                navStack = navStack + SettingsScope.Global
            }
            TextSettingType.COLORS.name -> {
                chooserNight = null
                colorsScope = scope
            }
            // Classic's `openHideLabels(scope)`, with its `awaitIntent` round trip replaced by an
            // in-graph push -- see TextDisplaySettingsSession.hideLabelsPayload for what the old
            // shape actually did when the host answered its own startActivityForResult.
            TextSettingType.BOOKMARKS_HIDELABELS.name -> {
                hideLabelsScope = scope
                navigateToHideLabels(session.hideLabelsPayload(scope))
            }
        }
    }

    /**
     * Classic's `RESULT_OK` branch of `openHideLabels`, applied to the controller of the scope that
     * ASKED -- which is why [hideLabelsScope] is remembered rather than re-derived from `navStack`:
     * the answer may arrive at a moment when a deeper scope has since been pushed.
     *
     * Once-only by construction, and doubly so: the arm consumes the channel (which clears it) and
     * this clears [hideLabelsScope] before applying, so a recomposition that re-ran this with the
     * same JSON would be a no-op returning `false`. Applying a label set twice is not idempotent --
     * the second application would write the recent-labels list again over a workspace the user may
     * have changed meanwhile.
     */
    fun applyHideLabelsResult(resultJson: String): Boolean {
        val scope = hideLabelsScope ?: return false
        hideLabelsScope = null
        session.applyHideLabelsResult(resultJson, controllerFor(scope))
        return true
    }

    /**
     * Classic's `pop()`, with its terminal `finish()` turned into a RETURN VALUE: `false` means
     * "this destination has nothing left to pop, leave it", which only the arm can act on (it owns
     * the channel and the [NavHostController]). Every other branch is classic's, line for line.
     */
    fun pop(): Boolean {
        when {
            chooserNight != null -> chooserNight = null
            // A colours-originated entry has nothing underneath the colours destination.
            colorsScope != null && startedAtColors -> return false
            colorsScope != null -> {
                val scope = colorsScope!!
                colorsScope = null
                // Colours are edited through a separate ColorSettingsController, so the cached text
                // controller for this scope may now show a stale COLORS inheritance badge.
                controllerFor(scope).refresh()
            }
            navStack.size > 1 -> {
                navStack = navStack.dropLast(1)
                // Edits made at the deeper scope may have changed what the shallower one inherits.
                navStack.last().let { controllerFor(it).refresh() }
            }
            else -> return false
        }
        return true
    }
}

/**
 * Whether a back press at the CURRENT destination should close search mode instead of falling
 * through to normal back navigation. The `commonMain` twin of classic's own
 * `shouldCloseSearchOnBack`, kept as a named function for that one's stated reason: it is the gate a
 * defect once hid in, and `atListDestination` must be false whenever a sub-destination is showing.
 *
 * `internal` rather than `private`, which is the whole point classic's copy made and this one had
 * lost: classic widened its visibility precisely so a test could prove the defect the gate once hid
 * (`TextDisplaySettingsComposeActivityBackTest`, four cases). The `commonTest` twin of those four is
 * `TextDisplaySettingsNavStateTest`, and Task 13 may delete the classic pair without losing them.
 */
internal fun shouldCloseSearchOnBack(atListDestination: Boolean, searchActive: Boolean): Boolean =
    atListDestination && searchActive

/**
 * Classic's `badgeLabel`: the "inherited from" badge for one settings row, or null when it is set
 * here. `internal` so `TextDisplaySettingsNavStateTest` can pin the three arms plus the
 * not-a-[TextSettingType] key that `runCatching` exists for -- the drill-up link rows go through the
 * same `badgeFor` lambda and their keys are not enum names.
 */
internal fun badgeLabel(
    state: TextDisplaySettingsScreenState,
    key: String,
    inheritedFromWorkspace: String,
    inheritedFromGlobal: String,
): String? = runCatching { TextSettingType.valueOf(key) }.getOrNull()
    ?.let { state.rows[it]?.inheritedFrom }
    ?.let {
        when (it) {
            InheritedFrom.WORKSPACE -> inheritedFromWorkspace
            InheritedFrom.GLOBAL -> inheritedFromGlobal
            InheritedFrom.NONE -> null
        }
    }

/**
 * The visit's state for [args], built once per visit and held by the HOST.
 *
 * `remember(args)` is still here, and it is doing a different job from the memo: it keeps the
 * composition from re-reading the memo on every recomposition. The memo is what makes the value
 * survive the composition being DISPOSED, which is what happens whenever this destination pushes a
 * child -- see [TextDisplaySettingsDeps.navStateMemo].
 */
@Composable
private fun textDisplaySettingsNavState(
    args: TextDisplaySettingsArgs,
    deps: TextDisplaySettingsDeps,
    navigateToHideLabels: (payload: String) -> Unit,
): TextDisplaySettingsNavState = remember(args) {
    deps.navStateMemo.getOrPut(args) {
        TextDisplaySettingsNavState(
            session = deps.sessionFor(args),
            startedAtColors = args.startAtColors,
            activeWorkspaceId = deps.activeWorkspaceId,
            navigateToHideLabels = navigateToHideLabels,
        )
    }
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The workspace selector and the text-display-settings editor, registered into the app's single
 * `NavHost` by the host Activity.
 *
 * **Nothing routes to the selector yet** -- `ScreenLauncher.MIGRATED` is untouched until nav-graph
 * slice 7 Task 8, its consumer (the reading view) arrives in Task 9, and both classic Activities
 * stay in the tree, compiling and still reachable by their own `Screen.X` arms, until Task 13. The
 * settings destination, by contrast, has ONE live in-graph caller from the moment this task lands:
 * the host's own global-text-settings row, which is the defect design §3.2 item 2 records and this
 * task fixes.
 *
 * Every result is delivered IN-GRAPH (design §1.1): neither destination sets an Activity result, and
 * both channels' host-side `exitWithResult` is a hard `error(...)`.
 */
fun NavGraphBuilder.workspaceNavGraph(navController: NavHostController, deps: WorkspaceNavDeps) {
    // ——— WORKSPACE SELECTOR ———
    composable(route = NavRoutes.WORKSPACE_SELECTOR) {
        val d = deps.workspaceSelector

        // Over a HOST-MEMOISED factory -- see WorkspaceSelectorDeps.controllerFor for why this
        // controller must survive the settings editor sitting on top of this destination, and why
        // classic's two onCreate side effects run inside the factory rather than in an effect here.
        val controller = remember {
            d.controllerFor(
                { workspaceId, changed ->
                    deps.workspaceResults.deliver(navController, WorkspaceResult(workspaceId, changed))
                },
                // Classic's cancel path set RESULT_CANCELED with an EMPTY Intent and its consumer
                // ignores it, so the in-graph equivalent publishes nothing at all -- see
                // WorkspaceResult's kdoc for why there is no cancelled variant to publish.
                { navController.popOrExit(deps.exitHost) },
                { id ->
                    navController.navigate(
                        NavRoutes.textDisplaySettings(settingsBundle = d.settingsBundleJson(id)),
                    )
                },
            )
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // ——— the channel's IN-GRAPH branch, consumed ———
        // Classic's `onActivityResult(WORKSPACE_SETTINGS_CHANGED = 999)`. The same shape
        // BookmarkNavGraph's two consumers use, and for the same reasons: `consume()` clears the
        // channel in the same breath as reading it, so a recomposition cannot apply the same edit
        // twice -- and clearing re-triggers this effect with `null`, which is the early return.
        //
        // The workspace id is read back out of the RETURNED JSON, never from host-side state, so the
        // round trip survives the host process being killed while the editor was foregrounded --
        // classic's own reasoning, preserved.
        val pendingTextSettings by deps.textSettingsResults.pending.collectAsState()
        LaunchedEffect(pendingTextSettings) {
            if (pendingTextSettings == null) return@LaunchedEffect
            val result = deps.textSettingsResults.consume() ?: return@LaunchedEffect
            controller.applyWorkspaceSettings(
                id = d.workspaceIdOf(result.settingsBundleJson),
                settingsBundleJson = result.settingsBundleJson,
                reset = result.reset,
            )
        }

        val workspaces by controller.workspaces.collectAsState()
        val dirty by controller.dirty.collectAsState()
        val query by controller.query.collectAsState()
        val filtering by controller.filtering.collectAsState()
        val canDelete by controller.canDelete.collectAsState()
        val copy by controller.copySettingsState.collectAsState()
        val pending by controller.pendingSelectId.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()

        // Classic's `onBackPressed` override: back leaves search mode before it leaves the screen
        // (a collapsed SearchView consumed back the same way). ONE gated handler with the branch
        // inside it, never two stacked ones -- two would work only by declaration ORDER. Always
        // enabled, because the non-search branch is `controller.cancel()`, not a plain pop: cancel
        // hard-deletes the workspaces this visit CLONED (`createNew` stages nothing), so the
        // NavHost's own back must not reach past it.
        PlatformBackHandler(enabled = true) {
            if (searchModeActive) controller.closeSearch() else controller.cancel()
        }

        WorkspaceSelectorScreen(
            title = d.title,
            workspaces = workspaces, dirty = dirty, canDelete = canDelete,
            filtering = filtering, query = query, searchModeActive = searchModeActive,
            copySettingsState = copy, pendingSelectId = pending,
            onQueryChange = controller::setQuery,
            onOpenSearch = controller::openSearch,
            onCloseSearch = controller::closeSearch,
            onMove = controller::moveIndex,
            onSelect = controller::selectWorkspace,
            onRename = controller::rename,
            onClone = controller::clone,
            onDelete = controller::requestDelete,
            onEditSettings = controller::editSettings,
            onCopySettings = controller::beginCopySettings,
            onCopySettingsToGlobal = controller::beginCopySettingsToGlobal,
            onChooseCopyTypes = controller::chooseCopyTypes,
            onChooseCopyTargets = controller::chooseCopyTargets,
            onCancelCopySettings = controller::cancelCopySettings,
            onCreate = controller::createNew,
            onSave = controller::save,
            onCancel = controller::cancel,
            onConfirmPendingSelect = controller::confirmPendingSelect,
            onDismissPendingSelect = controller::dismissPendingSelect,
            onHelp = { d.onHelp() },
            // Classic's `onNavigateUp = { controller.cancel() }`: the up-arrow CANCELS, it does not
            // merely pop. Routing it through the controller is what makes the created-workspace
            // cleanup run on this exit too.
            onNavigateUp = { controller.cancel() },
        )
    }

    // ——— TEXT DISPLAY SETTINGS ———
    composable(
        route = NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SCOPE_LEVEL) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_WINDOW_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_WORKSPACE_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_START_AT_COLORS) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_SETTINGS_BUNDLE) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val d = deps.textDisplaySettings

        // Read off the ARGUMENTS, never through `NavRoutes.readTextDisplaySettings`: that parser
        // takes a route STRING, and an arm never has one -- `destination.route` is the PATTERN, with
        // the placeholders unsubstituted. The host uses the parser on inbound routes; an arm uses
        // the bundle the library already parsed AND DECODED, so nothing here decodes twice.
        val args = remember(backStackEntry) {
            backStackEntry.arguments?.read {
                TextDisplaySettingsArgs(
                    scopeLevel = getStringOrNull(NavRoutes.ARG_SCOPE_LEVEL),
                    windowId = getStringOrNull(NavRoutes.ARG_WINDOW_ID),
                    workspaceId = getStringOrNull(NavRoutes.ARG_WORKSPACE_ID),
                    startAtColors = getStringOrNull(NavRoutes.ARG_START_AT_COLORS) == "true",
                    settingsBundleJson = getStringOrNull(NavRoutes.ARG_SETTINGS_BUNDLE),
                )
            } ?: TextDisplaySettingsArgs()
        }

        val nav = textDisplaySettingsNavState(args, d) { payload ->
            navController.navigate(NavRoutes.manageLabels(payload))
        }

        LaunchedEffect(d.windowTitle) { deps.setWindowTitle(d.windowTitle) }

        // ——— the label manager's answer, collected ———
        // The other half of the Hide-labels round trip (fix round 1). Same shape as the selector
        // arm's consumption of `textSettingsResults` below and BookmarkNavGraph's two consumers:
        // `consume()` clears the channel in the same breath as reading it, so a recomposition cannot
        // apply the same label set twice, and clearing re-triggers this effect with `null`, which is
        // the early return.
        //
        // The `awaitingHideLabels` gate is this consumer's own, and it is not optional: the channel
        // belongs to BookmarkNavGraph and is shared, so consuming unconditionally would let this arm
        // clear a result the bookmark list asked for if one were ever pending when it opens.
        val pendingHideLabels by deps.manageLabelsResults.pending.collectAsState()
        LaunchedEffect(pendingHideLabels) {
            if (pendingHideLabels == null || !nav.awaitingHideLabels) return@LaunchedEffect
            val result = deps.manageLabelsResults.consume() ?: return@LaunchedEffect
            nav.applyHideLabelsResult(result.data)
        }

        // Classic's `finish()` override, which is the destination's ONLY exit: every path out --
        // pop()'s terminal branch, the up-arrow and the system back -- ends here rather than
        // publishing anything of its own. A null result is classic not calling `setResult` at all.
        //
        // The memo is dropped on BOTH branches, and the ORDER matters: `resultOnLeave()` is read
        // first, because it reads the very session the drop discards. A visit that has ended must not
        // be resumable -- re-entering the route would otherwise reopen the previous edit's drill-up
        // stack and its already-delivered detached edit, which would publish a second time.
        fun leave() {
            val result = nav.session.resultOnLeave()
            d.navStateMemo.drop()
            if (result != null) deps.textSettingsResults.deliver(navController, result)
            else navController.popOrExit(deps.exitHost)
        }

        fun back() {
            val atListDestination = nav.colorsScope == null && nav.chooserNight == null
            if (shouldCloseSearchOnBack(atListDestination, nav.searchMode.active.value)) {
                nav.searchMode.close()
            } else if (!nav.pop()) {
                leave()
            }
        }

        // Always enabled, exactly as classic's `BackHandler { ... }` was: the branch lives inside
        // the handler, never in its `enabled` flag, because `pop()` is the thing that decides.
        PlatformBackHandler(enabled = true) { back() }

        val activeColorsScope = nav.colorsScope
        if (activeColorsScope != null) {
            // Fresh controller every time the colours destination is (re-)entered -- see
            // TextDisplaySettingsSession.colorControllerFor for why this must NOT be cached.
            val colorController = remember(activeColorsScope) { nav.session.colorControllerFor(activeColorsScope) }
            val colorState by colorController.state.collectAsState()
            val night = nav.chooserNight
            // Resolved HERE rather than where the deps object was built -- see
            // TextDisplaySettingsDeps.screenLabels for the ~90 `getString` calls that bought.
            val screenLabels = d.screenLabels()

            if (night != null) {
                BackgroundImageChooserScreen(
                    options = colorState.backgroundOptions,
                    labels = d.backgroundImageChooserLabels(),
                    loading = colorState.loading,
                    deleteConfirm = colorState.deleteConfirm,
                    thumbnailFor = d.thumbnailFor,
                    onUp = { back() },
                    onSelect = { initials ->
                        colorController.onSelectBackgroundImage(night, initials)
                        nav.selectBackgroundImageSlot(null)
                    },
                    onImport = colorController::onImportBackgroundImage,
                    onRequestDelete = colorController::onRequestDeleteBackgroundImage,
                    onConfirmDelete = colorController::onConfirmDeleteBackgroundImage,
                    onDismissDelete = colorController::onDismissDeleteConfirm,
                )
            } else {
                ColorSettingsScreen(
                    state = colorState,
                    labels = d.colorSettingsLabels(),
                    onUp = { back() },
                    onReset = colorController::onReset,
                    // The SAME resolved strings TextDisplaySettingsScreen's own reset confirm gets.
                    resetConfirmMessage = screenLabels.resetConfirmMessage,
                    confirmLabel = screenLabels.okLabel,
                    cancelLabel = screenLabels.cancelLabel,
                    onColorChange = colorController::onColorChange,
                    onNoiseChange = colorController::onNoiseChange,
                    onWorkspaceColorChange = colorController::onWorkspaceColorChange,
                    onOpacityChange = colorController::onOpacityChange,
                    onChangeBackgroundImage = { n -> nav.selectBackgroundImageSlot(n) },
                )
            }
        } else {
            val scope = nav.navStack.last()
            val controller = nav.controllerFor(scope)
            val state by controller.state.collectAsState()
            val query by nav.searchQuery.collectAsState()
            val searchModeActive by nav.searchMode.active.collectAsState()

            TextDisplaySettingsScreen(
                state = state,
                dialogLabels = d.screenLabels(),
                badgeFor = { key ->
                    badgeLabel(state, key, d.inheritedFromWorkspace, d.inheritedFromGlobal)
                },
                onUp = { back() },
                onSwitch = controller::onSwitch,
                onListChoice = controller::onListChoice,
                onNumericChange = controller::onNumericChange,
                onMarginsChange = controller::onMarginsChange,
                onRevert = controller::onRevert,
                onReset = controller::onReset,
                onNavigate = { key -> nav.onNavigate(scope, key) },
                searchQuery = query,
                searchModeActive = searchModeActive,
                onSearchQueryChange = { nav.searchQuery.value = it },
                onOpenSearch = nav.searchMode::open,
                onCloseSearch = nav.searchMode::close,
            )
        }
    }
}
