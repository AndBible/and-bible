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

package net.bible.sharedui.reading.host

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * How a platform hosts ONE reading pane (the Bible text of one window): Android puts the window's
 * cached `BibleView` in an `AndroidView`, iOS will put a `WKWebView` in a `UIKitView`. [ReadingHost]
 * only decides WHERE a pane goes and how big it is ([modifier]); what fills it is this factory's job.
 */
typealias ReadingPaneFactory = @Composable (windowId: String, modifier: Modifier) -> Unit

/** Test tag prefix of [LocalReadingPane]'s default; the full tag is `reading-pane-placeholder-<windowId>`. */
@kotlin.native.HiddenFromObjC
const val ReadingPanePlaceholderTagPrefix = "reading-pane-placeholder-"

/**
 * The pane factory for the current composition. The default is a tagged, empty placeholder so a host
 * that forgot to provide one shows an empty pane (findable in a test) rather than crashing.
 */
@kotlin.native.HiddenFromObjC
val LocalReadingPane = staticCompositionLocalOf<ReadingPaneFactory> {
    { windowId, modifier -> Box(modifier.testTag(ReadingPanePlaceholderTagPrefix + windowId)) }
}
