package net.bible.sharedui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode

/** The one grey MONOCHROME allows: disabled/off. Visible on thresholding e-ink panels. */
val MonoDisabled: Color = Color(0xFF808080)

fun monoInk(dark: Boolean): Color = if (dark) Color.White else Color.Black
fun monoPaper(dark: Boolean): Color = if (dark) Color.Black else Color.White

@Composable
fun isPureMonochrome(): Boolean = LocalDisplayColorMode.current == DisplayColorMode.MONOCHROME

/** Replaces a tone step or shadow with an ink edge; preserves other modes' modifiers unchanged. */
@Composable
fun Modifier.monoBorder(shape: Shape = RectangleShape, width: Dp = 1.dp): Modifier =
    if (isPureMonochrome()) border(width, monoInk(LocalIsDarkTheme.current), shape) else this
