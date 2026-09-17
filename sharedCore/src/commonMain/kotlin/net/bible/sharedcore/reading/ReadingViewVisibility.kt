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
package net.bible.sharedcore.reading

/**
 * Whether the reading view is what the user is currently looking at.
 *
 * This replaces `CurrentActivityHolder.currentActivity is MainBibleActivity`, which was the only
 * path in `HistoryManager` that produced a `KeyHistoryItem` and therefore the only source of the
 * verse back-stack and of history persistence. See the slice-7 design spec 5.1.
 *
 * **The rule, in one sentence (reading-host re-typing R7b, spec §3.5):** a reading view is visible
 * when it is registered by a host that [ReadingHostPresence] says is FOREGROUND. Everything below
 * is a consequence of that sentence.
 *
 * Two kinds of registration, both keyed by their host's token:
 *
 *  - [enter]/[exit], the DESTINATION input, owned by the `reading` destination's `DisposableEffect`
 *    (`ReadingNavGraph.kt`) — the permanent one, entered when the destination composes and exited
 *    when it is disposed. Its host is `ReadingNavDeps.host`, i.e. the Activity that hosts the graph.
 *  - [setActivityVisible], the ACTIVITY input — classic `MainBibleActivity`'s whole lifecycle
 *    (`onCreate`, `onResume`, `onPause`, `onActivityResult`), because its reading view is an
 *    Activity and never a destination; and, on `NavHostComposeActivity`, ONLY the bootstrap window
 *    before its first composition (see "the bootstrap bridge" below).
 *
 * **Why the foreground gate, and what it fixed.** Both inputs are written by the reading view about
 * ITSELF, and a composition-scoped effect stays entered while its host Activity is in the
 * background — navigation-compose does not dispose the current entry's content when the Activity
 * stops. So "registered" used to mean "visible", and with a classic secondary Activity over a
 * backgrounded host the flag stayed true: `HistoryManager.goBack()`'s
 * `if (!isVisible) currentActivity?.finish()` never fired and the user got a DEAD BACK KEY
 * (`ActivityBase.onBackPressed` returns without `super` when `goBack()` returned true). The same
 * state also recorded a `KeyHistoryItem` for an `AddHistoryItem` posted while the app was in the
 * background. [ReadingHostPresence]'s kdoc has the full argument, including why the Activity input
 * and the destination input could not simply be ANDed with a lifecycle observer each: they would be
 * two facts that can disagree, and this seam had that bug once already.
 *
 * **Why [isVisible] is COMPUTED and no longer cached.** The foreground can change without any
 * method of this object being called — another host resumes, this one pauses — so a value published
 * by [enter]/[exit]/[setActivityVisible] would go stale between calls, silently and in the
 * dangerous direction. Every reader of this seam (`HistoryManager.createHistoryItem`,
 * `HistoryManager.goBack`) asks the question at the instant it needs the answer, so a getter is all
 * that was ever needed. (Task 6 published a `StateFlow` here too; nothing collected it and fix
 * round 2 dropped it. Add one back the day something actually reacts to a change — and give it
 * `ReadingHostPresence` as an input if you do.)
 *
 * **The bootstrap bridge, and how `NavHostComposeActivity` stays balanced.** That host calls
 * `setActivityVisible(this, true)` as the first statement of `bootstrapIfNeeded()`, before the
 * `openLink` deep link posts a synchronous `AddHistoryItem` (see that method's kdoc and
 * `ReadingAppBootstrap`); the destination's effect cannot cover that window, because an effect
 * inside the graph necessarily runs after `setContent`. The bridge is RETIRED — not shadowed — at
 * whichever comes first of:
 *
 *  - [enter] for the same host: the destination and the bridge are not two reading views, they are
 *    the same one before and after its first composition. Retiring rather than shadowing is what
 *    makes navigating from `reading` to another destination of the same host turn the flag OFF; a
 *    bridge that merely stopped counting while a destination was entered would come back the moment
 *    that destination was disposed, leaving the flag true on a Download screen.
 *  - the host's `onPause`, which calls `setActivityVisible(this, false)` — so the bridge is also
 *    retired on a host that is backgrounded before it ever composes.
 *
 * `MainBibleActivity` is unaffected by that rule: it hosts no `reading` destination, so nothing ever
 * calls [enter] with its token.
 *
 * **What is genuinely equivalent to the old predicate.** A **sheet** over the reading view (search,
 * key chooser, text settings, Speak) changes neither the Activity, the destination, nor the
 * foreground host, so the predicate stays true; a **screen** over it changes the destination (a
 * sibling route) or the foreground (a secondary Activity), so it goes false. A sheet is NOT a
 * destination — do not set this false when opening one.
 *
 * **The one remaining divergence from the old predicate**, unchanged by R7b and harmless: the old
 * check went false at `onStop` (`CurrentActivityHolder.deactivate`), this one at `onPause`. See
 * `HistoryManager.goBack`, which argues why no caller can run in that window.
 *
 * **Why the registrations are MULTISETS rather than booleans** (Task 3's carried finding):
 * `StartupActivity`'s `FLAG_ACTIVITY_MULTIPLE_TASK` can make a second reading instance real. The
 * host token now keeps those two instances apart by itself, but the counting still matters within
 * one host: [enter] and [exit] are balanced by the destination's own `DisposableEffect`, and an
 * unbalanced [exit] must not be able to un-register a registration that is still live. All calls
 * are on the main thread (a composition effect, a lifecycle callback, or a test), so the lists are
 * plain.
 */
