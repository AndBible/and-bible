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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A zero [MotionDurationScale]: an animation run in a coroutine whose context holds it (`SheetState.hide()`,
 * `Animatable.animateTo`, ...) ends on its first frame. For code that drives an animation from its own coroutine while
 * "Disable animations" is on, on hosts whose recomposer carries no such scale (iOS, desktop; osTicket 3499). On
 * Android the app's [AppMotionDurationScale] is already 0 then, so adding this changes nothing there.
 */
internal object NoMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float get() = 0f
}

/** [NoMotionDurationScale] when [disableAnimations], else nothing: `withContext(noMotionIf(...)) { sheet.expand() }`. */
internal fun noMotionIf(disableAnimations: Boolean): CoroutineContext =
    if (disableAnimations) NoMotionDurationScale else EmptyCoroutineContext
