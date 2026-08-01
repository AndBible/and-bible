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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.theme.argbToHsv
import net.bible.sharedcore.theme.hexOf
import net.bible.sharedcore.theme.hsvToArgb
import net.bible.sharedcore.theme.hueFromOffset
import net.bible.sharedcore.theme.hueToOffset
import net.bible.sharedcore.theme.parseHexColor
import net.bible.sharedcore.theme.satValFromOffset
import net.bible.sharedcore.theme.satValToOffset

/** Classic's custom page: a saturation/value square with the hue strip down its right-hand side
 *  (`ColorPickerView`'s geometry — 30dp strip, 10dp apart), then the original colour, an arrow, the
 *  working colour, and a six-digit hex field.
 *
 *  Public, not internal, because the golden tests live in `:app` — a different module. */
@Composable
fun ColorPickerCustomPage(
    initialColor: Int,
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ARGB cannot represent the hue of an achromatic colour, so HSV — not the Int — is the source
    // of truth while the user drags: dragging value or saturation to 0 and back must return the
    // hue the user picked, not red. Classic keeps hue/sat/val in fields for exactly this reason
    // (ColorPickerView), and its sat/val touch branch never writes hue. The LaunchedEffect
    // resyncs on an EXTERNAL change only: after our own drag, hsvToArgb(state) == color already,
    // so it no-ops.
    var hsv by remember { mutableStateOf(argbToHsv(color)) }
    LaunchedEffect(color) {
        if (hsvToArgb(hsv.first, hsv.second, hsv.third) != color) hsv = argbToHsv(color)
    }
    val hue = hsv.first
    val sat = hsv.second
    val value = hsv.third

    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The square's height comes from its own `aspectRatio`, which Row cannot see when
        // measuring the hue strip's `fillMaxHeight()` sibling (Row has no fixed height of its own
        // here, so the incoming height constraint is unbounded and `fillMaxHeight()` collapses to
        // zero). `Modifier.height(IntrinsicSize.Min)` makes the Row measure its OWN height from its
        // children's min intrinsic height first, then hands that bounded height to both children —
        // the standard Compose idiom for "match the tallest sibling".
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SatValSquare(
                hue = hue,
                sat = sat,
                value = value,
                onSatVal = { s, v -> hsv = Triple(hue, s, v); onColorChange(hsvToArgb(hue, s, v)) },
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
            HueStrip(
                hue = hue,
                onHue = { h -> hsv = Triple(h, sat, value); onColorChange(hsvToArgb(h, sat, value)) },
                modifier = Modifier.width(30.dp).fillMaxHeight(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorPreviewPanel(initialColor)
            Text("→", style = MaterialTheme.typography.titleMedium)
            ColorPreviewPanel(color)
            Spacer(Modifier.weight(1f))
            Text("#", style = MaterialTheme.typography.bodyLarge)
            HexField(color = color, onColorChange = onColorChange)
        }
    }
}

/** 66x34dp preview panel, classic's `cpv_dialog_preview_*` dimensions. */
@Composable
private fun ColorPreviewPanel(color: Int) {
    Box(
        Modifier
            .size(width = 66.dp, height = 34.dp)
            .background(AbColor.toComposeColor(color)),
    )
}

/** Saturation left->right over a white->hue gradient, value top->bottom over a transparent->black
 *  one; the tracker is classic's 5dp ring. All coordinate maths is in `:sharedCore` so it is
 *  testable — this composable only forwards the local offset and the measured size. */
@Composable
private fun SatValSquare(
    hue: Float,
    sat: Float,
    value: Float,
    onSatVal: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture lambdas must see the CURRENT hue without re-keying `pointerInput` on every frame
    // of a drag (re-keying restarts the gesture detector and drops the drag).
    val currentOnSatVal by rememberUpdatedState(onSatVal)
    Box(
        modifier
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color(hsvToArgb(hue, 1f, 1f)))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val (tx, ty) = satValToOffset(sat, value, size.width, size.height)
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(tx, ty),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            // A single hand-rolled recognizer, not a stacked detectTapGestures +
            // detectDragGestures pair — two gesture detectors racing on the same node is a shape
            // this repo has already ruled out once (see detectTitleGestures in
            // sharedUi/reading/ReadingToolbar.kt). The press itself moves the tracker immediately
            // (classic's behaviour), then a drag keeps updating it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val (s0, v0) = satValFromOffset(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())
                    currentOnSatVal(s0, v0)
                    drag(down.id) { change ->
                        change.consume()
                        val (s, v) = satValFromOffset(
                            change.position.x, change.position.y,
                            size.width.toFloat(), size.height.toFloat(),
                        )
                        currentOnSatVal(s, v)
                    }
                }
            },
    )
}

/** Vertical hue strip: 360 at the top, 0 at the bottom (classic's orientation), with a 4dp bar
 *  tracker. The seven stops are the six hue corners plus the wrap back to red. */
@Composable
private fun HueStrip(hue: Float, onHue: (Float) -> Unit, modifier: Modifier = Modifier) {
    val currentOnHue by rememberUpdatedState(onHue)
    val stops = remember {
        listOf(360f, 300f, 240f, 180f, 120f, 60f, 0f).map { Color(hsvToArgb(it, 1f, 1f)) }
    }
    Box(
        modifier
            .drawBehind {
                drawRect(Brush.verticalGradient(stops))
                val y = hueToOffset(hue, size.height)
                drawRect(
                    color = Color.White,
                    topLeft = Offset(0f, y - 2.dp.toPx()),
                    size = Size(size.width, 4.dp.toPx()),
                )
            }
            // Single recognizer — see the comment on SatValSquare's pointerInput.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnHue(hueFromOffset(down.position.y, size.height.toFloat()))
                    drag(down.id) { change ->
                        change.consume()
                        currentOnHue(hueFromOffset(change.position.y, size.height.toFloat()))
                    }
                }
            },
    )
}

/** The six-digit hex field.
 *
 *  The two directions must not fight: a drag or a swatch tap rewrites the field, and typing moves
 *  the colour — but only when the text parses. The `LaunchedEffect` guard (rewrite only when the
 *  field does not already describe [color]) is this port's equivalent of classic's `fromEditText`
 *  flag, and it is what stops a half-typed value being erased under the cursor. */
@Composable
private fun HexField(color: Int, onColorChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(hexOf(color)) }
    LaunchedEffect(color) {
        if (parseHexColor(text) != color) text = hexOf(color)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6)
            text = filtered
            parseHexColor(filtered)?.let { parsed -> if (parsed != color) onColorChange(parsed) }
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.width(140.dp),
    )
}
