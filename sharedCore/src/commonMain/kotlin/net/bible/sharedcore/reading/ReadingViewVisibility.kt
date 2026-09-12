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
 * **The swap is not behaviour-neutral by itself — it is made so by WHERE the flag is set.** The old
 * predicate did not track "resumed": `ActivityBase.onCreate`'s first line is
 * `CurrentActivityHolder.activate(this)` and the matching `deactivate` is in `onStop`, so the class
 * check was true from the start of `onCreate` until the reading Activity was stopped or another
 * Activity was created on top of it. That is why `MainBibleActivity` sets this flag in FOUR places
 * while the reading view is still an Activity — `onCreate` (deep links posted from `openLink` land
 * there, before `onResume`), `onResume`, `onPause`, and `onActivityResult` (chooser results are
 * delivered before `onResume`) — and not in `onResume`/`onPause` alone. Getting `onCreate` wrong is
 * worse than losing an item: `createHistoryItem` falls through to its
 * `currentActivity is AndBibleActivity` arm, and `MainBibleActivity` is one, so a WRONG
 * `IntentHistoryItem` is recorded instead.
 *
 * What is genuinely equivalent, once those four call sites are in place, is the part that matters:
 * a **sheet** over the reading view (search, key chooser, text settings, Speak) changes neither the
 * Activity (before) nor the destination (after), so the predicate stays true; a **screen** over it
 * changes both, so it goes false. A sheet is NOT a destination — do not set this false when opening
 * one.
 *
 * The one state where old and new still differ is the reverse of the `onCreate` gap: between
 * `onPause` and the next Activity's `onCreate` the class check was still true while this flag is
 * already false. Nothing posts `AddHistoryItem` there, and `goBack()` cannot run there (it is only
 * reached from a resumed Activity's callbacks), so it is documented rather than papered over.
 *
 * TODO(Task 6): when the setter moves into the reading destination's `DisposableEffect`, make this
 * a depth counter (`enter()`/`exit()`) rather than a boolean — `FLAG_ACTIVITY_MULTIPLE_TASK` can
 * make a second reading instance real, and then one instance's exit would clear the flag while the
 * other is still on screen.
 */
object ReadingViewVisibility {
    private val _isVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _isVisible.asStateFlow()
    val isVisible: Boolean get() = _isVisible.value
    fun setVisible(visible: Boolean) { _isVisible.value = visible }
}
