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


package net.bible.android.view.util.widget

/**
 * Posted when the Speak transport should be hidden.
 *
 * Promoted from a nested class of `SpeakTransportWidget` by Batch Z-late's epilogue (spec 10.4):
 * it is posted and consumed entirely on the Compose path -- `SpeakTransportServiceImpl.stop()`
 * posts it when speech is already stopped, that same class's own `onMain` handler clears the
 * Compose bar's `visible` state, and `MainBibleActivity` drops `transportBarVisible` on it -- while
 * the classic widget that used to host it is deleted in this commit.
 */
class HideTransportEvent
