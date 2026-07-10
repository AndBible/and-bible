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

package net.bible.sharedui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

/**
 * Wraps [content] in a Material3 pull-to-refresh container. Plan B owns the real refresh wiring.
 *
 * Under inspection (Compose previews / Roborazzi golden tests) the refreshing spinner is swapped
 * for a frozen determinate circular frame. This mirrors [AbLoadingIndicator]: Roborazzi's
 * `inspectionMode(true)` sets `LocalInspectionMode` but does NOT freeze the Material3 pull-refresh
 * indicator's animation clock, so the real indeterminate spinner captures at an arbitrary rotation
 * phase → non-deterministic golden verify (record != verify). `LocalInspectionMode` is FALSE in
 * production, so this branch is inert at runtime (zero behavior change).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbPullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    if (LocalInspectionMode.current) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = state,
            indicator = {
                if (isRefreshing) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                    ) {
                        CircularProgressIndicator(
                            progress = { 0.75f },
                            modifier = Modifier.padding(6.dp).size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            },
        ) { content() }
    } else {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, state = state) { content() }
    }
}
