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

package net.bible.android.view.activity.nav

/**
 * One branch of the reading view's BACK chain, as data.
 *
 * Classic `MainBibleActivity.onBackPressed` (`MainBibleActivity.kt:761-819`) is six branches in one
 * method. When the Compose host became the launcher (reading-host re-typing T8b) it inherited
 * `ActivityBase`'s two-line `onBackPressed` instead, and five of those six behaviours disappeared with
 * nothing to notice -- finding F55, the most user-visible defect of the 2026-09-18 device walk, where
 * BACK exited the app instead of walking the reading history.
 *
 * Expressing the chain as an ordered list rather than nested `if`s is the repair for the *class* of
 * defect: the list is enumerable, so `ReadingHostBackChainTest` can assert both that each step consumes
 * its own press and that the steps below it did not run, and a future reader can line it up against
 * classic's six branches without reading a method body.
 *
 * @param name for logging and for the test's failure messages -- never parsed.
 * @param clearsExitWarning whether consuming this step resets the "press back again to close the app"
 *   timer. Classic clears it only after the WebView-modal and history branches; its drawer, search and
 *   fullscreen branches `return` before reaching that assignment, leaving the timer standing. Modelled
 *   explicitly rather than reproduced by control flow, because it is the kind of detail a rewrite
 *   silently drops.
 * @param consume runs the step. `true` means the press was consumed and the chain stops here.
 */
class BackStep(
    val name: String,
    val clearsExitWarning: Boolean = false,
    private val consume: () -> Boolean,
) {
    fun run(): Boolean = consume()
}
