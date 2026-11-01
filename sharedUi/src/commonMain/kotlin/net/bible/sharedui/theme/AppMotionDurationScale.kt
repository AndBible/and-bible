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

package net.bible.sharedui.theme

import androidx.compose.ui.MotionDurationScale

/**
 * The [MotionDurationScale] a Compose root's recomposer carries so the app's "Disable animations" setting
 * (`disable_animations`) reaches every Compose animation at once (osTicket 3499).
 *
 * Compose animations (`animate*AsState`, `Animatable`, `AnimatedVisibility`/`Transition`, `SheetState.show()/hide()`,
 * the sheet scrim, ...) read the duration scale from the coroutine context they run in, and that context is the
 * recomposer's effect context for anything launched from `LaunchedEffect` or `rememberCoroutineScope`; dialogs, popups
 * and bottom-sheet windows inherit it from their parent composition. A scale of `0` makes every such animation end on
 * its next frame, which is also what the platforms' own "remove animations" settings do, so code that awaits an
 * animation (hide-then-dismiss) still resumes, one frame later.
 *
 * Both inputs are read on every query, so a changed setting applies to the next animation without rebuilding any
 * window, and a failing settings read never breaks an animation (it counts as "animations on").
 *
 * @param animationsDisabled the live value of the app setting.
 * @param systemScale the platform's own scale, used while the app setting is off (Android: the developer-option
 *   "Animator duration scale"; 1 where the platform has none).
 */
class AppMotionDurationScale(
    private val animationsDisabled: () -> Boolean,
    private val systemScale: () -> Float = { 1f },
) : MotionDurationScale {
    override val scaleFactor: Float
        get() = if (runCatching(animationsDisabled).getOrDefault(false)) 0f else systemScale()
}
