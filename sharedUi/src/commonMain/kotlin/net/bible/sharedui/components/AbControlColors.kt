package net.bible.sharedui.components

import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import net.bible.sharedui.theme.LocalAbColors
import net.bible.sharedui.theme.isPureMonochrome

/** Preserve Material defaults outside pure mode; disabled paints are opaque, not composited alpha. */
@Composable
fun abOutlinedTextFieldColors() = if (isPureMonochrome()) OutlinedTextFieldDefaults.colors(
    disabledTextColor = LocalAbColors.current.monoDisabled,
    disabledBorderColor = LocalAbColors.current.monoDisabled,
    disabledLabelColor = LocalAbColors.current.monoDisabled,
    disabledPlaceholderColor = LocalAbColors.current.monoDisabled,
    disabledLeadingIconColor = LocalAbColors.current.monoDisabled,
    disabledTrailingIconColor = LocalAbColors.current.monoDisabled,
) else OutlinedTextFieldDefaults.colors()

@Composable
fun abFilterChipBorder(enabled: Boolean, selected: Boolean) = if (isPureMonochrome()) androidx.compose.foundation.BorderStroke(
    androidx.compose.ui.unit.Dp(1f), if (enabled) MaterialTheme.colorScheme.onSurface else LocalAbColors.current.monoDisabled,
) else FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected)

@Composable
fun abCheckboxColors() = if (isPureMonochrome()) CheckboxDefaults.colors(
    disabledCheckedColor = LocalAbColors.current.monoDisabled,
    disabledUncheckedColor = LocalAbColors.current.monoDisabled,
    disabledIndeterminateColor = LocalAbColors.current.monoDisabled,
) else CheckboxDefaults.colors()

@Composable
fun abFilterChipColors() = if (isPureMonochrome()) FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
    selectedLabelColor = MaterialTheme.colorScheme.surface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    disabledSelectedContainerColor = MaterialTheme.colorScheme.surface,
    disabledLabelColor = LocalAbColors.current.monoDisabled,
    disabledLeadingIconColor = LocalAbColors.current.monoDisabled,
    disabledTrailingIconColor = LocalAbColors.current.monoDisabled,
) else FilterChipDefaults.filterChipColors()
