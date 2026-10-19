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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Calls [onChange] whenever one of [keys] is written. [changes] is `SettingsStore.changes`, the key of every write the
 * database-backed `AppSettings` makes (iOS stores display_color_mode, disable_animations and screen_keep_on_pref there,
 * not in UserDefaults, so no platform notification announces them). Collection starts eagerly on [scope].
 */
@kotlin.native.HiddenFromObjC
fun watchSettingKeys(changes: Flow<String>, keys: Set<String>, scope: CoroutineScope, onChange: () -> Unit): Job =
    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
        changes.collect { if (it in keys) onChange() }
    }
