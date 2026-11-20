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
package net.bible.android.view.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.android.activity.R
import net.bible.android.control.widget.VerseBible
import net.bible.android.control.widget.VerseChangeFrequency
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.database.IdType
import net.bible.sharedui.components.AbAlertDialog

/** What the configuration offers; read from the app before the screen is shown. */
data class VerseConfigOptions(
    val labels: List<Pair<IdType, String>>,
    val bibles: List<VerseBible>,
    val defaultBible: VerseBible?,
    /** False hides "Today's reading plan" (no plan is active). */
    val hasReadingPlan: Boolean,
)

private enum class SourceKind { LABEL, RANDOM, PLAN }

/**
 * The Verse widget's configuration as a dialog: where the verses come from, how often they change, which Bible, and the
 * two display toggles. [existing] is the widget's current configuration when it is reconfigured, null for a new one;
 * [onSave] gets a configuration with [seed] kept (or [newSeed] for a new widget).
 */
@Composable
fun VerseWidgetConfigScreen(
    options: VerseConfigOptions,
    existing: VerseWidgetConfig?,
    newSeed: Long,
    onSave: (VerseWidgetConfig) -> Unit,
    onCancel: () -> Unit,
) {
    val initialKind = when (existing?.source) {
        is VerseSource.Label -> if (options.labels.any { it.first == (existing.source as VerseSource.Label).labelId }) SourceKind.LABEL else SourceKind.RANDOM
        VerseSource.ReadingPlan -> if (options.hasReadingPlan) SourceKind.PLAN else SourceKind.RANDOM
        else -> SourceKind.RANDOM
    }
    var kind by remember { mutableStateOf(initialKind) }
    var labelId by remember {
        mutableStateOf((existing?.source as? VerseSource.Label)?.labelId ?: options.labels.firstOrNull()?.first)
    }
    var part by remember { mutableStateOf((existing?.source as? VerseSource.Random)?.part ?: VerseSource.RandomPart.WHOLE_BIBLE) }
    var frequency by remember { mutableStateOf(existing?.frequency ?: VerseChangeFrequency.DAILY) }
    var bible by remember { mutableStateOf(existing?.bibleInitials) }
    var showButtons by remember { mutableStateOf(existing?.showButtons ?: true) }
    var showSource by remember { mutableStateOf(existing?.showSourceAndDate ?: true) }

    AbAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.widget_verse_config_title)) },
        text = {
            Column(Modifier.testTag("verse-config")) {
                Section(R.string.widget_verse_config_source_label)
                if (options.labels.isNotEmpty()) {
                    Choice(stringResource(R.string.widget_verse_config_source_label_option), kind == SourceKind.LABEL, "source-label") { kind = SourceKind.LABEL }
                    if (kind == SourceKind.LABEL) options.labels.forEach { (id, name) ->
                        Choice(name, labelId == id, "label-$name", indent = true) { labelId = id }
                    }
                }
                Choice(stringResource(R.string.widget_verse_config_source_random), kind == SourceKind.RANDOM, "source-random") { kind = SourceKind.RANDOM }
                if (kind == SourceKind.RANDOM) listOf(
                    VerseSource.RandomPart.WHOLE_BIBLE to R.string.widget_verse_config_random_whole,
                    VerseSource.RandomPart.NEW_TESTAMENT to R.string.widget_verse_config_random_nt,
                    VerseSource.RandomPart.PSALMS_PROVERBS to R.string.widget_verse_config_random_psalms_proverbs,
                ).forEach { (p, res) -> Choice(stringResource(res), part == p, "random-${p.name}", indent = true) { part = p } }
                if (options.hasReadingPlan) {
                    Choice(stringResource(R.string.widget_verse_config_source_plan), kind == SourceKind.PLAN, "source-plan") { kind = SourceKind.PLAN }
                }

                Section(R.string.widget_verse_config_changes)
                listOf(
                    VerseChangeFrequency.DAILY to R.string.widget_verse_config_daily,
                    VerseChangeFrequency.EVERY_3_HOURS to R.string.widget_verse_config_every_few_hours,
                    VerseChangeFrequency.ON_SHUFFLE to R.string.widget_verse_config_on_shuffle,
                ).forEach { (f, res) -> Choice(stringResource(res), frequency == f, "freq-${f.name}") { frequency = f } }

                Section(R.string.widget_verse_config_bible)
                Choice(
                    stringResource(R.string.widget_verse_config_bible_default, options.defaultBible?.abbreviation ?: "-"),
                    bible == null, "bible-default",
                ) { bible = null }
                options.bibles.forEach { b -> Choice(b.abbreviation, bible == b.initials, "bible-${b.initials}") { bible = b.initials } }

                Toggle(stringResource(R.string.widget_verse_config_show_buttons), showButtons, "show-buttons") { showButtons = it }
                Toggle(stringResource(R.string.widget_verse_config_show_source), showSource, "show-source") { showSource = it }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton(onClick = {
                val source = when (kind) {
                    SourceKind.LABEL -> labelId?.let { VerseSource.Label(it) } ?: VerseSource.Random(part)
                    SourceKind.RANDOM -> VerseSource.Random(part)
                    SourceKind.PLAN -> VerseSource.ReadingPlan
                }
                onSave(
                    VerseWidgetConfig(
                        source = source, frequency = frequency, bibleInitials = bible,
                        showButtons = showButtons, showSourceAndDate = showSource,
                        seed = existing?.seed ?: newSeed, shuffleCount = existing?.shuffleCount ?: 0,
                    ),
                )
            }, modifier = Modifier.testTag("verse-config-save")) {
                Text(stringResource(if (existing == null) R.string.widget_verse_config_add else R.string.widget_verse_config_save))
            }
        },
    )
}

@Composable
private fun Section(res: Int) = Text(
    stringResource(res),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
)

@Composable
private fun Choice(text: String, selected: Boolean, tag: String, indent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).testTag(tag)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(start = if (indent) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).testTag(tag)
            .clickable(role = Role.Checkbox) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text, Modifier.padding(start = 12.dp))
    }
}
