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

package net.bible.sharedui.speak

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedui.components.AbHelpMenuIcon
import net.bible.sharedui.components.AbSettingsCategoryHeader
import net.bible.sharedui.components.AbSettingsRow
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Advanced (rarely-changed) Speak settings — the `Advanced` page of the Speak bottom sheet.
 *
 * The page's TITLE is "Advanced settings" (`speakAdvancedSettings`), supplied by the sheet shell.
 * `speakSettingsTitle` ("General Speak Settings") appears here exactly once, as the first section
 * header — before round 13a it was rendered BOTH as the screen title and as this header, which is
 * what made the window look mis-titled.
 *
 * The last row is Help ([onHelp]), which shows the auto-bookmarking help dialog the deleted
 * `SpeakSettingsComposeActivity.showHelp()` owned (`speak_help_auto_bookmark` +
 * `speak_help_playback_settings*` and the tutorial-video link). Spec §5 keeps that dialog a platform
 * `AlertDialog` — it carries an HTML hyperlink — and moves its ownership to the reading-view host, so
 * this row is a seam, not a dialog. It explains the two least self-evident switches on this page, and
 * without it that text would only be reachable from the classic screen Batch Z deletes.
 */
@Composable
fun AdvancedSpeakSettingsContent(
    advanced: AdvancedSpeakVd,
    onSynchronize: (Boolean) -> Unit,
    onReplaceDivineName: (Boolean) -> Unit,
    onAutoBookmark: (Boolean) -> Unit,
    onRestoreSettingsFromBookmarks: (Boolean) -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    /** Hoistable scroll state (round 14b §7.b) — see [SpeakSettingsContent]'s own parameter for why
     *  it is defaulted. The Speak sheet passes its per-page state; the golden does not. */
    scrollState: ScrollState = rememberScrollState(),
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth().verticalScroll(scrollState)) {
        AbSettingsCategoryHeader(strings.speakSettingsTitle)
        AbSwitchRow(
            strings.confSpeakSynchronize, advanced.synchronize, onSynchronize,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
        )
        AbSwitchRow(
            strings.confReplaceDivinename, advanced.replaceDivineName, onReplaceDivineName,
            leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null) },
        )

        AbSettingsCategoryHeader(strings.speakBookmarkingSettingsTitle)
        AbSwitchRow(
            strings.confSpeakAutoBookmark, advanced.autoBookmark, onAutoBookmark,
            leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
        )
        AbSwitchRow(
            strings.confSavePlaybackSettingsToBookmarks, advanced.restoreSettingsFromBookmarks,
            onRestoreSettingsFromBookmarks,
            leadingIcon = { Icon(Icons.Filled.BookmarkAdded, contentDescription = null) },
        )

        AbSettingsRow(
            title = strings.helpLabel, summary = null, enabled = true,
            onClick = onHelp,
            leadingIcon = AbHelpMenuIcon,
        )
    }
}
