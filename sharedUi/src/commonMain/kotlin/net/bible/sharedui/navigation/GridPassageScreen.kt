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
package net.bible.sharedui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridOption
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTopAppBar
import net.bible.sharedui.strings.LocalStrings

@Composable
fun GridChoosePassageScreen(
    ui: GridUi,
    options: GridOptions,
    onPick: (Int) -> Unit,
    onToggle: (GridOption) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(
        topBar = {
            AbTopAppBar(
                title = { Text(ui.title) },
                onNavigateUp = onNavigateUp,
                actions = {
                    if (ui.step == GridStep.BOOK) {
                        AbOverflowMenu(contentDescription = null) {
                            if (ui.showDeutToggle) {
                                DropdownMenuItem(
                                    text = { Text(if (options.showScripture) strings.deuterocanonical else strings.bible) },
                                    onClick = { onToggle(GridOption.DEUTEROCANONICAL) },
                                )
                            }
                            CheckItem(strings.menuAlphabetical, options.alphabetical) { onToggle(GridOption.ALPHABETICAL) }
                            CheckItem(strings.menuRowOrder, options.ltr) { onToggle(GridOption.LTR) }
                            CheckItem(strings.menuGroupByCategory, options.groupByCategory) { onToggle(GridOption.GROUP_BY_CATEGORY) }
                            CheckItem(strings.menuShowLongName, options.longNames) { onToggle(GridOption.LONG_NAMES) }
                            CheckItem(strings.menuShowProgressBars, options.showProgress) { onToggle(GridOption.SHOW_PROGRESS) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val sections = ui.sections
        val cols = ui.columns.coerceAtLeast(1)
        // Row-break spacers between grouped sections (classic group gaps).
        val spacerCount = if (sections != null) (sections.size - 1).coerceAtLeast(0) else 0
        // Content rows: each section starts on a fresh row (a full-span spacer forces the break),
        // so count rows per section, not across the whole list.
        val contentRows = if (sections != null) {
            sections.sumOf { (it.size + cols - 1) / cols }.coerceAtLeast(1)
        } else {
            ((ui.buttons.size + cols - 1) / cols).coerceAtLeast(1)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Fill the viewport height like the classic grid: divide the available height by the
            // row count. contentPadding is 4dp each side (=8dp) and each spacer is 8dp tall.
            val available = maxHeight - 8.dp - (spacerCount * 8).dp
            val minCell = 40.dp // below this we stop growing and let long (verse) lists scroll instead
            val cellHeight = (available / contentRows).coerceAtLeast(minCell)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
            ) {
                if (sections != null) {
                    sections.forEachIndexed { i, section ->
                        items(section.size, key = { "s$i-${section[it].id}" }) { idx ->
                            GridCell(section[idx], ui, cellHeight, onPick)
                        }
                        if (i < sections.lastIndex) {
                            item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(8.dp)) } // group row-break
                        }
                    }
                } else {
                    items(ui.buttons.size, key = { ui.buttons[it].id }) { idx ->
                        GridCell(ui.buttons[idx], ui, cellHeight, onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(b: GridButton, ui: GridUi, cellHeight: Dp, onPick: (Int) -> Unit) {
    // M3 neutral + accent: inactive cells are a neutral tonal surface, the current cell a filled
    // primary container — both theme-aware (no hardcoded dark). The legacy full-category-palette
    // mode (colorAllButtons) keeps its BW/COLOR_EINK-aware category colours (CategoryPalette.kt).
    val container = when {
        ui.colorAllButtons -> categoryColor(b.colorGroup)
        b.isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        // Category colours are always light pastels/greys, so pick contrast by fill luminance.
        ui.colorAllButtons -> if (container.luminance() > 0.5f) Color.Black else Color.White
        b.isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = Modifier.padding(2.dp)) {
        Surface(
            onClick = { onPick(b.id) },
            color = container,
            shape = RoundedCornerShape(6.dp),
            border = if (b.isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.fillMaxWidth().height(cellHeight),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (ui.showLongNames) {
                    // Abbreviation (bold) on top, full name below.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(2.dp),
                    ) {
                        Text(
                            text = b.label,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        b.longLabel?.let {
                            Text(
                                text = it,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                } else {
                    Text(
                        text = b.label,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(2.dp),
                    )
                }
                if (ui.showProgress && (b.readProgress > 0f || b.memProgress > 0f)) {
                    ProgressBars(b.readProgress, b.memProgress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ProgressBars(reading: Float, memorization: Float, modifier: Modifier) {
    Column(modifier) {
        if (reading > 0f) Bar(reading, readingColor())
        if (memorization > 0f) Bar(memorization, memorizationColor())
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(3.dp)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            Checkbox(checked = checked, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors())
        },
    )
}
