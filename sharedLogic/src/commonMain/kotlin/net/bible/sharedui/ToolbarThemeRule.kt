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
package net.bible.sharedui

/**
 * The experimental switch that keeps the reading toolbar's LITERAL workspace colour instead of
 * the theme-derived one (§6). Derived is the default (maintainer decision, batch 4b feedback
 * 2026-08-01; made unconditional 2026-09-18 when the former master switch,
 * `workspace_color_theme`, was retired -- see `docs/superpowers/status/compose-port-status.md`); this is the opt-out
 * back to the literal colour, not an opt-in to deriving it.
 */
const val TOOLBAR_LITERAL_COLOR_FEATURE = "toolbar_literal_color"

/**
 * The reading toolbar's colour rule, as a pure function so it can be unit-tested without Koin, a
 * Context or [net.bible.android.control.page.toolbar.ToolbarStateServiceImpl]'s four collaborators
 * (A/B batch 4b feedback, 2026-08-01). Derived is the DEFAULT (unconditionally since the
 * 2026-09-18 master-switch retirement); [TOOLBAR_LITERAL_COLOR_FEATURE] is the one remaining
 * opt-OUT back to the literal workspace colour, not an opt-in to deriving it.
 */
fun deriveToolbarFromTheme(enabledFeatures: Set<String>): Boolean =
    TOOLBAR_LITERAL_COLOR_FEATURE !in enabledFeatures