object ReadingViewVisibility {
    /** One entry per live `enter(host)`, by host token — a multiset, hence a list. */
    private val destinations = mutableListOf<Any>()

    /** The hosts whose ACTIVITY input is currently on. At most one entry per host. */
    private val activityHosts = mutableListOf<Any>()

    /** [setVisible]'s forcing state — tests only; see that function. */
    private var forced = false

    /**
     * Computed, never cached: is any registered reading view's host the FOREGROUND one? See the
     * class kdoc for why a cached value would go stale in the dangerous direction.
     */
    val isVisible: Boolean
        get() = forced ||
            destinations.any { ReadingHostPresence.isForeground(it) } ||
            activityHosts.any { ReadingHostPresence.isForeground(it) }

    /**
     * A reading DESTINATION hosted by [host] became visible. Paired with exactly one [exit] for the
     * same host. Also retires [host]'s bootstrap bridge — see the class kdoc.
     */
    fun enter(host: Any) {
        destinations.add(host)
        activityHosts.removeAll { it === host }
    }

    /**
     * A reading destination hosted by [host] went away. Removes ONE registration, never less than
     * none: an unbalanced [exit] is a bug in the caller, and letting it remove a registration that
     * does not exist would hide that bug behind a flag that can no longer be turned on. Does NOT
     * touch [setActivityVisible]'s input, nor any other host's registrations.
     */
    fun exit(host: Any) {
        val i = destinations.indexOfLast { it === host }
        if (i >= 0) destinations.removeAt(i)
    }

    /**
     * The ACTIVITY input for [host]: its reading view is on screen without a composed destination.
     *
     * `MainBibleActivity`'s four lifecycle call sites drive it for as long as that Activity IS the
     * reading view; `NavHostComposeActivity` uses it only as the bootstrap bridge described in the
     * class kdoc. **Temporary in the first sense, not the second:** the four classic call sites go
     * away with `MainBibleActivity` itself, the bridge stays as long as `bootstrapIfNeeded()` has to
     * run before the graph composes.
     */
    fun setActivityVisible(host: Any, visible: Boolean) {
        activityHosts.removeAll { it === host }
        if (visible) activityHosts.add(host)
    }

    /**
     * Force the answer, ignoring every registration. The only callers are TESTS that need a known
     * starting state (or that drive the predicate directly instead of composing a destination and
     * declaring a foreground host); production code goes through [enter]/[exit] and
     * [setActivityVisible].
     *
     * Either value first CLEARS both inputs, so `setVisible(false)` really is a full reset rather
     * than "false unless some earlier test left a registration behind". It does not touch
     * [ReadingHostPresence] — a stale foreground token with no registrations cannot make anything
     * visible — so a test that declares a foreground host resets that itself.
     */
    fun setVisible(visible: Boolean) {
        destinations.clear()
        activityHosts.clear()
        forced = visible
    }
}
