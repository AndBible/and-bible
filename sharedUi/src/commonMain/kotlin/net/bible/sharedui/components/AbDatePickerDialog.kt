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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import net.bible.sharedui.theme.LocalAbColors
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoBorder

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Civil-calendar day count since the 1970-01-01 epoch, from (year, month 1-12, day) — Howard
 * Hinnant's `days_from_civil` algorithm (public domain,
 * http://howardhinnant.github.io/date_algorithms.html#days_from_civil). Chosen deliberately over
 * `java.util.Calendar` (JVM-only, unusable from `:sharedUi` commonMain's iOS target) and
 * `kotlinx-datetime` (not a project dependency): pure integer arithmetic needs neither.
 */
internal fun daysFromCivil(year: Int, month1to12: Int, day: Int): Long {
    val y = if (month1to12 <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = (y - era * 400).toLong() // [0, 399]
    val doy = (153L * (if (month1to12 > 2) month1to12 - 3 else month1to12 + 9) + 2) / 5 + day - 1 // [0, 365]
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy // [0, 146096]
    return era * 146097L + doe - 719468L
}

/** Inverse of [daysFromCivil] — Hinnant's `civil_from_days`. Returns (year, month 1-12, day). */
internal fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097 // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
    val mp = (5 * doy + 2) / 153 // [0, 11]
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt() // [1, 31]
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt() // [1, 12]
    return Triple((if (month <= 2) y + 1 else y).toInt(), month, day)
}

/**
 * UTC-midnight millis of (year, month 1-12, day) — the representation Material3's
 * [androidx.compose.material3.DatePickerState] uses for both `initialSelectedDateMillis` and
 * `selectedDateMillis`. Round-trips exactly with [utcMidnightMillisToYmd] because both go through
 * the same day-count encoding: a caller that only ever deals in (year, month, day) — never a real
 * timezone-bearing instant — can use this pair without ever risking the off-by-one-day-in-UTC+
 * mistake that converting a genuine local instant to/from UTC millis invites.
 */
fun ymdToUtcMidnightMillis(year: Int, month1to12: Int, day: Int): Long =
    daysFromCivil(year, month1to12, day) * MILLIS_PER_DAY

/** Inverse of [ymdToUtcMidnightMillis]: the (year, month 1-12, day) a UTC-midnight millis value names. */
fun utcMidnightMillisToYmd(utcMillis: Long): Triple<Int, Int, Int> =
    civilFromDays(utcMillis.floorDiv(MILLIS_PER_DAY))

/**
 * Material3 `DatePicker` inside a width-constrained [BasicAlertDialog] (Task 30b, correction 10 /
 * ruling R3-2; rebuilt off M3's own `DatePickerDialog` by the run 3 final-review fix wave, I4).
 * Replaces the platform `android.app.DatePickerDialog` classic used for the reading plan's "set
 * start date" (`NavHostComposeActivity.showReadingPlanStartDatePicker`, classic `:245-256`).
 * [initialUtcMillis] and [maxUtcMillis] are UTC-midnight millis for the initially-shown date and the
 * last selectable date respectively (both computed by the host from local year/month/day via
 * [ymdToUtcMidnightMillis] — never a raw instant, so no timezone conversion ever actually happens;
 * see that function's kdoc). [onConfirm] reports the picked date back as (year, month 1-12, day),
 * decoded via [utcMidnightMillisToYmd] so the host never has to touch Material3's millis
 * representation itself.
 *
 * I4: M3's own `DatePickerDialog` sizes its surface with
 * `Modifier.requiredWidth(DatePickerModalTokens.ContainerWidth)` (360dp), which `requiredWidth`
 * enforces even on a narrower window — the platform dialog it replaced adapted to the width, so
 * this port didn't. Built here instead on `BasicAlertDialog(properties =
 * DialogProperties(usePlatformDefaultWidth = false))`, whose content is free to size itself against
 * the REAL available width: a [Surface] capped at `widthIn(max = 360.dp)` but otherwise
 * `fillMaxWidth()`, so it shrinks below 360dp rather than clipping. [BoxWithConstraints] reads that
 * real width (measured OUTSIDE the 360dp cap, so it sees the window's actual narrowness) to start
 * the picker in [DisplayMode.Input] below 360dp — a stack of text fields, not a 7-column calendar
 * grid, so it has no columns to clip — and in [DisplayMode.Picker] (the calendar grid, this
 * function's previous sole behaviour) at 360dp and up. The user can still switch modes via the
 * picker's own toggle icon either way; only the STARTING mode depends on width.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbDatePickerDialog(
    initialUtcMillis: Long,
    maxUtcMillis: Long,
    confirmText: String,
    dismissText: String,
    onConfirm: (year: Int, month1to12: Int, day: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val mono = isPureMonochrome()
    val selectableDates = remember(maxUtcMillis, mono) {
        object : SelectableDates {
            override fun isSelectableYear(year: Int): Boolean =
                !mono || year <= utcMidnightMillisToYmd(maxUtcMillis).first
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMillis
        }
    }
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (mono) NoDialogDim()
        BoxWithConstraints {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = initialUtcMillis,
                selectableDates = selectableDates,
                initialDisplayMode = if (maxWidth < 360.dp) DisplayMode.Input else DisplayMode.Picker,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).monoBorder(AlertDialogDefaults.shape),
                shape = AlertDialogDefaults.shape,
                color = if (mono) MaterialTheme.colorScheme.surface else AlertDialogDefaults.containerColor,
                tonalElevation = if (mono) 0.dp else AlertDialogDefaults.TonalElevation,
            ) {
                Column {
                    val ink = MaterialTheme.colorScheme.onSurface
                    val paper = MaterialTheme.colorScheme.surface
                    val disabled = LocalAbColors.current.monoDisabled
                    DatePicker(
                        state = state, modifier = Modifier.weight(1f, fill = false),
                        colors = if (mono) DatePickerDefaults.colors(
                            disabledDayContentColor = disabled,
                            disabledYearContentColor = disabled,
                            selectedDayContentColor = paper,
                            selectedDayContainerColor = ink,
                            selectedYearContentColor = paper,
                            selectedYearContainerColor = ink,
                            disabledSelectedDayContentColor = disabled,
                            disabledSelectedDayContainerColor = paper,
                            disabledSelectedYearContentColor = disabled,
                            disabledSelectedYearContainerColor = paper,
                        ) else DatePickerDefaults.colors(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text(dismissText) }
                        TextButton(onClick = {
                            val (year, month, day) = utcMidnightMillisToYmd(state.selectedDateMillis ?: initialUtcMillis)
                            onConfirm(year, month, day)
                        }) { Text(confirmText) }
                    }
                }
            }
        }
    }
}
