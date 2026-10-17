/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import net.bible.android.control.passagefinder.PassageFinderDataSource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * All drawing for the passage finder.
 *
 * Everything here runs inside `onDraw`, so the class allocates nothing per frame: paints,
 * gradient shaders, and scratch rectangles are created once and mutated in place, and the
 * gradients are defined over a unit span and positioned with a reusable [Matrix] rather
 * than being rebuilt for each spine's changing width.
 *
 * Text sizes are quantised to [TEXT_SIZE_QUANTUM] pixels. Glyph rasterisation is cached
 * by the platform per typeface *and size*, so letting a size vary continuously with the
 * lens would miss that cache on every frame; rounding to a fortieth of a pixel is
 * invisible but collapses the working set to a handful of entries per strip.
 */
class PassageFinderPainter(private val metrics: PassageFinderMetrics) {

    /** Repainted when the theme changes; see [applyTheme]. */
    private var isDarkTheme = false
    private var isMonochrome = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val scratchRect = RectF()
    private val shaderMatrix = Matrix()
    private val fontMetrics = Paint.FontMetrics()

    /** Unit-span gradients for the spine relief; positioned per spine via [shaderMatrix]. */
    private val highlightShader = LinearGradient(
        0f, 0f, 1f, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP,
    )
    private val rightShadowShader = LinearGradient(
        0f, 0f, 1f, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP,
    )
    private val bottomShadowShader = LinearGradient(
        0f, 0f, 0f, 1f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP,
    )

    /** Rebuilt on size or theme change — its extent is fixed, unlike the spine gradients. */
    private var panelShader: LinearGradient? = null
    private var panelShaderTop = Float.NaN
    private var panelShaderBottom = Float.NaN
    private var panelShaderColor = 0

    /** Opaque backdrop behind the strips; tracks the day/night theme. */
    private val panelColor: Int
        get() = if (isDarkTheme) 0xF0121212.toInt() else 0xF0F0F0F0.toInt()

    /**
     * Accent for the selection border. Cells sit on a white background, so black reads
     * clearly on e-ink; the red accent is used only in normal colour mode.
     */
    private val selectionColor: Int
        get() = if (isMonochrome) Color.BLACK else Color.RED

    fun applyTheme(darkTheme: Boolean, monochrome: Boolean) {
        if (darkTheme != isDarkTheme) panelShader = null
        isDarkTheme = darkTheme
        isMonochrome = monochrome
    }

    // ---- Panel ---------------------------------------------------------------------

    /**
     * Draws the gradient backdrop: transparent at the top, fully opaque from 15% down.
     * [top] is the panel's top edge; it extends to [bottom].
     *
     * [fadeLeft] and [fadeRight] soften the corresponding vertical edge over that many
     * pixels, each applied only where the panel does not reach the screen edge — so a
     * stack anchored to the right of a landscape screen fades on its left, one anchored
     * left fades on its right, and a portrait panel spanning the full width fades on
     * neither. Where the reader shows through beside the stack, a hard vertical edge
     * cutting down the middle of the text reads as a rendering seam rather than a
     * backdrop; the top already fades for the same reason.
     */
    fun drawPanel(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        fadeLeft: Float,
        fadeRight: Float,
    ) {
        val colour = panelColor
        // Keyed on both edges, not just the top. On a screen too short to hold the panel
        // the top is pinned to the toolbar, so a resize that changes only the height —
        // dragging a split-screen divider, a foldable posture change — moves the bottom
        // while leaving the top exactly where it was. Keying on the top alone would keep a
        // gradient built for the old height and put the fade in the wrong place.
        if (panelShader == null ||
            panelShaderTop != top ||
            panelShaderBottom != bottom ||
            panelShaderColor != colour
        ) {
            panelShader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(Color.TRANSPARENT, colour, colour),
                floatArrayOf(0f, 0.15f, 1f),
                Shader.TileMode.CLAMP,
            )
            panelShaderTop = top
            panelShaderBottom = bottom
            panelShaderColor = colour
        }
        gradientPaint.shader = panelShader

        // Never let the two ramps meet, or the panel would have no solid core left.
        val half = (right - left) / 2f
        val rampLeft = fadeLeft.coerceIn(0f, half)
        val rampRight = fadeRight.coerceIn(0f, half)

        gradientPaint.alpha = 255
        canvas.drawRect(left + rampLeft, top, right - rampRight, bottom, gradientPaint)
        drawEdgeRamp(canvas, left, top, bottom, rampLeft, towardsRight = true)
        drawEdgeRamp(canvas, right, top, bottom, rampRight, towardsRight = false)

