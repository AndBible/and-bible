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

package net.bible.sharedui.reading.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewHostCallbacks
import net.bible.sharedcore.reading.ReadingViewHostHandlers
import net.bible.sharedcore.reading.ReadingViewKey
import net.bible.sharedcore.reading.ReadingViewVisibility

/**
 * What the `reading` destination needs from its platform — the same top-level shape as every other
 * cluster's deps holder ([net.bible.sharedui.navigation.nav.ChooserNavDeps],
 * [net.bible.sharedui.workspaces.nav.WorkspaceNavDeps]): the window title first, then the slots.
 *
 * **Why there is a [content] slot instead of the reading view's own ~70 parameters.** Every other
 * destination in these graphs calls a `*Screen` composable that lives in `:sharedUi` and takes
 * host-supplied lambdas. The reading view cannot: the composition it needs is
 * `ComposeReadingViewHost.ReadingViewContent` (`:app`, extracted from `mountComposeView` by this
 * same task), and that body reads `CommonUtils.settings` directly, hosts each pane's `BibleView`
 * WebView through `AndroidView`, and resolves `R.drawable` painters. None of that can cross into
 * `commonMain`, and moving it would mean restructuring `mountComposeView`, which the plan's Global
 * Constraints and design §10 put out of scope for this batch. A pre-built `@Composable` slot is the
 * established shape for exactly this situation in this codebase — `mountComposeView`'s own
 * `agentLogSlot`/`speakBarSlot`/`quickSheetSlot`, and `ChooseDocumentDeps.topBarActions` — so this
 * destination is one slot rather than seventy.
 *
 * **What this destination is NOT yet, at the commit that introduces it.** Nothing routes here:
 * `ScreenLauncher.MIGRATED` is Task 8's and `MainBibleActivity` is still the launcher. The host's
 * production [content] does not render the reading view either, and cannot until
 * `ComposeReadingViewHost` (130 `activity.` references to `MainBibleActivity`, including ~25
 * `compose*` toolbar entry points) and `DocumentViewManager` are re-typed off that Activity — work
 * no task in the slice-7 plan owns. See `NavHostComposeActivity.readingNavDeps`, where that gap is
 * an `error(...)` rather than a blank screen, and this task's report.
 */
class ReadingNavDeps(
    /**
     * The Activity that hosts this graph, as an opaque identity token (reading-host re-typing R7b).
     *
     * The destination registers itself with [ReadingViewVisibility] and [ReadingViewHostCallbacks]
     * under this token, and both resolve it through
     * [net.bible.sharedcore.reading.ReadingHostPresence] — so a destination that is still composed
     * under a BACKGROUNDED host counts as neither visible nor current. Without it, "registered"
     * meant "on screen", which is a dead back key on a classic secondary Activity and volume keys
     * dispatched into a reading view the user cannot see; that object's kdoc has the argument.
     *
     * **A deps field rather than a `CompositionLocal`**, because `rememberUpdatedState(deps)` below
     * already exists for exactly this class of staleness: a local would be a second way for the
     * destination to learn the same fact, readable from places the deps are not, and the two could
     * then disagree about which host is registering. `Any` rather than an Activity type because
     * `:sharedCore`/`:sharedUi` `commonMain` is Android-free; it is only ever compared by identity.
     */
    val host: Any,
    /**
     * The HOST WINDOW's title (Recents, TalkBack), classic `MainBibleActivity`'s
     * `android:label="@string/app_name_short"` (`AndroidManifest.xml:103`). Applied from a
     * `LaunchedEffect` keyed on the VALUE, the shape every other destination uses; the host
     * Activity's own manifest block carries no label at all, so without this the reading view's
     * window title would silently become the application label.
     */
    val windowTitle: String,
    /**
     * The reading view itself — see this class's kdoc for why it is one slot. Composed directly in
     * the destination arm, with no wrapper of any kind, so that what the destination renders is
     * byte-for-byte what `ComposeReadingViewHost.install` renders into the classic Activity.
     *
     * **Work this destination OWES, payable by the task that makes this slot real.** Three items,
     * collected here because this is the parameter that task has to touch and a code comment
     * somewhere else is not findable:
     *
     *  1. **`ExternalKeyboardBack` does not close the drawers.**
     *     `NavHostComposeActivity.readingViewKeyPressed` returns `true` for it and does nothing
     *     else; classic `MainBibleActivity.onKeyDown` closed BOTH the classic `binding.drawerLayout`
     *     and, via `composeCloseDrawerIfOpen()`, the Compose drawer. Neither is reachable from the
     *     host, because both live on the `ComposeReadingViewHost` this slot cannot build yet — so
     *     the close half lands with the slot. Dead today (nothing routes here); the day it is not
     *     dead, an external-keyboard BACK silently does nothing instead of closing the drawer.
     *  2. ~~`ReadingViewVisibility` keeps `HistoryManager.goBack()` from finishing the screen on
     *     top once a composed destination's depth is live under a backgrounded host — a dead back
     *     key.~~ **PAID by reading-host re-typing R7b**, which made both seams resolve through
     *     [net.bible.sharedcore.reading.ReadingHostPresence] and this class carry [host].
     *  3. ~~`ReadingViewHostCallbacks.current` is "last published", not "the foreground host".~~
     *     **PAID by R7b**, the same change: it was the same divergence and it wanted the same answer.
     *
     *  Items 2 and 3 were PRECONDITIONS, not follow-ups — both would have become user-visible the
     *  moment this slot rendered the real reading view, which is why they were paid first. Item 1 is
     *  still owed and still lands with this slot.
     */
    val content: @Composable () -> Unit,
    /**
     * Classic `MainBibleActivity.onKeyDown`'s body, minus the decoding: the volume-key gates
     * (`volume_keys_scroll`, `speakControl.isSpeaking`, `AudioManager.isMusicActive`) and the
     * `BibleView.volumeUpPressed()`/`volumeDownPressed()` calls, plus the external-keyboard BACK
     * branch that closes the drawer. Built host-side because every one of those is Android or
     * `:app`; published from this destination's effect because only the destination knows when it
     * is on screen. Returns whether the key was CONSUMED — see [ReadingViewHostHandlers.onKey].
     */
    val onKey: (ReadingViewKey) -> Boolean,
    /** Classic `MainBibleActivity.onScreenTurnedOn`, host-side. */
    val onScreenTurnedOn: () -> Unit,
    /** Classic `MainBibleActivity.onScreenTurnedOff`, host-side. */
    val onScreenTurnedOff: () -> Unit,
    /** Sets the host window's title — graph-wide, exactly as in the other clusters' deps. */
    val setWindowTitle: (String) -> Unit,
)

