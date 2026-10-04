/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import android.content.res.Configuration
import androidx.core.graphics.Insets

/**
 * The reading host's bus events, formerly nested in `MainBibleActivity` (deleted in slice 8). Same names, so
 * every poster and subscriber reads the same; renaming them is left to the tail sweep (spec §2).
 */
class SpeakTransportVisibilityChanged(val value: Boolean)

class SystemInsetsChangedEvent(val insets: Insets)

class KeyIsNull: Exception()

class AgentLogOffsetsUpdated

/** See [updateSearchSheetOffsets]. */
class SearchSheetOffsetsUpdated

/** See [onComposeSearchFieldFocusChanged]. */
class ImePaddingChanged

class FullScreenEvent(val isFullScreen: Boolean)

class UpdateRestoreWindowButtons

class ConfigurationChanged(val configuration: Configuration)

class MainBibleAfterRestore

class UpdateMainBibleActivityDocuments
