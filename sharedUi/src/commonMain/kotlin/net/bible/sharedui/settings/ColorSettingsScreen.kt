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

package net.bible.sharedui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorSettingsUiState
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbColorPicker
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless port of the classic colours editor (`ColorSettingsFragment`/`colors_settings.xml`):
 * an optional workspace-colour row (hidden at window scope, per
 * `ColorsSnapshot.workspaceColorVisible`), then a "day mode" section (text/background swatches,
 * noise slider, background-image row + opacity slider) and an identical "night mode" section.
 * Every value comes from [state]; every mutation is forwarded to the host via the action lambdas,
 * which the host wires 1:1 to `ColorSettingsController`.
 *
 * The text/background/workspace swatches open the shared [AbColorPicker] in an [AlertDialog]
 * (mirrors [net.bible.sharedui.bookmark.LabelEditScreen]'s colour dialog) — picking a preset or
 * dragging a slider calls the matching `on*Change` immediately; the dialog's own button just
 * closes it. The background-image rows are always shown, even in BW/e-ink modes — this is the
 * *editor*, not the rendered WebView, which is what actually suppresses the image in monochrome.
 */
@Composable
fun ColorSettingsScreen(
    state: ColorSettingsUiState,
    labels: ColorSettingsLabels,
    onUp: () -> Unit,
    onReset: () -> Unit,
    onColorChange: (ColorField, Int) -> Unit,
    onNoiseChange: (night: Boolean, value: Int) -> Unit,
    onWorkspaceColorChange: (Int) -> Unit,
    onOpacityChange: (night: Boolean, value: Int) -> Unit,
    onChangeBackgroundImage: (night: Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = state.colors
    var colorFieldDialog by remember { mutableStateOf<ColorField?>(null) }
    var workspaceDialogOpen by remember { mutableStateOf(false) }

    AbScaffold(
        title = colors.title,
        onNavigateUp = onUp,
        actions = {
            IconButton(onClick = onReset) {
                Icon(Icons.Filled.Refresh, contentDescription = labels.reset)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (colors.workspaceColorVisible) {
                ColorSwatchRow(
                    label = labels.workspaceColor,
                    color = colors.workspaceColor,
                    onClick = { workspaceDialogOpen = true },
                )
            }

            SectionTitle(labels.dayMode)
            ColorModeSection(
                labels = labels,
                textColor = colors.dayTextColor,
                backgroundColor = colors.dayBackground,
                noise = colors.dayNoise,
                backgroundImageLabel = labels.backgroundImageDay,
                backgroundImageName = colors.dayBackgroundImageName,
                opacityLabel = labels.opacityDay,
                opacity = colors.dayBackgroundImageOpacity,
                onTextColorClick = { colorFieldDialog = ColorField.DAY_TEXT },
                onBackgroundColorClick = { colorFieldDialog = ColorField.DAY_BACKGROUND },
                onNoiseChange = { onNoiseChange(false, it) },
                onOpacityChange = { onOpacityChange(false, it) },
                onChangeBackgroundImage = { onChangeBackgroundImage(false) },
            )

            SectionTitle(labels.nightMode)
            ColorModeSection(
                labels = labels,
                textColor = colors.nightTextColor,
                backgroundColor = colors.nightBackground,
                noise = colors.nightNoise,
                backgroundImageLabel = labels.backgroundImageNight,
                backgroundImageName = colors.nightBackgroundImageName,
                opacityLabel = labels.opacityNight,
                opacity = colors.nightBackgroundImageOpacity,
                onTextColorClick = { colorFieldDialog = ColorField.NIGHT_TEXT },
                onBackgroundColorClick = { colorFieldDialog = ColorField.NIGHT_BACKGROUND },
                onNoiseChange = { onNoiseChange(true, it) },
                onOpacityChange = { onOpacityChange(true, it) },
                onChangeBackgroundImage = { onChangeBackgroundImage(true) },
            )
        }
    }

    val field = colorFieldDialog
    if (field != null) {
        AlertDialog(
            onDismissRequest = { colorFieldDialog = null },
            text = { AbColorPicker(color = colors.colorFor(field), onColorChange = { onColorChange(field, it) }) },
            confirmButton = { TextButton(onClick = { colorFieldDialog = null }) { Text(strings.okay) } },
        )
    }

    if (workspaceDialogOpen) {
        AlertDialog(
            onDismissRequest = { workspaceDialogOpen = false },
            text = { AbColorPicker(color = colors.workspaceColor, onColorChange = onWorkspaceColorChange) },
            confirmButton = { TextButton(onClick = { workspaceDialogOpen = false }) { Text(strings.okay) } },
        )
    }
}

/** One day/night mode section: text + background swatches, a noise slider, the background-image
 *  row and its opacity slider. Shared by both modes in [ColorSettingsScreen] — only the values and
 *  the `on*` callbacks (which field/night flag they close over) differ. */
@Composable
private fun ColorModeSection(
    labels: ColorSettingsLabels,
    textColor: Int,
    backgroundColor: Int,
    noise: Int,
    backgroundImageLabel: String,
    backgroundImageName: String,
    opacityLabel: String,
    opacity: Int,
    onTextColorClick: () -> Unit,
    onBackgroundColorClick: () -> Unit,
    onNoiseChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onChangeBackgroundImage: () -> Unit,
) {
    ColorSwatchRow(label = labels.textColor, color = textColor, onClick = onTextColorClick)
    ColorSwatchRow(label = labels.backgroundColor, color = backgroundColor, onClick = onBackgroundColorClick)
    AbSliderRow(
        label = labels.noise,
        value = noise,
        onValueChange = onNoiseChange,
        valueRange = 0f..100f,
        valueLabel = "$noise %",
        valueLabelFor = { "$it %" },
    )
    BackgroundImageRow(
        label = backgroundImageLabel,
        imageName = backgroundImageName,
        changeLabel = labels.change,
        onChange = onChangeBackgroundImage,
    )
    AbSliderRow(
        label = opacityLabel,
        value = opacity,
        onValueChange = onOpacityChange,
        valueRange = 0f..100f,
        valueLabel = "$opacity %",
        valueLabelFor = { "$it %" },
    )
}

/** A clickable coloured circle + label; opens the caller's colour-picker dialog. */
@Composable
private fun ColorSwatchRow(label: String, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AbColor.toComposeColor(color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The current background-image name (or "None") + a `change` button that opens the host's
 *  background-image chooser sub-screen. Always shown, in every colour mode — see the kdoc above. */
@Composable
private fun BackgroundImageRow(label: String, imageName: String, changeLabel: String, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(imageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = onChange) { Text(changeLabel) }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** Picks the ARGB value out of [ColorsSnapshot] that [field] addresses. */
private fun ColorsSnapshot.colorFor(field: ColorField): Int = when (field) {
    ColorField.DAY_TEXT -> dayTextColor
    ColorField.DAY_BACKGROUND -> dayBackground
    ColorField.NIGHT_TEXT -> nightTextColor
    ColorField.NIGHT_BACKGROUND -> nightBackground
}
