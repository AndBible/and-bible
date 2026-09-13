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
 * **The swap is not behaviour-neutral by itself — it is made so by WHERE the flag is set.** Since
 * nav-graph slice 7 Task 6 that place is ONE place: the `reading` destination's `DisposableEffect`
 * (`ReadingNavGraph.kt`), entered when the destination composes and exited when it is disposed.
 * The four temporary `setVisible` calls Task 3 put in `MainBibleActivity` (`onCreate`, `onResume`,
 * `onPause`, `onActivityResult`) are gone with it — a test asserts they are, because leaving one
 * behind would mean two owners for one flag.
 *
 * What is genuinely equivalent is the part that matters: a **sheet** over the reading view (search,
 * key chooser, text settings, Speak) changes neither the Activity (before) nor the destination
 * (after), so the predicate stays true; a **screen** over it changes both, so it goes false. A
 * sheet is NOT a destination — do not set this false when opening one.
 *
 * **What the old predicate covered and this does not, measured rather than assumed.** The old check
 * was true from `ActivityBase.onCreate`'s first line (`CurrentActivityHolder.activate(this)`) until
 * `onStop`'s `deactivate` — so it spanned a backgrounded-but-not-stopped reading view, and went
 * false once the app was stopped. A composition-scoped effect is neither of those things: it is
 * "while the reading destination is the current destination", and it stays entered while the host
 * is in the background, because navigation-compose does not dispose the current entry's content
 * when the Activity stops. Two consequences, both deliberate:
 *
 *  - The `onCreate` window Task 3's kdoc was worried about is closed for good: the destination's
 *    effect runs with its first composition, before anything it hosts can post `AddHistoryItem`, so
 *    the "wrong `IntentHistoryItem` recorded for a deep link" failure mode cannot come back.
 *  - The divergence moves to the other end: an `AddHistoryItem` posted while the app is in the
 *    BACKGROUND with the reading destination current now records a `KeyHistoryItem`, where the old
 *    predicate (after `onStop`) recorded none. Task 3's kdoc described the effect as "pause-like";
 *    measured, it is not. The failure mode is one history item too many, never a wrong one, which
 *    is the direction this seam is allowed to err in — and a lifecycle-aware effect
 *    (`LifecycleStartEffect`) would trade it for the `onCreate`-window defect above, which is the
 *    worse of the two.
 *
 * **A depth counter, not a boolean** (Task 3's carried finding, done in Task 6): `StartupActivity`'s
 * `FLAG_ACTIVITY_MULTIPLE_TASK` can make a second reading instance real, and with a process-wide
 * boolean the instance that left would clear the flag for the one still on screen. [enter] and
 * [exit] are balanced by the destination's own `DisposableEffect`, and [isVisible] is `depth > 0`.
 * All calls are on the main thread (a composition effect, or a test), so the counter is plain.
 */
object ReadingViewVisibility {
    private var depth = 0
    private val _isVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _isVisible.asStateFlow()
    val isVisible: Boolean get() = _isVisible.value

    /** A reading view became visible. Paired with exactly one [exit]. */
    fun enter() {
        depth += 1
        publish()
    }

    /**
     * A reading view went away. Never drives the depth below zero: an unbalanced [exit] is a bug in
     * the caller, and turning it into a negative depth would hide it behind a flag that can no
     * longer be turned on.
     */
    fun exit() {
        if (depth > 0) depth -= 1
        publish()
    }

    /**
     * Force the flag, ignoring the counter. The only callers are TESTS that need a known starting
     * state (or that drive the predicate directly instead of composing the destination); production
     * code goes through [enter]/[exit]. `true` sets the depth to exactly 1, so a following [exit]
     * still lands on false.
     */
    fun setVisible(visible: Boolean) {
        depth = if (visible) 1 else 0
        publish()
    }

    private fun publish() {
        _isVisible.value = depth > 0
    }
}
