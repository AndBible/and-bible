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

/**
 * Whether closing the navigation drawer should hand focus back to the active pane's `BibleView`
 * (classic `DrawerLayout.onDrawerClosed` parity).
 *
 * It must NOT when the reading search bar is open. Round 12b §1: the drawer's "Search" row starts
 * search and closes the drawer in the same click, so the search field takes focus and raises the
 * keyboard immediately — and then, ~250-350ms later when the close animation settles,
 * `onDrawerClosed` used to call `activeWindow.bibleView.requestFocus()` unconditionally, pulling
 * focus off the field and dismissing the keyboard. That is the "keyboard flashes and vanishes"
 * report; the toolbar entry point never involved the drawer, which is why only one path was broken.
 *
 * The input is the search bar's OPEN state, deliberately not the field's focus state: open-ness is
 * true the instant the controller enters search mode, whereas whether the field has actually taken
 * focus by the time the drawer settles depends on frame timing — a focus-based guard would be a
 * race that silently fails on a slow frame.
 */
fun shouldRestorePaneFocusOnDrawerClose(searchBarOpen: Boolean): Boolean = !searchBarOpen
