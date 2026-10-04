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

package net.bible.sharedcore.window

/**
 * Whether a per-pane window button should draw its pin indicator — the port of classic
 * `WindowButtonWidget.kt:86-95`'s
 * `!isRestoreButton && !workspaceSettings.autoPin && window.isPinMode && !isMaximised`.
 *
 * The `!autoPin` term is the non-obvious one, and it is not cosmetic. `Window.isPinMode`
 * (`control/page/window/Window.kt:181-182`) returns `true` for EVERY window once the workspace's
 * auto-pin setting is on, so without this term the indicator appears on every button at once and
 * therefore distinguishes nothing. Classic suppressed it for exactly that reason.
 *
 * `!isRestoreButton` has no parameter here: the restore rail draws no pin indicator at all, which
 * `WindowButton` already expresses as a `mode == WindowButtonMode.Pane` gate at the drawing site.
 * Restating it here would give two places to disagree.
 */
fun shouldShowPinIndicator(isPinMode: Boolean, autoPin: Boolean, isMaximised: Boolean): Boolean =
    isPinMode && !autoPin && !isMaximised
