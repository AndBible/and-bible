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
 * Event posted when the agent log panel's visibility changes. `MainBibleActivity` reads
 * `visible`/`height` off it into `agentLogVisible`/`agentLogHeight`, which `bottomOffset2` and
 * `bottomOffsetForWebView` add to the reading view's bottom reservation.
 *
 * Split out of `AgentLogWidget` by Batch Z-late's epilogue (spec 10.4) rather than deleted with it,
 * because the CONSUMER is live and on the Compose path -- see the offset properties above.
 *
 * Its POSTER, however, is not: the only `post` was `AgentLogWidget.notifyVisibilityChanged`, and
 * every call site of that was already gated on `classicBottomChromeAllowed`, so with a Compose host
 * mounted the event has not actually been posted since that gate landed. Deleting the widget
 * therefore changes no behaviour -- `agentLogVisible`/`agentLogHeight` were already permanently
 * `false`/`0` on this path -- but it does leave this event with a handler and no sender. That is the
 * SEAM the Compose agent-log panel has to fill when the reading view's bottom double-reservation is
 * addressed (the KNOWN GAP already tracked in `MainBibleActivity.updateBottomBars`); it is kept for
 * that, not because anything sends it today.
 *
 * @param visible Whether the panel is now visible
 * @param height The height of the panel (for offset calculation)
 */
class AgentLogVisibilityChanged(val visible: Boolean, val height: Int)