        gradientPaint.shader = null
        gradientPaint.alpha = 255
    }

    /**
     * Ramps the panel from transparent at [edgeX] to solid [width] pixels inwards.
     *
     * Drawn as slices, with the shader supplying the vertical fade and the paint's alpha
     * the horizontal one. A mask shader would express the product of the two directly, but
     * composing two gradients of the same type is not reliably hardware accelerated below
     * API 28 — and this app still supports API 23 — while an offscreen layer would cost a
     * buffer on every frame of a widget whose whole point is that it is cheap to draw.
     * Slices allocate nothing, and the eased ramp puts the finest steps at the faint end
     * where banding would otherwise show.
     */
    private fun drawEdgeRamp(
        canvas: Canvas,
        edgeX: Float,
        top: Float,
        bottom: Float,
        width: Float,
        towardsRight: Boolean,
    ) {
        if (width <= 0f) return
        val step = width / PANEL_FADE_SLICES
        for (i in 0 until PANEL_FADE_SLICES) {
            val t = (i + 0.5f) / PANEL_FADE_SLICES
            gradientPaint.alpha = ((t * t * (3f - 2f * t)) * 255f).roundToInt().coerceIn(0, 255)
            val offset = i * step
            if (towardsRight) {
                canvas.drawRect(edgeX + offset, top, edgeX + offset + step, bottom, gradientPaint)
            } else {
                canvas.drawRect(edgeX - offset - step, top, edgeX - offset, bottom, gradientPaint)
            }
        }
    }

    // ---- Book strip ----------------------------------------------------------------

    /**
     * Draws one book spine.
     *
     * @param proximity linear lens proximity, driving fade, relief and text weight.
     * @param sizeFactor bell-shaped magnification, driving width and height.
     * @param focusFactor 1 for the selected spine, 0 one place away; raises it clear of
     *   the shelf so the selection is unmistakable.
     * @param isGroupStart whether a divider marks the start of a new biblical category.
     * @param isOpenBook whether this is the book currently open in the reader.
     */
    fun drawBookSpine(
        canvas: Canvas,
        book: PassageFinderDataSource.BookInfo,
        left: Float,
        width: Float,
        bottom: Float,
        proximity: Float,
        sizeFactor: Float,
        focusFactor: Float,
        isGroupStart: Boolean,
        isOpenBook: Boolean,
    ) {
        val height = lerp(metrics.spineMinHeight, metrics.spineMaxHeight, sizeFactor) +
            metrics.spineFocusOvershoot * focusFactor
        val top = bottom - height

        // The Compose spine sat in a graphicsLayer with this alpha. Folding it into each
        // element instead of taking a saveLayer avoids an offscreen buffer per spine —
        // sixty-six of those per frame would dominate the frame time.
        val layerAlpha = (0.85f + 0.15f * proximity).coerceIn(0.85f, 1f)

        fillPaint.shader = null
        fillPaint.color = spineColour(book, proximity)
        fillPaint.alpha = (Color.alpha(fillPaint.color) * layerAlpha).roundToInt()
        scratchRect.set(left, top, left + width, bottom)
        canvas.drawRoundRect(
            scratchRect, metrics.spineCornerRadius, metrics.spineCornerRadius, fillPaint,
        )

        // 3D relief. Each gradient is the unit-span shader mapped onto its slice of the spine.
        drawSpineGradient(
            canvas, highlightShader, horizontal = true,
            x0 = left, y0 = top, x1 = left + width * 0.3f, y1 = bottom,
            alpha = 0.25f * proximity * layerAlpha,
        )
        drawSpineGradient(
            canvas, rightShadowShader, horizontal = true,
            x0 = left + width * 0.7f, y0 = top, x1 = left + width, y1 = bottom,
            alpha = 0.2f * proximity * layerAlpha,
        )
        drawSpineGradient(
            canvas, bottomShadowShader, horizontal = false,
            x0 = left, y0 = top + height * 0.85f, x1 = left + width, y1 = bottom,
            alpha = 0.15f * proximity * layerAlpha,
        )

        if (isGroupStart) {
            strokePaint.shader = null
            strokePaint.color = Color.BLACK
            strokePaint.alpha = (0.5f * 255 * layerAlpha).roundToInt()
            strokePaint.strokeWidth = metrics.groupDividerWidth
            // Tucked in by the spine's corner radius at both ends, so the divider stops
            // where the rounded corner starts instead of overhanging it.
            val inset = metrics.spineCornerRadius
            canvas.drawLine(left, top + inset, left, bottom - inset, strokePaint)
        }

        if (isOpenBook) {
            // Monochrome keeps the marker grayscale — black on the light e-ink panel;
            // the blue accent appears only in normal day mode.
            fillPaint.color = when {
                isDarkTheme -> Color.WHITE
                isMonochrome -> Color.BLACK
                else -> 0xFF1565C0.toInt()
            }
            fillPaint.alpha = (255 * layerAlpha).roundToInt()
            canvas.drawRect(
                left + metrics.openBookMarkerInset,
                bottom - metrics.openBookMarkerHeight,
                left + width - metrics.openBookMarkerInset,
                bottom,
                fillPaint,
            )
        }

        drawSpineLabel(canvas, book.shortName, left, top, width, height, proximity, layerAlpha)
    }

    /** Per-book colour with a small deterministic variation so neighbours stay distinguishable. */
    private fun spineColour(book: PassageFinderDataSource.BookInfo, proximity: Float): Int {
        val base = if (isMonochrome) {
            val shade = (book.category.monochromeShade * 255).roundToInt().coerceIn(0, 255)
            Color.rgb(shade, shade, shade)
        } else {
            book.category.color
        }
        val variation = ((book.shortName.hashCode() and 0xFF) % 30 - 15)
        // The per-channel weights below add a warm tint in colour mode. In monochrome the
        // base is grayscale, so every channel gets the same shift — differing weights would
        // re-introduce a colour cast on e-ink.
        val gWeight = if (isMonochrome) 1f else 0.7f
        val bWeight = if (isMonochrome) 1f else 0.5f
        val alpha = ((0.75f + 0.25f * proximity) * 255).roundToInt().coerceIn(0, 255)
        return Color.argb(
            alpha,
            (Color.red(base) + variation).coerceIn(0, 255),
            (Color.green(base) + (variation * gWeight).roundToInt()).coerceIn(0, 255),
            (Color.blue(base) + (variation * bWeight).roundToInt()).coerceIn(0, 255),
        )
    }

    /** Maps a unit-span gradient onto the given rectangle and fills it. */
    private fun drawSpineGradient(
        canvas: Canvas,
        shader: LinearGradient,
        horizontal: Boolean,
        x0: Float, y0: Float, x1: Float, y1: Float,
        alpha: Float,
    ) {
        if (alpha <= 0.004f) return
        shaderMatrix.reset()
        if (horizontal) {
            shaderMatrix.setScale((x1 - x0).coerceAtLeast(0.01f), 1f)
            shaderMatrix.postTranslate(x0, 0f)
        } else {
            shaderMatrix.setScale(1f, (y1 - y0).coerceAtLeast(0.01f))
            shaderMatrix.postTranslate(0f, y0)
        }
        shader.setLocalMatrix(shaderMatrix)
        gradientPaint.shader = shader
        gradientPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        canvas.drawRect(x0, y0, x1, y1, gradientPaint)
        gradientPaint.shader = null
    }

    /**
     * Draws the book abbreviation rotated a quarter turn, as on a real spine.
     *
     * The label is clipped to the spine so a long abbreviation cannot bleed into its
     * neighbours — the Compose version got this from laying the text out in a box of the
     * spine's height before rotating it.
     */
    private fun drawSpineLabel(
        canvas: Canvas,
        label: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        proximity: Float,
        layerAlpha: Float,
    ) {
        val centreX = left + width / 2f
        val centreY = top + height / 2f
        textPaint.shader = null
        textPaint.textSize = quantiseTextSize(
            lerp(metrics.spineMinTextSize, metrics.spineMaxTextSize, proximity)
        )
        // Only the spine at the exact centre of the lens goes bold.
        textPaint.typeface = if (proximity > 0.95f) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.color = Color.BLACK
        val textAlpha = (0.45f + 0.55f * proximity).coerceIn(0.45f, 1f)
        textPaint.alpha = (textAlpha * layerAlpha * 255).roundToInt().coerceIn(0, 255)

        canvas.save()
        canvas.clipRect(left, top, left + width, top + height)
        canvas.rotate(-90f, centreX, centreY)
        textPaint.getFontMetrics(fontMetrics)
        val baseline = centreY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(label, centreX, baseline, textPaint)
        canvas.restore()
    }

    // ---- Chapter and verse cells ---------------------------------------------------

    /**
     * Draws a numeric cell for the chapter or verse strip.
     *
     * Cells keep a constant layout pitch and grow about their own centre, so [scale]
     * drives both the box and the glyph size — matching the Compose `graphicsLayer`
     * scale, which likewise magnified the rasterised text along with the box.
     *
     * The box is square for one and two digits but widens to fit whatever the digits
     * actually measure, so a three-digit chapter like Psalm 150 gets a wider plate rather
     * than smaller numerals. Sizing from the measurement rather than from the digit count
     * matters because the box is in dp while the text is in sp: at a large system font
     * scale even two digits outgrow the square, and the same rule catches that.
     *
     * @param borderAlpha 0 hides the selection border entirely.
     */
    /**
     * Width the plate for [text] is drawn at, magnification included.
     *
     * Exposed because touch and accessibility have to agree with what is on screen: cells
     * sit on a small constant pitch but the centred one is drawn nearly three times that,
     * so anything deriving a cell's extent from the pitch would hand most of the focused
     * cell's visible area to its neighbour.
     *
     * Width comes from the digit advance rather than measureText, so every number with
     * the same digit count gets the same plate even on OEM fonts with proportional
     * figures — otherwise "111" and "888" would sit in visibly different boxes as they
     * scrolled past. Measured bold, the widest the cell ever draws, so the plate does not
     * twitch when a cell goes bold on reaching the centre.
     */
    fun cellWidth(text: String, cellSize: Float, baseTextSize: Float, scale: Float): Float {
        val height = cellSize * scale
        val padding = metrics.cellTextPaddingRatio * height
        val maxWidth = metrics.cellMaxAspect * height
        val textWidth = text.length * digitAdvanceRatio * quantiseTextSize(baseTextSize * scale)
        return if (textWidth + padding * 2f > maxWidth) maxWidth
        else maxOf(height, textWidth + padding * 2f)
    }

    fun drawNumberCell(
        canvas: Canvas,
        text: String,
        centreX: Float,
        centreY: Float,
        cellSize: Float,
        baseTextSize: Float,
        scale: Float,
        alpha: Float,
        isSelected: Boolean,
        emphasised: Boolean,
        borderAlpha: Float,
    ) {
        val height = cellSize * scale
        val halfHeight = height / 2f
        var textSize = quantiseTextSize(baseTextSize * scale)
        val padding = metrics.cellTextPaddingRatio * height
        val maxWidth = metrics.cellMaxAspect * height

        val textWidth = text.length * digitAdvanceRatio * textSize
        if (textWidth + padding * 2f > maxWidth) {
            // Only reachable at extreme font scales; shrink the text to fit the cap.
            textSize = quantiseTextSize(textSize * (maxWidth - padding * 2f) / textWidth)
        }
        val width = cellWidth(text, cellSize, baseTextSize, scale)

        val halfWidth = width / 2f
        scratchRect.set(
            centreX - halfWidth, centreY - halfHeight, centreX + halfWidth, centreY + halfHeight,
        )
        // Corner radius stays proportional to the cell rather than becoming a stadium —
        // a fully rounded pill would read as a different component from its neighbours.
        val radius = metrics.cellCornerRadius * scale

        fillPaint.shader = null
        fillPaint.color = Color.WHITE
        val bgAlpha = if (isSelected) 0.95f else 0.85f
        fillPaint.alpha = (bgAlpha * alpha * 255).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(scratchRect, radius, radius, fillPaint)

        if (borderAlpha > 0f) {
            strokePaint.shader = null
            strokePaint.color = selectionColor
            // Outer halo, then the solid core on top of it.
            strokePaint.strokeWidth = metrics.cellBorderHaloWidth * scale
            strokePaint.alpha = (borderAlpha * 0.3f * alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(scratchRect, radius, radius, strokePaint)
            strokePaint.strokeWidth = metrics.cellBorderCoreWidth * scale
            strokePaint.alpha = (borderAlpha * 0.8f * alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(scratchRect, radius, radius, strokePaint)
        }

        textPaint.shader = null
        textPaint.textSize = textSize
        textPaint.typeface = if (emphasised) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.color = Color.BLACK
        textPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        textPaint.getFontMetrics(fontMetrics)
        val baseline = centreY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, centreX, baseline, textPaint)
    }

    /**
     * Widest digit advance for the bold face, per pixel of text size.
     *
     * Measured once at a large size and normalised, since advance scales linearly. Bold
     * is the widest the cell ever renders, so sizing from it means a cell keeps the same
     * plate whether or not it is currently the centred, emphasised one.
     */
    private val digitAdvanceRatio: Float by lazy {
        val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = DIGIT_PROBE_SIZE
        }
        var widest = 0f
        for (digit in '0'..'9') {
            widest = maxOf(widest, probe.measureText(digit.toString()))
        }
        widest / DIGIT_PROBE_SIZE
    }

    // ---- Skeleton ------------------------------------------------------------------

    /**
     * Draws placeholder spines shown between the tap and the book list arriving.
     *
     * This is what makes the widget appear within a frame even on a cold data cache. It
     * is deliberately static: the initial book list load is short, and a looping
     * animation that stalls would read as a freeze rather than as progress.
     */
    fun drawSkeleton(canvas: Canvas, left: Float, right: Float, bottom: Float) {
        fillPaint.shader = null
        fillPaint.color = if (isDarkTheme) Color.WHITE else Color.BLACK
        val pitch = metrics.skeletonSpineWidth + metrics.spineGap
        val centre = (left + right) / 2f
        var x = centre - ((centre - left) / pitch).toInt() * pitch
        while (x < right) {
            // Fade the placeholders out towards the edges, echoing the lens falloff.
            val distance = abs(x - centre) / metrics.bookLensRadius
            val proximity = (1f - distance).coerceIn(0f, 1f)
            val height = lerp(
                metrics.spineMinHeight, metrics.spineMaxHeight,
                bellFalloff(distance, metrics.bookLensFalloff),
            )
            fillPaint.alpha = ((0.06f + 0.10f * proximity) * 255).roundToInt()
            scratchRect.set(x, bottom - height, x + metrics.skeletonSpineWidth, bottom)
            canvas.drawRoundRect(
                scratchRect, metrics.spineCornerRadius, metrics.spineCornerRadius, fillPaint,
            )
            x += pitch
        }
    }

    // ---- Preview bubble ------------------------------------------------------------

    /** Draws the bubble background: a soft drop shadow under a translucent rounded panel. */
    fun drawBubbleBackground(canvas: Canvas, rect: RectF, alpha: Float) {
        val radius = metrics.bubbleCornerRadius
        // Approximate elevation with a few offset translucent passes. A real blurred
        // shadow would need either setShadowLayer (hardware-accelerated only from API 28)
        // or a software layer for the whole view, and neither is worth the cost here.
        fillPaint.shader = null
        fillPaint.color = Color.BLACK
        val steps = 3
        for (step in steps downTo 1) {
            val spread = metrics.bubbleElevation * step / steps
            fillPaint.alpha = (0.06f * alpha * 255).roundToInt().coerceIn(0, 255)
            scratchRect.set(
                rect.left - spread * 0.5f,
                rect.top + spread * 0.25f,
                rect.right + spread * 0.5f,
                rect.bottom + spread * 0.75f,
            )
            canvas.drawRoundRect(scratchRect, radius + spread, radius + spread, fillPaint)
        }

        fillPaint.color = Color.BLACK
        fillPaint.alpha = (0.75f * alpha * 255).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
    }

    /** Draws the bubble's reference line, e.g. "Gen 1:1", centred at [centreX]. */
    fun drawBubbleReference(canvas: Canvas, text: String, centreX: Float, top: Float, alpha: Float) {
        textPaint.shader = null
        textPaint.textSize = metrics.bubbleReferenceTextSize
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        textPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        textPaint.getFontMetrics(fontMetrics)
        canvas.drawText(text, centreX, top - fontMetrics.ascent, textPaint)
    }

    /** Height of one reference line, for laying the bubble out before drawing it. */
    fun referenceLineHeight(): Float {
        textPaint.textSize = metrics.bubbleReferenceTextSize
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.getFontMetrics(fontMetrics)
        return fontMetrics.descent - fontMetrics.ascent
    }

    /** Width of the reference line, for sizing the bubble. */
    fun measureReference(text: String): Float {
        textPaint.textSize = metrics.bubbleReferenceTextSize
        textPaint.typeface = Typeface.DEFAULT_BOLD
        return textPaint.measureText(text)
    }

    private fun quantiseTextSize(size: Float): Float =
        (size / TEXT_SIZE_QUANTUM).roundToInt() * TEXT_SIZE_QUANTUM

    private companion object {
        /**
         * Slices per panel edge ramp. Enough that the alpha step is invisible against the
         * text showing through, few enough that the extra draws stay negligible.
         */
        const val PANEL_FADE_SLICES = 48

        /**
         * Glyph-cache granularity for animated text sizes. Fine enough that the growth
         * still looks continuous, coarse enough that the platform's per-size glyph cache
         * keeps hitting during a scroll.
         */
        const val TEXT_SIZE_QUANTUM = 0.25f

        /** Large enough that the measured digit advance is precise once normalised. */
        const val DIGIT_PROBE_SIZE = 100f
    }
}
