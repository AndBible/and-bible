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

package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons

/**
 * Toolbar fixtures shared by every golden test that renders a [net.bible.sharedui.reading.ReadingToolbar].
 *
 * Lifted verbatim out of `ReadingToolbarGoldenTest` (where they were private) when
 * `ReadingToolbarSearchGoldenTest` needed the same two values — duplicating them would let the two
 * classes' goldens drift apart on an icon change, which is exactly the kind of difference a golden
 * is supposed to be trusted to report.
 */

/**
 * The same drawables `main_bible_view.xml`'s toolbarLayout buttons use (homeButton/searchButton/
 * speakButton/strongsButton/bibleButton/commentaryButton/workspaceButton/optionsMenu).
 */
@Composable
internal fun goldenToolbarIcons() = ReadingToolbarIcons(
    home = painterResource(R.drawable.ic_menu),
    search = painterResource(R.drawable.ic_search_24dp),
    speak = painterResource(R.drawable.ic_baseline_headphones_24),
    strongs = painterResource(R.drawable.ic_strongs_hebrew),
    bible = painterResource(R.drawable.ic_bible_24dp),
    commentary = painterResource(R.drawable.ic_commentary),
    workspace = painterResource(R.drawable.ic_workspace_solid_24dp),
    overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
    sync = painterResource(R.drawable.ic_syncdb_24dp),
)

/** Every toolbar callback a no-op — a golden never clicks anything. */
internal fun goldenToolbarCallbacks() = ReadingToolbarCallbacks(
    onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
    onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
    onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
    onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
)
