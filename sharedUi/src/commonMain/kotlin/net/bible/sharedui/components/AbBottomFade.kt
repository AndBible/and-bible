package net.bible.sharedui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the bottom fade. Deep enough to read as a soft edge on a 24dp settings row rather than
 * as a hairline, shallow enough not to wash out the row it sits over.
 */
val AbBottomFadeHeight: Dp = 24.dp

/**
 * Fades the bottom edge of a bounded scroll region while there is still content below it.
 *
 * This exists because a bounded scroll area inside a bottom sheet clips flush, with nothing beneath
 * the clip, so a half-cut row reads as the end of the list rather than as more content. The framing
 * that a reset button above and a confirm row below give the colour-settings sheet is exactly what
 * the Speak settings page lacked; this is that framing, reduced to one modifier any sheet can apply.
 *
 * **`visible` is a lambda on purpose, and this is load-bearing.** It is read in the DRAW phase, so
 * the fade is correct on the very first frame. A plain `Boolean` argument would be evaluated during
 * composition, before the content had measured — `ScrollState.maxValue` would still be 0, so
 * `canScrollForward` would be false and the fade would be absent from precisely the frame that has
 * to show it. Pass `{ scrollState.canScrollForward }` or the `LazyListState` equivalent.
 *
 * **The gradient runs from `color` at alpha 0 to `color`, never from [Color.Transparent].**
 * `Color.Transparent` is black with alpha 0, and interpolating towards it drags the gradient through
 * a grey-to-black cast that is plainly visible over a light surface. Copying the target colour and
 * varying only its alpha keeps the hue constant across the whole ramp.
 *
 * `color` is the caller's surface colour, i.e. a Material ROLE and not a hue, so the fade greys
 * correctly in the black-and-white and e-ink display modes with no special-casing.
 *
 * The fade is bottom-only by design: a symmetric top fade would advertise content above, which was
 * never the problem — content above is what the user just scrolled past.
 *
 * **What the default height is actually for.** Because the ramp goes from the surface colour to the
 * same surface colour, the fade is only *visible* where it covers content — over bare surface it
 * paints nothing at all. So the affordance works exactly when the clip cannot land in a gap: at
 * [AbBottomFadeHeight] any vertical run of empty space shorter than 24dp is guaranteed to be
 * straddled, which covers settings rows, list rows and slider rows as this app spaces them. A
 * caller whose content has taller gaps than that must pass a larger [height], or the fade will be
 * invisible at some scroll positions. That condition is the reason for the number, not taste.
 *
 * ROBORAZZI: unlike the sheets that host it, this needs no popup to render, so it CAN be captured.
 * `AbBottomFadeGoldenTest` is the port's only automated coverage of sheet-internal chrome.
 */
fun Modifier.abBottomFade(
    color: Color,
    height: Dp = AbBottomFadeHeight,
    visible: () -> Boolean,
): Modifier = drawWithContent {
    drawContent()
    if (!visible()) return@drawWithContent
    // Clamp: a viewport shorter than the fade must fade what it has, not overdraw past its own top.
    val fadeHeight = height.toPx().coerceIn(0f, size.height)
    if (fadeHeight <= 0f) return@drawWithContent
    val top = size.height - fadeHeight
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0f), color),
            startY = top,
            endY = size.height,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, fadeHeight),
    )
}
