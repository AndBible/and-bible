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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
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
 * Landscape (wider than tall): the display shrinks to one key row's height; the 5 × 4 grid is unchanged.
 * All state lives in [CalculatorController]; this only renders [display] / [error] and forwards
 * key taps. When [error] is non-null it maps to the localized message via `LocalStrings.current`,
 * restoring the three error Toasts the classic activity showed (and exercising the strings pipeline).
 */
@Composable
fun CalculatorScreen(display: String, error: CalcError?, onKey: (CalcKey) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // Display weight + 5 key rows; the glyph size follows the row height only in landscape, so the
        // portrait rendering is unchanged.
        val rowHeight = (maxHeight - 8.dp) / (if (landscape) 6f else 8f)
        // M3 buttons pad 8dp top and bottom, which would clip the glyph in a landscape-height row.
        val keyPadding = if (landscape) PaddingValues(0.dp) else ButtonDefaults.ContentPadding
        val glyphSize = if (landscape) (rowHeight.value * 0.45f).coerceIn(14f, 32f).sp else 28.sp
        // The one-row landscape display is too short for 40sp; shrink the glyphs with it.
        val displaySize = if (landscape) (rowHeight.value * 0.6f).coerceIn(14f, 40f).sp else 40.sp
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Grouped display "screen": a tonal surfaceContainer panel so the result reads as a distinct
            // calculator display rather than a bare Text. Single large display line — the controller
            // exposes only one `display: String`, so a two-line (expression + result) split is deferred
            // as a follow-up (it would require new controller state; behaviour must stay untouched).
            Surface(
                modifier = Modifier.fillMaxWidth().weight(if (landscape) 1f else 3f).padding(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = if (landscape) 0.dp else 12.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Text(
                        text = display,
                        textAlign = TextAlign.End,
                        fontSize = displaySize,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
            ), onKey, glyphSize, keyPadding)
            KeyRow(listOf(
                "7" to CalcKey.D7, "8" to CalcKey.D8, "9" to CalcKey.D9, "×" to CalcKey.TIMES,
            ), onKey, glyphSize, keyPadding)
            KeyRow(listOf(
                "4" to CalcKey.D4, "5" to CalcKey.D5, "6" to CalcKey.D6, "-" to CalcKey.MINUS,
            ), onKey, glyphSize, keyPadding)
            KeyRow(listOf(
                "1" to CalcKey.D1, "2" to CalcKey.D2, "3" to CalcKey.D3, "+" to CalcKey.PLUS,
            ), onKey, glyphSize, keyPadding)
            // Last row: "0" spans two columns (weight 2), then "." and "=".
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CalcButton("0", CalcKey.D0, onKey, Modifier.weight(2f).fillMaxSize(), glyphSize, keyPadding)
                CalcButton(".", CalcKey.DOT, onKey, Modifier.weight(1f).fillMaxSize(), glyphSize, keyPadding)
                CalcButton("=", CalcKey.EQUALS, onKey, Modifier.weight(1f).fillMaxSize(), glyphSize, keyPadding)
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(keys: List<Pair<String, CalcKey>>, onKey: (CalcKey) -> Unit, glyphSize: TextUnit, keyPadding: PaddingValues) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { (label, key) ->
            CalcButton(label, key, onKey, Modifier.weight(1f).fillMaxSize(), glyphSize, keyPadding)
        }
    }
}

/** The visual weight a key carries, driving its M3 colour role (Option 1 hierarchy). Shape is left
 *  at the M3 default pill for every role — Option 1 changed colour + the display panel only. */
private enum class CalcRole { DIGIT, OPERATOR, EQUALS, FUNCTION }

private fun roleFor(key: CalcKey): CalcRole = when (key) {
    CalcKey.PLUS, CalcKey.MINUS, CalcKey.TIMES, CalcKey.DIV -> CalcRole.OPERATOR
    CalcKey.EQUALS -> CalcRole.EQUALS
    CalcKey.CLEAR, CalcKey.PARENS, CalcKey.PERCENT -> CalcRole.FUNCTION
    else -> CalcRole.DIGIT // D0-D9 and DOT: number entry
}

@Composable
private fun CalcButton(label: String, key: CalcKey, onKey: (CalcKey) -> Unit, modifier: Modifier, glyphSize: TextUnit, keyPadding: PaddingValues) {
    val onClick = { onKey(key) }
    val buttonModifier = modifier.padding(1.dp)
    val text = @Composable { Text(text = label, fontSize = glyphSize) }
    when (roleFor(key)) {
        // Digits → soft tonal (secondaryContainer) — the low-emphasis bulk of the keypad.
        CalcRole.DIGIT -> FilledTonalButton(onClick = onClick, modifier = buttonModifier, contentPadding = keyPadding) { text() }
        // Operators ÷ × − + → filled primary, the coloured emphasis.
        CalcRole.OPERATOR -> Button(onClick = onClick, modifier = buttonModifier, contentPadding = keyPadding) { text() }
        // "=" → the single strongest accent: a distinct emphasised (tertiary) container so it stands
        // apart from the primary operators.
        CalcRole.EQUALS -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = keyPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) { text() }
        // C / ( ) / % → tonal tertiary: distinct hue, low emphasis (utility keys).
        CalcRole.FUNCTION -> FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = keyPadding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) { text() }
    }
}
