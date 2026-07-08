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
package net.bible.sharedui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.calculator.CalcError
import net.bible.sharedcore.calculator.CalcKey
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless Compose keypad mirroring the classic `calculator_layout.xml`: a right-aligned display
 * `Text` above a 4-column grid (rows: C ( ) % ÷ / 7 8 9 × / 4 5 6 − / 1 2 3 + / 0 . =). The `0`
 * button spans two columns like the XML (`layout_weight=2`). Button glyphs are literals; the shown
 * multiply glyph is "×" while the controller records "x" internally, exactly as the old activity.
 * All state lives in [CalculatorController]; this only renders [display] / [error] and forwards
 * key taps. When [error] is non-null it maps to the localized message via `LocalStrings.current`,
 * restoring the three error Toasts the classic activity showed (and exercising the strings pipeline).
 */
@Composable
fun CalculatorScreen(display: String, error: CalcError?, onKey: (CalcKey) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(3f).padding(horizontal = 12.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Text(
                text = display,
                textAlign = TextAlign.End,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (error != null) {
            val strings = LocalStrings.current
            val message = when (error) {
                CalcError.WRONG_FORMAT -> strings.calcWrongFormat
                CalcError.WRONG_FORMAT_OPERAND -> strings.calcWrongFormatOperand
                CalcError.DIVISION_BY_ZERO -> strings.calcDivisionByZero
            }
            Text(
                text = message,
                textAlign = TextAlign.End,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        KeyRow(listOf(
            "C" to CalcKey.CLEAR, "( )" to CalcKey.PARENS, "%" to CalcKey.PERCENT, "÷" to CalcKey.DIV,
        ), onKey)
        KeyRow(listOf(
            "7" to CalcKey.D7, "8" to CalcKey.D8, "9" to CalcKey.D9, "×" to CalcKey.TIMES,
        ), onKey)
        KeyRow(listOf(
            "4" to CalcKey.D4, "5" to CalcKey.D5, "6" to CalcKey.D6, "-" to CalcKey.MINUS,
        ), onKey)
        KeyRow(listOf(
            "1" to CalcKey.D1, "2" to CalcKey.D2, "3" to CalcKey.D3, "+" to CalcKey.PLUS,
        ), onKey)
        // Last row: "0" spans two columns (weight 2), then "." and "=".
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CalcButton("0", CalcKey.D0, onKey, Modifier.weight(2f).fillMaxSize())
            CalcButton(".", CalcKey.DOT, onKey, Modifier.weight(1f).fillMaxSize())
            CalcButton("=", CalcKey.EQUALS, onKey, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(keys: List<Pair<String, CalcKey>>, onKey: (CalcKey) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { (label, key) ->
            CalcButton(label, key, onKey, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun CalcButton(label: String, key: CalcKey, onKey: (CalcKey) -> Unit, modifier: Modifier) {
    Button(
        onClick = { onKey(key) },
        modifier = modifier.padding(1.dp),
    ) {
        Text(text = label, fontSize = 28.sp)
    }
}
