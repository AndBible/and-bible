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

package net.bible.sharedui.bookmark

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedui.components.AbIcons
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.strings.LocalStrings

/**
 * The labels screen's help, for all four modes.
 *
 * It replaced a platform AlertDialog built from SpannableString + ImageSpan, whose text named
 * widgets the Compose screen no longer has: it told the user to tap the `ic_label_24dp` /
 * `ic_label_circle` pair for auto-assign (that is the ⚡ bolt now) and a refresh BUTTON to re-order
 * (that is an overflow row now), and it said nothing about the style tags, the ⚙ override mark or
 * the bolt's hollow off-state. Drawing the legend from the SAME icons the screen draws is the point:
 * the help cannot name a glyph that is not on screen, because it is the glyph.
 *
 * A dialog, not a bottom sheet, per the standing rule (round 12c, reaffirmed round 14a): info/help
 * stays a dialog everywhere in this port.
 *
 * The three intros and the scope sentence are the ORIGINAL translated strings — they never went
 * stale, so ~54 translations are kept rather than reset. Only the legend is new English.
 */
@Composable
fun ManageLabelsHelpDialog(
    mode: ManageLabelsMode,
    title: String,
    scopeSentence: String?,
    readMoreUrl: String,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val intro = when (mode) {
        ManageLabelsMode.ASSIGN -> strings.assignLabelsHelpIntro
        ManageLabelsMode.WORKSPACE -> strings.autoAssignLabelsHelpIntro
        ManageLabelsMode.HIDELABELS -> strings.hideLabelsHelpIntro
        ManageLabelsMode.STUDYPAD -> strings.studyPadsHelpText
    }
    val body = if (scopeSentence != null) "$intro\n\n$scopeSentence" else intro
    AbInfoDialog(
        title = title,
        body = body,
        onDismiss = onDismiss,
        readMoreLabel = strings.watchTutorialVideo,
        readMoreUrl = readMoreUrl,
        content = if (mode == ManageLabelsMode.STUDYPAD) null else {
            {
                Spacer(Modifier.height(12.dp))
                Column {
                    // HIDELABELS is excluded here even though showCheckboxes covers it too: its
                    // reused intro already says what ticking a label does, so a checkbox row would
                    // repeat the intro rather than add information.
                    if (mode == ManageLabelsMode.ASSIGN) {
                        // The only legend row whose screen counterpart is an interactive control
                        // (ManageLabelsScreen.kt's Checkbox), not a static Icon -- drawing the real,
                        // read-only Checkbox here (onCheckedChange = null, so it carries no click
                        // target or toggleable semantics) is what keeps "it IS the glyph" true for
                        // this row too, matching all five icon rows below it.
                        HelpRow({ Checkbox(checked = true, onCheckedChange = null) }, strings.manageLabelsHelpCheckbox)
                    }
                    if (mode.primaryShown) {
                        HelpRow({ Icon(Icons.Filled.Bookmark, null) }, strings.manageLabelsHelpPrimary)
                    }
                    if (mode.workspaceEdits) {
                        HelpRow({ Icon(AbIcons.BoltOutline, null) }, strings.manageLabelsHelpAutoAssign)
                        HelpRow({ Icon(Icons.Filled.Favorite, null) }, strings.manageLabelsHelpFavourite)
                    }
                    if (mode.styleTagsShown) {
                        HelpRow({ Icon(Icons.Filled.Tune, null) }, strings.manageLabelsHelpStyles)
                    }
                    if (mode.hasReOrderButton) {
                        HelpRow({ Icon(Icons.Filled.MoreVert, null) }, strings.manageLabelsHelpReorder)
                    }
                }
            }
        },
    )
}

/** One legend line: the screen's own glyph, then one short sentence. */
@Composable
private fun HelpRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Row(modifier = Modifier.size(24.dp), verticalAlignment = Alignment.CenterVertically) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
}