/**
 * The `reading` destination: what used to be `MainBibleActivity`'s composition, registered into the
 * app's single `NavHost` by the host Activity.
 *
 * The arm is deliberately tiny. Three things belong to the DESTINATION rather than to the content
 * it renders, and all three are the same fact stated once — "this reading view is on screen":
 *
 *  1. [ReadingViewVisibility], the predicate `HistoryManager.createHistoryItem` asks instead of
 *     `CurrentActivityHolder.currentActivity is MainBibleActivity` (design §5.1). It is the only
 *     producer of `KeyHistoryItem`, i.e. of the verse back-stack and of history persistence, so it
 *     has to be entered before anything the content hosts can post `AddHistoryItem` — which a
 *     `DisposableEffect` in the arm is, since effects run with the first composition. Both
 *     registrations below are made under [ReadingNavDeps.host], so neither of them says "on screen"
 *     while this destination's host is in the background (R7b).
 *  2. The two per-destination `ActivityBase` callback families ([ReadingViewHostCallbacks]): volume
 *     keys (and, by inversion, `enableGenericVolumeScroll`) and screen on/off.
 *  3. The host window's title.
 *
 * Both (1) and (2) are entered and left by ONE `DisposableEffect`, not two: they answer the same
 * question, and two effects could in principle be disposed in either order and leave the host
 * consulting a handler for a reading view History has already forgotten.
 *
 * [navController] is unused today and kept because every `*NavGraph` in this module takes it: this
 * destination's outbound navigation (the seven screens it opens, and the results it consumes) is
 * Task 9's, and adding the parameter then would change a signature the host already calls.
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.readingNavGraph(navController: NavHostController, deps: ReadingNavDeps) {
    composable(route = NavRoutes.READING) {
        // The effect below is keyed on Unit and so captures whatever it captures ONCE. Publishing
        // `deps.onKey` and friends straight into it would therefore pin the lambdas of the deps
        // instance that happened to be current at the first composition: safe only as long as the
        // host `remember`s its deps, which today's does but which nothing forces it to — a host
        // that writes `ReadingNavDeps(...)` inline, the obvious thing and what several other
        // clusters' call sites look like, would publish permanently stale handlers with no compile
        // error. rememberUpdatedState + delegating handlers make that impossible without giving up
        // the Unit key.
        val currentDeps = rememberUpdatedState(deps)
        // One effect for both seams — see this function's kdoc. Keyed on Unit: the destination is
        // argument-free, so there is nothing that could legitimately re-key it, and a re-key would
        // mean an exit/enter pair that History would see as the reading view briefly leaving.
        DisposableEffect(Unit) {
            // The HOST is read ONCE and the same token is used to register, to publish and to undo
            // both -- unlike the handler lambdas above, which delegate through `currentDeps` on
            // every call. It is an identity, and an enter/publish that was undone with a different
            // token than it was made with would leave this destination registered forever (and
            // un-register some other host's): exactly the unbalanced state `exit`'s kdoc refuses to
            // paper over. A host cannot change identity under a live composition anyway; if one
            // ever could, re-keying this effect on it -- not reading it twice -- is the fix.
            val host = currentDeps.value.host
            ReadingViewVisibility.enter(host)
            val unpublish = ReadingViewHostCallbacks.publish(
                ReadingViewHostHandlers(
                    onKey = { key -> currentDeps.value.onKey(key) },
                    onScreenTurnedOn = { currentDeps.value.onScreenTurnedOn() },
                    onScreenTurnedOff = { currentDeps.value.onScreenTurnedOff() },
                ),
                host = host,
            )
            onDispose {
                unpublish()
                ReadingViewVisibility.exit(host)
            }
        }

        LaunchedEffect(deps.windowTitle) { deps.setWindowTitle(deps.windowTitle) }

        deps.content()
    }
}
