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

package net.bible.sharedui.host.ports

import androidx.compose.runtime.Composable
import net.bible.sharedui.app.download.CloudDocumentsHostPlatform
import net.bible.sharedui.app.download.CloudDocumentsOverflowItem
import androidx.compose.material3.Icon
import net.bible.sharedui.icons.AbIcons
import org.jetbrains.compose.resources.painterResource

/** Cloud document actions use the shared, theme-tinted vectors. */
@kotlin.native.HiddenFromObjC
class IosCloudDocumentsHostPlatform : CloudDocumentsHostPlatform {
    @Composable
    override fun OverflowIcon(item: CloudDocumentsOverflowItem) {
        val key = when (item) {
            CloudDocumentsOverflowItem.SYNC_NOW -> "ic_sync_white_24dp"
            CloudDocumentsOverflowItem.RESCAN -> "ic_baseline_refresh_24"
            CloudDocumentsOverflowItem.HELP -> "ic_help_white_24dp"
        }
        Icon(painterResource(AbIcons.cloudDocumentsOverflowIconResources.getValue(key)), contentDescription = null)
    }
}
