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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the reading view is what the user is currently looking at.
 *
 * This replaces `CurrentActivityHolder.currentActivity is MainBibleActivity`, which was the only
 * path in `HistoryManager` that produced a `KeyHistoryItem` and therefore the only source of the
 * verse back-stack and of history persistence. See the slice-7 design spec 5.1.
 *
 * **The swap is not behaviour-neutral by itself — it is made so by WHERE the flag is set.** During
 * the migration a reading view can be either of two things, so the state has TWO INDEPENDENT
 * INPUTS and [isVisible] is their OR:
 *
 *  - [enter]/[exit], a DEPTH COUNTER owned by the `reading` destination's `DisposableEffect`
 *    (`ReadingNavGraph.kt`) — the permanent one, entered when the destination composes and exited
 *    when it is disposed;
 *  - [setActivityVisible], a plain boolean driven by classic `MainBibleActivity`'s lifecycle
 *    (`onCreate`, `onResume`, `onPause`, `onActivityResult`) — **temporary**, and removed by
 *    whatever task makes the reading destination's `content` slot render the real reading view.
 *    Until then `MainBibleActivity` is still the launcher and still the only reading view the user
 *    can reach, so without this input nothing would set the flag on the live path at all:
 *    `KeyHistoryItem` would never be created (no verse back-stack, no history persistence) and
 *    `HistoryManager.goBack`'s `if (!isVisible) finish()` would fire on every back-with-history.
 *
 * They are orthogonal on purpose: neither can clear the other. A destination that is disposed while
 * classic's Activity is resumed leaves the flag true, and an Activity that pauses while a reading
 * destination is composed leaves it true — which is the correct answer in both cases, because a
 * reading view really is on screen. Mixing them into one counter would let a `MULTIPLE_TASK`
 * second instance, or a lifecycle callback arriving out of order, turn the flag off under a reading
 * view that is still there.
 *
 * What is genuinely equivalent to the old predicate is the part that matters: a **sheet** over the
 * reading view (search, key chooser, text settings, Speak) changes neither the Activity (before)
 * nor the destination (after), so the predicate stays true; a **screen** over it changes both, so
 * it goes false. A sheet is NOT a destination — do not set this false when opening one.
 *
 * **What the old predicate covered and the DESTINATION input does not, measured rather than
 * assumed.** The old check was true from `ActivityBase.onCreate`'s first line
 * (`CurrentActivityHolder.activate(this)`) until `onStop`'s `deactivate` — so it spanned a
 * backgrounded-but-not-stopped reading view, and went false once the app was stopped. A
 * composition-scoped effect is neither of those things: it is "while the reading destination is the
 * current destination", and it stays entered while the host is in the background, because
 * navigation-compose does not dispose the current entry's content when the Activity stops. Two
 * consequences, both deliberate:
 *
 *  - The `onCreate` window Task 3's kdoc was worried about is closed for good ON THE DESTINATION
 *    PATH: the destination's effect runs with its first composition, before anything it hosts can
 *    post `AddHistoryItem`, so the "wrong `IntentHistoryItem` recorded for a deep link" failure mode
 *    cannot come back there. (On the Activity path it is still the `onCreate` setter that closes
 *    it — which is why that setter exists and is not folded into `onResume`.)
 *  - The divergence moves to the other end: an `AddHistoryItem` posted while the app is in the
 *    BACKGROUND with the reading destination current now records a `KeyHistoryItem`, where the old
 *    predicate (after `onStop`) recorded none. Task 3's kdoc described the effect as "pause-like";
 *    measured, it is not. The failure mode is one history item too many, never a wrong one, which
 *    is the direction this seam is allowed to err in — and a lifecycle-aware effect
 *    (`LifecycleStartEffect`) would trade it for the `onCreate`-window defect above, which is the
 *    worse of the two.
 *
 * **Why the destination input is a depth counter, not a boolean** (Task 3's carried finding, done
 * in Task 6): `StartupActivity`'s `FLAG_ACTIVITY_MULTIPLE_TASK` can make a second reading instance
 * real, and with a process-wide boolean the instance that left would clear the flag for the one
 * still on screen. [enter] and [exit] are balanced by the destination's own `DisposableEffect`.
 * All calls are on the main thread (a composition effect, a lifecycle callback, or a test), so the
 * counter is plain.
 */
object ReadingViewVisibility {
    private var depth = 0
    private var activityVisible = false
    private val _isVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** `depth > 0 || activityVisible` — see the class kdoc for why those are two inputs, not one. */
    val isVisible: Boolean get() = _isVisible.value

    /** A reading DESTINATION became visible. Paired with exactly one [exit]. */
    fun enter() {
        depth += 1
        publish()
    }

    /**
     * A reading destination went away. Never drives the depth below zero: an unbalanced [exit] is a
     * bug in the caller, and turning it into a negative depth would hide it behind a flag that can
     * no longer be turned on. Does NOT touch [setActivityVisible]'s input.
     */
    fun exit() {
        if (depth > 0) depth -= 1
        publish()
    }

    /**
     * The classic-Activity input: `MainBibleActivity`'s lifecycle says whether ITS reading view is
     * on screen. Independent of [enter]/[exit] — a `false` here cannot clear a composed
     * destination's depth, and a destination's `exit()` cannot clear a resumed Activity.
     *
     * **TEMPORARY**, together with its four call sites in `MainBibleActivity`: it exists only while
     * the reading destination's `content` slot cannot render the real reading view
     * (`ComposeReadingViewHost` is constructed with a `MainBibleActivity`), so the Activity is
     * still the only live reading view. It goes away with the task that makes that slot real.
     */
    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
        publish()
    }

    /**
     * Force the whole state, ignoring both inputs. The only callers are TESTS that need a known
     * starting state (or that drive the predicate directly instead of composing the destination);
     * production code goes through [enter]/[exit] and [setActivityVisible]. `true` sets the depth to
     * exactly 1, so a following [exit] still lands on false; either value clears the Activity input,
     * so `setVisible(false)` really is a full reset rather than "false unless some earlier test left
     * the Activity input on".
     */
    fun setVisible(visible: Boolean) {
        depth = if (visible) 1 else 0
        activityVisible = false
        publish()
    }

    private fun publish() {
        _isVisible.value = depth > 0 || activityVisible
    }
}
