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
 * verse back-stack and of history persistence. The swap is behaviour-neutral: a sheet over the
 * reading view changes neither the Activity (before) nor the destination (after), and a screen over
 * it changes both. See the slice-7 design spec 5.1.
 *
 * A sheet is NOT a destination — do not set this false when opening one.
 */
object ReadingViewVisibility {
    private val _isVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _isVisible.asStateFlow()
    val isVisible: Boolean get() = _isVisible.value
    fun setVisible(visible: Boolean) { _isVisible.value = visible }
}
