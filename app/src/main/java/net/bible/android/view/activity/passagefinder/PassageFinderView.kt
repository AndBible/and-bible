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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import android.widget.OverScroller
import net.bible.android.activity.R
import net.bible.android.control.passagefinder.PassageFinderDataSource
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import kotlin.math.abs

/**
 * The passage finder overlay, drawn entirely by hand.
 *
 * This replaces a Jetpack Compose implementation that was visually right but took
 * seconds to appear: Compose was the only user of that runtime in the app, so opening
 * the finder paid for loading and interpreting the whole Compose stack before the first
 * frame. Everything used here — [Canvas], [android.graphics.Paint], [OverScroller],
 * [VelocityTracker] — is framework code that is already loaded and AOT-compiled on every
 * device, so the first frame costs a layout pass and one `onDraw`.
 *
 * The view is a passive renderer: it holds no Bible data of its own and owns no
 * coroutines. [PassageFinderLauncher] pushes state in through [render] and [setBooks],
 * and user intent comes back out through the callbacks. Scroll offsets are the one piece
 * of state that lives here, exactly as they lived in the Compose implementation — hoisting
 * them into the ViewModel would create a write-back loop with the scroll animations.
 *
 * Layout is bottom-anchored. The vertical rects are computed fresh in [onDraw] from the
 * animated strip heights, while everything that depends only on the viewport and the
 * opening gesture is settled once in [updateSessionGeometry]. There are no child views, so
 * nothing needs measuring or laying out per frame.
 */
class PassageFinderView(context: Context) : View(context) {

    // ---- Collaborators -------------------------------------------------------------

    private val metrics = PassageFinderMetrics(resources.displayMetrics)
    private val painter = PassageFinderPainter(metrics)
    private val bubble = PreviewBubbleRenderer(metrics, painter)
    private val haptics = HapticController(this)

    private val bookLane = LensLane()
    private val chapterLane = UniformLane()
    private val verseLane = UniformLane()

    // The numeric strips damp their flings by their own peak magnification. Their cells
    // keep a small constant pitch but are drawn up to 2.8x that at the centre, so the
    // numbers stream past far faster than the strip looks like it is moving and an
    // ordinary fling overshoots to the end of a long chapter. Dragging needs no such
    // correction — the finger stays on the content — and the book strip already divides
    // by its live magnification while scrolling.
    private val bookScroll = LaneScroller(bookLane)
    private val chapterScroll = LaneScroller(chapterLane, 1f / metrics.chapterMaxScale)
    private val verseScroll = LaneScroller(verseLane, 1f / metrics.verseMaxScale)

    private val a11y = PassageFinderA11yHelper(this)

    // ---- Callbacks -----------------------------------------------------------------

    /** Invoked when the user taps outside the strips or swipes down past the book level. */
    var onDismiss: (() -> Unit)? = null

    /** Invoked when the user commits the current selection. */
    var onConfirm: (() -> Unit)? = null

    /** Invoked with the newly centred book index once a book scroll settles. */
    var onBookSelected: ((Int) -> Unit)? = null

    /** Invoked with the newly centred chapter once a chapter scroll settles. */
    var onChapterSelected: ((Int) -> Unit)? = null

    /** Invoked with the newly centred verse once a verse scroll settles. */
    var onVerseSelected: ((Int) -> Unit)? = null

    /** Invoked on an upward swipe; drills book → chapter → verse. */
    var onDrillDown: (() -> Unit)? = null

    /** Invoked on a downward swipe. Returns false when already at book level, which dismisses. */
    var onDrillUp: (() -> Boolean)? = null

    /**
     * Receives touches that land clear of the widget, to be replayed on the reader
     * showing through above it. Leave null to keep every touch inside the overlay.
     */
    var onReaderTouch: ((MotionEvent) -> Unit)? = null

    /**
     * Invoked the moment the user puts a finger on the widget itself.
     *
     * Only fired for touches the widget keeps — a strip or the bubble. Anything landing
     * clear of those is forwarded to the reader instead, which stops its own fling.
     */
    var onWidgetTouched: (() -> Unit)? = null

    // ---- State mirrored from the ViewModel -----------------------------------------

    private var books: List<PassageFinderDataSource.BookInfo> = emptyList()
    private var chapterCounts: IntArray = IntArray(0)
    private var state = PassageFinderUiState()
    private var previewText: PreviewVerseText = PreviewVerseText.None

    /** True between the tap and the book list arriving; draws the skeleton. */
    private var loading = false

    /**
     * "1", "2", ... shared by both number strips, and the bubble's reference line.
     *
     * Both are otherwise rebuilt every frame — around fifty short-lived strings per frame
     * while a strip is flinging — which is exactly the steady garbage the rest of this
     * widget is written to avoid.
     */
    private var cellLabels: Array<String> = emptyArray()
    private var bubbleReference: String = ""

    /**
     * Set by [show] and consumed by the first [render] of that session.
     *
     * A fresh open must place the strips at the passage the reader is on, not glide there
     * from wherever the previous session left them: the widget is meant to appear already
     * pointing at the current verse. The view cannot infer that from [state] alone, because
     * the launcher cancels its state collector before dismissing the ViewModel, so no
     * `visible = false` render ever arrives and the previous session's state looks live.
     *
     * A flag rather than clearing [state], which is what an earlier fix did: [state] is what
     * tells the strips which cell is the committed selection, and it also drives the tap
     * hit-test priority, the accessibility nodes' selected flags and the bubble reference.
     * Clearing it desynchronises all of those from the lanes it just re-centred.
     */
    private var pendingOpenSnap = false

    // ---- View-local interaction state ----------------------------------------------

    /**
     * Progressive reveal, ported from the Compose widget:
     *  - while the user scrolls books, the chapter and verse strips hide;
     *  - while the user scrolls chapters, the verse strip hides;
     *  - after the book changes, the verse strip stays hidden until a chapter is picked,
     *    so the widget does not pre-commit to verse 1 of the new book.
     */
    private var bookScrolling = false
    private var chapterScrolling = false
    private var verseRevealed = true

    private var isDarkTheme = false
    private var isMonochrome = false
    private var disableAnimations = false

    // ---- Animations ----------------------------------------------------------------

    /** Slide-in progress for the whole stack, 0 fully off the bottom edge to 1 in place. */
    private val showAnim = Tween1D(0f, durationMs = SHOW_DURATION_MS)
    private val chapterHeight = Spring1D(metrics.chapterStripHeight)
    private val chapterAlpha = Spring1D(1f)
    private val verseHeight = Spring1D(metrics.verseStripHeight)
    private val verseAlpha = Spring1D(1f)
    private val bubbleAlpha = Tween1D(0f, durationMs = BUBBLE_FADE_IN_MS)

    /** Selection-border fade for the cell currently under the centre of each strip. */
    private val chapterBorder = Tween1D(1f, durationMs = BORDER_FADE_MS)
    private val verseBorder = Tween1D(1f, durationMs = BORDER_FADE_MS)
    private var chapterBorderIndex = -1
    private var verseBorderIndex = -1

    private var lastFrameNanos = 0L

    /** Seconds elapsed since the previous frame; shared by every animator this frame. */
    private var lastFrameDelta = 0f

    /** Set while the exit animation runs; the view hides itself when it completes. */
    private var dismissing = false

    // ---- Session geometry, fixed between size changes ------------------------------

    /**
     * X the stack is centred on, in overlay coordinates: where the opening gesture was.
     * Null for an open with no gesture behind it, which centres the stack instead.
     *
     * Fixed for the session. The finder is a one-thumb control and, once its width is
     * capped, a centred stack sits under neither thumb; anchoring it to the gesture that
     * summoned it puts it under whichever thumb the user actually reached with, which is
     * also why this needs no handedness setting. In portrait the stack is as wide as the
     * view, so the clamp in [PassageFinderLayoutRules.anchoredContentLeft] discards this
     * entirely and the layout is what it always was.
     */
    private var anchorX: Float? = null

    /**
     * Bottom of the app toolbar in overlay coordinates — the highest the bubble may go
     * before it starts covering chrome. Supplied by the launcher on each [show].
     */
    private var safeTop = 0f

    /** Top of the screen's own content area; the bubble is never allowed above this. */
    private var screenTop = 0f

    private var contentLeft = 0f
    private var contentRight = 0f

    /**
     * How far the panel fades out along each vertical edge.
     *
     * Only an edge with the reader showing through beside it gets one: an edge flush with
     * the screen has nothing to dissolve into, and fading it would just thin the backdrop
     * at the screen border. So a right-anchored stack fades on its left, a left-anchored
     * one on its right, a centred stack on both, and a full-width portrait panel on
     * neither.
     */
    private var panelFadeLeft = 0f
    private var panelFadeRight = 0f

    /** Top edge of the gradient panel, held clear of the toolbar. */
    private var panelTop = 0f

    // ---- Geometry, recomputed per frame --------------------------------------------

    private val bookRect = RectF()
    private val chapterRect = RectF()
    private val verseRect = RectF()
    private val bubbleRect = RectF()

    /** Vertical offset of the whole stack during the show/hide slide. */
    private var slideOffset = 0f

    // ---- Touch ---------------------------------------------------------------------

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    /** Above this a stationary finger is a resting thumb rather than a tap; see the UP handler. */
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f

    /** True between an accepted touch-down and the end of that gesture. */
    private var gestureActive = false

    /** True while the current gesture is being handed to the reader behind the overlay. */
    private var readerGesture = false

    /**
     * True between touch-down and lift on a gesture the widget is handling itself.
     *
     * Reader tracking is suspended while this holds, so an update arriving mid-drag
     * cannot pull a strip out from under the finger.
     */
    var isBeingTouched = false
        private set

    /**
     * True when no finger is on the widget and none of its strips are still moving.
     *
     * The reader-follow update uses this rather than [isBeingTouched] alone: a released
     * fling is still the user's gesture playing out, and letting a late scroll report
     * re-centre a strip mid-flight would cancel the throw and pull it back.
     */
    val isIdle: Boolean
        get() = !isBeingTouched && !bookScroll.isMoving &&
            !chapterScroll.isMoving && !verseScroll.isMoving

    /** null until the gesture commits to an axis; then true for vertical. */
    private var lockedVertical: Boolean? = null
    private var cumulativeVertical = 0f
    private var activeScroller: LaneScroller? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.passage_finder_a11y_label)
        ViewCompat.setAccessibilityDelegate(this, a11y)
        // Cell pitch depends only on density, so fix it up front: the lanes clamp their
        // scroll against it whenever an item count arrives, which can precede the first
        // book list.
        chapterLane.pitchPx = metrics.chapterCellSize
        verseLane.pitchPx = metrics.verseCellSize
    }

    // ---- Public API ----------------------------------------------------------------

    /**
     * Installs the book list and its per-book chapter counts.
     *
     * The counts are supplied rather than looked up here because each one reaches through
     * to JSword, which on a cold module means disk reads; the launcher gathers them off
     * the main thread. Passing them in also keeps spine widths stable for the lifetime of
     * the list instead of being re-derived every frame.
     */
    fun setBooks(books: List<PassageFinderDataSource.BookInfo>, chapterCounts: IntArray) {
        require(books.size == chapterCounts.size) {
            "chapterCounts must have one entry per book (${books.size} books, ${chapterCounts.size} counts)"
        }
        this.books = books
        this.chapterCounts = chapterCounts
        loading = books.isEmpty()
        rebuildBookLane()
        invalidate()
    }

    /** Pushes a new ViewModel state and preview text into the view. */
    fun render(newState: PassageFinderUiState, newPreviewText: PreviewVerseText) {
        val previous = state
        state = newState
        previewText = newPreviewText

        chapterLane.itemCount = newState.chapterCount.coerceAtLeast(1)
        verseLane.itemCount = newState.verseCount.coerceAtLeast(1)

        // First render of a session: place the strips outright rather than diffing against
        // a state left over from the previous one.
        val opening = pendingOpenSnap
        pendingOpenSnap = false

        // Animate a re-centre only when the strips were already showing real content.
        // On a cold open the widget first renders a loading placeholder, which is visible
        // but bookless and parked at book 0 / chapter 1 / verse 1; treating that as a
        // starting position would send the strips spinning all the way to the current
        // verse at the very moment the books appear. Snap instead — that placeholder was
        // never a position the user chose, and neither is a stale selection carried over
        // from the last time the widget was open.
        val animate = !opening && previous.visible && previous.books.isNotEmpty()

        // Re-center any strip whose selection moved for a reason other than its own
        // settle — a tap, a drill, or a book change resetting chapter and verse to 1.
        //
        // On an open every strip is re-centred regardless of the diff. The diff only
        // answers "did the selection move", which is not the same question as "is the lane
        // where the selection says it is": closing the widget mid-fling stops the scrollers
        // without letting them settle, so a lane can be left off its snap point while the
        // state still names the item it started from.
        if (opening || newState.selectedBookIndex != previous.selectedBookIndex) {
            recenter(bookScroll, newState.selectedBookIndex, animate = animate)
        }
        if (opening ||
            newState.selectedChapter != previous.selectedChapter ||
            newState.selectedBookIndex != previous.selectedBookIndex
        ) {
            recenter(chapterScroll, newState.selectedChapter - 1, animate = animate)
        }
        if (opening ||
            newState.selectedVerse != previous.selectedVerse ||
            newState.selectedChapter != previous.selectedChapter ||
            newState.selectedBookIndex != previous.selectedBookIndex
        ) {
            recenter(verseScroll, newState.selectedVerse - 1, animate = animate)
        }

        val book = books.getOrNull(newState.selectedBookIndex)
        if (book != null) {
            bubbleReference = "${book.shortName} ${newState.selectedChapter}:${newState.selectedVerse}"
        }

        bubbleAlpha.durationMs = if (newState.showPreview) BUBBLE_FADE_IN_MS else BUBBLE_FADE_OUT_MS
        // The strips have moved, so TalkBack's cached node bounds no longer describe what
        // is on screen. Without this its focus rectangles drift away from the cells.
        a11y.invalidateRoot()
        invalidate()
    }

    /**
     * Shows the overlay, sliding the stack up from the bottom edge.
     *
     * Reads the theme and animation settings on every open, since the user can change
     * them between openings without the view being recreated.
     *
     * @param anchorX x of the gesture that opened the finder, in overlay coordinates, or
     *   null when there was no gesture — an accessibility action, a keyboard, or the
     *   fallback path taken when the module turns out to have no books. The stack is
     *   placed under the gesture; with none it falls back to the centre.
     * @param safeTop bottom edge of the app toolbar in overlay coordinates, or the
     *   status-bar inset when the toolbar is hidden. The panel and the preview bubble both
     *   stay below it.
     * @param screenTop the status-bar inset. The bubble may cover the toolbar on a screen
     *   too short to hold it below [safeTop], but never goes above this.
     */
    fun show(anchorX: Float?, safeTop: Float, screenTop: Float) {
        isDarkTheme = ScreenSettings.nightMode
        isMonochrome = CommonUtils.settings.monochromeMode
        disableAnimations = CommonUtils.settings.disableAnimations
        painter.applyTheme(isDarkTheme, isMonochrome)
        applyAnimationSetting()

        this.screenTop = screenTop.coerceAtLeast(0f)
        this.safeTop = safeTop.coerceAtLeast(this.screenTop)
        this.anchorX = anchorX
        metrics.configure(height.toFloat())
        updateSessionGeometry()

        dismissing = false
        bookScrolling = false
        chapterScrolling = false
        verseRevealed = true

        // Tell the next render this is a fresh open, so it places the strips outright
        // instead of animating them in from whatever the previous session selected. The
        // coordinator reset goes with it: a settle in one frame and a dismiss before the
        // resulting state change is delivered leaves the scroll-settle flag set, which
        // would silently swallow the first re-centre of the new session.
        pendingOpenSnap = true
        bookScroll.coordinator.reset()
        chapterScroll.coordinator.reset()
        verseScroll.coordinator.reset()
        chapterHeight.snapTo(metrics.chapterStripHeight)
        chapterAlpha.snapTo(1f)
        verseHeight.snapTo(metrics.verseStripHeight)
        verseAlpha.snapTo(1f)
        bubbleAlpha.snapTo(0f)
        chapterBorderIndex = -1
        verseBorderIndex = -1

        showAnim.snapTo(0f)
        showAnim.animateTo(1f)
        lastFrameNanos = 0L
        visibility = VISIBLE
        invalidate()
    }

    /** Starts the exit animation; the view hides itself once it finishes. */
    fun hide() {
        if (dismissing) return
        dismissing = true
        cancelScrolls()
        showAnim.animateTo(0f)
        if (disableAnimations) finishHide()
        invalidate()
    }

    /**
     * True while the overlay is open and interactive.
     *
     * Deliberately false for the duration of the exit animation, even though the stack is
     * still sliding down on screen: once dismissed it must stop claiming touches, stop
     * publishing accessibility nodes, and stop counting as open for the fling gesture
     * that reopens it. It is not a question of whether any pixels are visible.
     */
    val isShowing: Boolean
        get() = visibility == VISIBLE && !dismissing

    private fun finishHide() {
        dismissing = false
        // INVISIBLE rather than GONE: a GONE view is skipped during measurement, so every
        // open and close would force a fresh layout pass over the whole DrawerLayout.
        // INVISIBLE keeps the overlay laid out and costs only a skipped draw.
        visibility = INVISIBLE
    }

    private fun applyAnimationSetting() {
        showAnim.snapping = disableAnimations
        chapterHeight.snapping = disableAnimations
        chapterAlpha.snapping = disableAnimations
        verseHeight.snapping = disableAnimations
        verseAlpha.snapping = disableAnimations
        bubbleAlpha.snapping = disableAnimations
        chapterBorder.snapping = disableAnimations
        verseBorder.snapping = disableAnimations
    }

    // ---- Lane setup ----------------------------------------------------------------

    /**
     * Derives spine base widths from chapter counts: a one-chapter book gets the minimum
     * width and the longest book the maximum, so the strip reads like a shelf where thick
     * books are thick.
     */
    private fun rebuildBookLane() {
        val n = books.size
        val maxChapters = (chapterCounts.maxOrNull() ?: 1).coerceAtLeast(1)
        val widths = FloatArray(n)
        for (i in 0 until n) {
            val fraction = (chapterCounts[i].toFloat() / maxChapters).coerceIn(0f, 1f)
            widths[i] = lerp(metrics.spineBaseMinWidth, metrics.spineBaseMaxWidth, fraction)
        }
        bookLane.gapPx = metrics.spineGap
        bookLane.lensRadiusPx = metrics.bookLensRadius
        bookLane.lensWidthPx = metrics.spineLensWidth
        bookLane.lensFalloff = metrics.bookLensFalloff
        bookLane.setBaseWidths(widths)
        bookLane.scroll = bookLane.snapPointFor(state.selectedBookIndex)
        chapterLane.scroll = chapterLane.snapPointFor(state.selectedChapter - 1)
        verseLane.scroll = verseLane.snapPointFor(state.selectedVerse - 1)
    }

    /** Animates a lane onto [index] unless the change came from that lane's own settle. */
    private fun recenter(scroller: LaneScroller, index: Int, animate: Boolean) {
        if (index < 0) return
        if (!scroller.coordinator.shouldRecenter()) return
        val target = scroller.lane.snapPointFor(index)
        if (animate && !disableAnimations) {
            scroller.animateTo(target)
        } else {
            scroller.stop()
            scroller.lane.scroll = target
        }
    }

    private fun cancelScrolls() {
        bookScroll.stop()
        chapterScroll.stop()
        verseScroll.stop()
        bookScrolling = false
        chapterScrolling = false
    }

    // ---- Drawing -------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        // Clamp so a stalled frame cannot make the animations jump.
        lastFrameDelta = if (lastFrameNanos == 0L) 0f else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
        }
        lastFrameNanos = now

        // Scroll positions are advanced before layout so the frame drawn is the frame
        // computed — otherwise every strip would render one frame behind the finger.
        var animating = advanceScrollers()
        if (advanceAnimations(lastFrameDelta)) animating = true

        if (dismissing && !showAnim.isAnimating && showAnim.value <= 0f) {
            finishHide()
            return
        }

        layoutStrips()

        canvas.save()
        canvas.translate(0f, slideOffset)

        painter.drawPanel(
            canvas, contentLeft, panelTop, contentRight, height.toFloat(),
            panelFadeLeft, panelFadeRight,
        )

        if (loading) {
            painter.drawSkeleton(canvas, contentLeft, contentRight, bookRect.bottom)
        } else {
            drawBookStrip(canvas)
        }
        drawNumberStrip(canvas, chapterLane, chapterRect, isChapterStrip = true)
        drawNumberStrip(canvas, verseLane, verseRect, isChapterStrip = false)
        drawBubble(canvas)

        canvas.restore()

        if (animating) postInvalidateOnAnimation()
    }

    /** Advances every animator by [dt]; returns true if any still needs frames. */
    private fun advanceAnimations(dt: Float): Boolean {
        var animating = false

        chapterHeight.target = if (bookScrolling) 0f else metrics.chapterStripHeight
        chapterAlpha.target = if (bookScrolling) 0f else 1f
        val hideVerse = bookScrolling || chapterScrolling || !verseRevealed
        verseHeight.target = if (hideVerse) 0f else metrics.verseStripHeight
        verseAlpha.target = if (hideVerse) 0f else 1f

        // The bubble is shown only once a chapter or verse scroll has happened, and is
        // suppressed while the user is picking a book — a book-only preview says nothing.
        bubbleAlpha.animateTo(if (state.showPreview && !bookScrolling) 1f else 0f)

        if (showAnim.advance(dt)) animating = true
        if (chapterHeight.advance(dt)) animating = true
        if (chapterAlpha.advance(dt)) animating = true
        if (verseHeight.advance(dt)) animating = true
        if (verseAlpha.advance(dt)) animating = true
        if (bubbleAlpha.advance(dt)) animating = true
        if (chapterBorder.advance(dt)) animating = true
        if (verseBorder.advance(dt)) animating = true

        return animating
    }

    /** Steps any in-flight fling or snap; returns true if any lane is still moving. */
    private fun advanceScrollers(): Boolean {
        var moving = false
        if (bookScroll.advance()) moving = true
        if (chapterScroll.advance()) moving = true
        if (verseScroll.advance()) moving = true
        return moving
    }

    /**
     * Computes the bottom-anchored stack: book strip at the bottom, then chapter, verse
     * and the bubble above it, separated by a constant gap. Collapsed strips keep their
     * gaps, so the strips above do not shift as one hides.
     *
     * Reads the session geometry rather than deriving it: the horizontal placement, the
     * panel top and the bubble's line budget all depend only on the anchor and the
     * viewport, so they are settled once in [updateSessionGeometry] and left alone here,
     * which runs on every frame.
     */
    private fun layoutStrips() {
        slideOffset = (1f - showAnim.value) * height

        val bookBottom = height - metrics.stackBottomPadding
        bookRect.set(contentLeft, bookBottom - metrics.bookStripHeight, contentRight, bookBottom)

        val chapterBottom = bookRect.top - metrics.stripSpacing
        chapterRect.set(contentLeft, chapterBottom - chapterHeight.value, contentRight, chapterBottom)

        val verseBottom = chapterRect.top - metrics.stripSpacing
        verseRect.set(contentLeft, verseBottom - verseHeight.value, contentRight, verseBottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        metrics.configure(h.toFloat())
        // The anchor is a position within the old width, so rotating carries the stack to
        // the matching place in the new one rather than snapping it back to the centre —
        // the user's thumb has not moved to the middle of the screen either.
        if (oldw > 0 && w != oldw) anchorX = anchorX?.let { it * w / oldw }
        updateSessionGeometry()
    }

    /**
     * Settles everything that depends on the viewport and the opening gesture rather than
     * on the animation clock, so no frame has to work any of it out again.
     *
     * Called from [show] and [onSizeChanged] — the only two moments either can change.
     */
    private fun updateSessionGeometry() {
        val viewWidth = width.toFloat()
        // Resolved here rather than stored, so an open that lands before the first layout
        // pass keeps its anchor instead of having it clamped away against a zero width.
        val anchor = anchorX?.coerceIn(0f, viewWidth) ?: (viewWidth / 2f)
        val contentWidth = minOf(viewWidth, metrics.maxContentWidth)
        contentLeft = PassageFinderLayoutRules.anchoredContentLeft(anchor, contentWidth, viewWidth)
        contentRight = contentLeft + contentWidth
        // The panel is opaque, so letting it run under the toolbar would hide the very
        // chrome the user needs to get back out of the finder.
        panelTop = maxOf(height - metrics.panelHeight, safeTop)
        panelFadeLeft = if (contentLeft > 0.5f) metrics.panelEdgeFade else 0f
        panelFadeRight = if (contentRight < viewWidth - 0.5f) metrics.panelEdgeFade else 0f
        bubble.configure(computeBubbleMaxLines())
    }

    /**
     * How many lines of verse text fit between the toolbar and the verse strip.
     *
     * Derived from measured font metrics rather than a table of heights, so a large system
     * font scale costs the bubble a line instead of pushing it off the top of the screen.
     *
     * Measured against the verse strip's *fully revealed* top rather than its animated
     * one. The strip springs open and shut as the user drills between levels, so budgeting
     * against the live height would walk the line count up and down throughout, rebuilding
     * the bubble's [android.text.StaticLayout] on most frames of every reveal.
     */
    private fun computeBubbleMaxLines(): Int {
        val verseTopTarget = height - metrics.stackBottomPadding - metrics.bookStripHeight -
            metrics.stripSpacing - metrics.chapterStripHeight -
            metrics.stripSpacing - metrics.verseStripHeight
        return PassageFinderLayoutRules.bubbleMaxLines(
            room = verseTopTarget - safeTop,
            gap = metrics.bubbleGap,
            chrome = metrics.bubblePaddingVertical * 2f + painter.referenceLineHeight(),
            lineHeight = bubble.verseLineHeight(),
            maxLines = metrics.bubbleMaxVerseLines,
        )
    }

    private fun drawBookStrip(canvas: Canvas) {
        if (books.isEmpty()) return
        bookLane.layout(centreX())
        canvas.save()
        // The selected spine rises above the shelf, so the clip has to let it out. The
        // gap to the strip above is far wider than the overshoot, so nothing collides.
        canvas.clipRect(
            bookRect.left, bookRect.top - metrics.spineFocusOvershoot,
            bookRect.right, bookRect.bottom,
        )
        val range = bookLane.visibleRange(contentLeft, contentRight)
        val centred = bookLane.nearestIndex()
        for (i in range) {
            // The centred spine is drawn last so it overlaps its neighbours, matching
            // the z-ordering the Compose version got from `zIndex(proximity)`.
            if (i == centred) continue
            drawSpine(canvas, i)
        }
        if (centred in range) drawSpine(canvas, centred)
        canvas.restore()
    }

    private fun drawSpine(canvas: Canvas, index: Int) {
        val book = books[index]
        val isGroupStart = index > 0 && book.category != books[index - 1].category
        painter.drawBookSpine(
            canvas = canvas,
            book = book,
            left = bookLane.lefts[index],
            width = bookLane.widths[index],
            bottom = bookRect.bottom,
            proximity = bookLane.proximities[index],
            sizeFactor = bookLane.sizeFactors[index],
            focusFactor = bookLane.focusFactors[index],
            isGroupStart = isGroupStart,
            isOpenBook = index == state.openBookIndex,
        )
    }

    private fun drawNumberStrip(
        canvas: Canvas,
        lane: UniformLane,
        rect: RectF,
        isChapterStrip: Boolean,
    ) {
        if (rect.height() <= 0.5f || lane.itemCount <= 0 || books.isEmpty()) return
        val stripAlpha = if (isChapterStrip) chapterAlpha.value else verseAlpha.value
        if (stripAlpha <= 0.01f) return

        val cellSize = if (isChapterStrip) metrics.chapterCellSize else metrics.verseCellSize
        val minScale = if (isChapterStrip) metrics.chapterMinScale else metrics.verseMinScale
        val maxScale = if (isChapterStrip) metrics.chapterMaxScale else metrics.verseMaxScale
        val radius = if (isChapterStrip) metrics.chapterLensRadius else metrics.verseLensRadius
        val textSize = if (isChapterStrip) metrics.chapterTextSize else metrics.verseTextSize
        val selected = if (isChapterStrip) state.selectedChapter else state.selectedVerse

        val centreX = centreX()
        val centreY = rect.centerY()
        val centred = lane.nearestIndex()
        updateBorderAnimation(isChapterStrip, centred)
        val borderAlpha = if (isChapterStrip) chapterBorder.value else verseBorder.value

        canvas.save()
        canvas.clipRect(rect)
        // Cells overflow their pitch when magnified, so extend the range by the widest
        // possible cell to avoid popping at the edges.
        val range = lane.visibleRange(contentWidth(), cellSize * maxScale * metrics.cellMaxAspect)

        fun drawCell(i: Int) {
            if (i !in range) return
            val isCentred = i == centred
            val isSelected = i + 1 == selected
            val proximity = lane.proximity(i, radius)
            // Quartic falloff steepens the bell curve so the centre cell dominates
            // its immediate neighbours instead of blending into them.
            val sizeProximity = proximity * proximity * proximity * proximity
            painter.drawNumberCell(
                canvas = canvas,
                text = cellLabel(i + 1),
                centreX = centreX + lane.offsetFromCentre(i),
                centreY = centreY,
                cellSize = cellSize,
                baseTextSize = textSize,
                scale = lerp(minScale, maxScale, sizeProximity),
                alpha = (0.3f + 0.7f * proximity) * stripAlpha,
                isSelected = isSelected,
                emphasised = isSelected || isCentred,
                borderAlpha = when {
                    isSelected -> 1f
                    isCentred -> borderAlpha
                    else -> 0f
                },
            )
        }

        // Draw outside-in from both sides, so every cell overlaps the one further from
        // the centre and the stack converges on the focused cell. Drawing in plain index
        // order would be right only to the left of centre; to the right each cell would
        // cover its inner neighbour, and the wider three-digit plates make that obvious.
        val selectedIndex = selected - 1
        for (distance in maxOf(centred - range.first, range.last - centred) downTo 1) {
            // Written out rather than looping over a pair, so no array is allocated per
            // ring per strip per frame.
            if (centred - distance != selectedIndex) drawCell(centred - distance)
            if (centred + distance != selectedIndex) drawCell(centred + distance)
        }
        // Committed selection, then the visual centre on top of everything.
        if (selectedIndex != centred) drawCell(selectedIndex)
        drawCell(centred)
        canvas.restore()
    }

    /** Restarts the border fade whenever the centred cell of a strip changes. */
    private fun updateBorderAnimation(isChapterStrip: Boolean, centred: Int) {
        if (isChapterStrip) {
            if (centred != chapterBorderIndex) {
                chapterBorderIndex = centred
                chapterBorder.snapTo(0f)
                chapterBorder.animateTo(1f)
            }
        } else if (centred != verseBorderIndex) {
            verseBorderIndex = centred
            verseBorder.snapTo(0f)
            verseBorder.animateTo(1f)
        }
    }

    /** The label for [number], reused rather than rebuilt on every frame. */
    private fun cellLabel(number: Int): String {
        if (number > cellLabels.size) {
            // Grows to the largest number either strip has asked for; the ceiling is the
            // longest book in any supported versification, so this settles immediately.
            cellLabels = Array(maxOf(number, MIN_LABEL_CACHE)) { (it + 1).toString() }
        }
        return cellLabels[number - 1]
    }

    private fun drawBubble(canvas: Canvas) {
        val alpha = bubbleAlpha.value
        if (alpha <= 0.01f) {
            bubbleRect.setEmpty()
            return
        }
        books.getOrNull(state.selectedBookIndex) ?: return
        val reference = bubbleReference
        val maxWidth = minOf(metrics.bubbleMaxWidth, contentWidth())
        // The bubble sits a gap above the verse strip. It tracks the animated strip, so it
        // rides up and down with the reveal instead of hanging in space above it, while
        // the number of lines it may hold was budgeted against the fully revealed strip.
        val bottom = verseRect.top - metrics.bubbleGap
        // Resolved layout direction, read straight off the view — no allocation, and it
        // is what decides which edge of the verse block the placeholder dots align to.
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // The slide is applied by the caller's canvas translation, so undo it here: the
        // floor is a screen position, not one that should ride up with the stack.
        bubble.draw(
            canvas, reference, previewText, centreX(), bottom, maxWidth,
            screenTop - slideOffset, alpha, isRtl, bubbleRect,
        )
    }

    private fun centreX(): Float = (contentLeft + contentRight) / 2f

    /** Width of the strip stack, which on a wide screen is narrower than the view. */
    private fun contentWidth(): Float = contentRight - contentLeft

    /**
     * Recomputes the layout rects and spine positions that drawing normally produces.
     *
     * Touch routing and the accessibility tree both need geometry matching what is on
     * screen, and both can run before the next frame. It is a handful of rect
     * assignments plus one O(books) sweep, and allocates nothing.
     */
    private fun ensureGeometry() {
        layoutStrips()
        if (books.isNotEmpty()) bookLane.layout(centreX())
    }

    // ---- Touch ---------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!isShowing) return false
            gestureActive = true
        } else if (!gestureActive) {
            return false
        }
        // A gesture already under way is always seen through to its end, even if the
        // widget is dismissed mid-drag — which happens routinely, since swiping down at
        // book level dismisses while the finger is still down. Dropping the rest of the
        // gesture would leak the velocity tracker and strand [isBeingTouched] set,
        // silently suspending reader tracking for the rest of the session.

        // Hit-testing reads geometry that is otherwise only produced while drawing, so
        // bring it up to date first: a touch arriving before the first frame after a show
        // or a size change would otherwise test against empty rects, where every y sits
        // inside the book strip and nothing reaches the reader.
        ensureGeometry()

        val x = event.x
        // Undo the slide so hit-testing works against the laid-out positions.
        val y = event.y - slideOffset

        var tracker = velocityTracker
        if (tracker == null) {
            tracker = VelocityTracker.obtain()
            velocityTracker = tracker
        }
        tracker.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastX = x
                lockedVertical = null
                cumulativeVertical = 0f
                activeScroller = bandAt(x, y)
                val onBubble = !bubbleRect.isEmpty && bubbleRect.contains(x, y)
                // Anything clear of the strips and the bubble — above them, or beside them
                // in landscape — belongs to the reader showing through, so the touch is
                // handed straight to it. That way a finger put down there stops the glide
                // and scrolls the text in one motion, instead of the overlay swallowing it.
                //
                // Ownership is decided here and holds for the whole gesture, even if the
                // finger later crosses into the widget or out of it: a scroll that changed
                // hands halfway would jump under the finger.
                readerGesture = activeScroller == null && !onBubble && onReaderTouch != null
                isBeingTouched = !readerGesture
                if (readerGesture) {
                    // The real press is what halts the fling — the same thing that
                    // happens whenever a finger lands on a scrolling page.
                    onReaderTouch?.invoke(event)
                } else {
                    // Grabbing a moving strip stops it, as with any scrollable.
                    activeScroller?.stop()
                    // A finger on the widget halts the reader gliding beneath it.
                    onWidgetTouched?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (readerGesture) {
                    onReaderTouch?.invoke(event)
                    return true
                }
                val dx = x - lastX
                lastX = x
                if (lockedVertical == null) {
                    val totalX = abs(x - downX)
                    val totalY = abs(y - downY)
                    if (totalX > touchSlop || totalY > touchSlop) {
                        // Locking vertical whenever it dominates makes the upward drill
                        // gesture easy to trigger from within a horizontal strip.
                        lockedVertical = totalY > totalX
                        if (lockedVertical == true) cumulativeVertical = 0f
                    }
                }
                when (lockedVertical) {
                    true -> handleVerticalDrag(y)
                    false -> handleHorizontalDrag(dx)
                    else -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (readerGesture) {
                    val moved = abs(x - downX) > touchSlop || abs(y - downY) > touchSlop
                    if (moved) {
                        // A real scroll, so lift for real and let the reader fling on from
                        // here. The finder stays open and re-centres itself on the verse
                        // the reader lands on, once it reports one.
                        onReaderTouch?.invoke(event)
                    } else {
                        // Stationary. Either way the page must see no click — it must not
                        // select a verse on the way out — so cancel rather than lift.
                        forwardCancel(event)
                        // Past the long-press timeout this is a thumb resting on the
                        // screen, which in a two-handed landscape grip is simply how the
                        // phone is held. Dismissing on that would make holding the device
                        // close the finder. Below it, it is the deliberate tap outside the
                        // strips that has always dismissed.
                        if (event.eventTime - event.downTime < longPressTimeout) {
                            onDismiss?.invoke()
                        }
                    }
                    endGesture()
                    return true
                }
                val wasTap = lockedVertical == null &&
                    abs(x - downX) <= touchSlop && abs(y - downY) <= touchSlop
                if (wasTap) {
                    handleTap(x, y)
                } else if (lockedVertical == false) {
                    tracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    flingActiveLane(tracker.xVelocity)
                }
                endGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (readerGesture) {
                    onReaderTouch?.invoke(event)
                    endGesture()
                    return true
                }
                if (lockedVertical == false) activeScroller?.snap()
                endGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Replays [event] on the reader as a cancellation.
     *
     * A cancel unwinds whatever the page started on the down — the pressed state, any
     * pending link activation — without it counting as a click.
     */
    private fun forwardCancel(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        onReaderTouch?.invoke(cancel)
        cancel.recycle()
    }

    private fun endGesture() {
        velocityTracker?.recycle()
        velocityTracker = null
        activeScroller = null
        readerGesture = false
        isBeingTouched = false
        gestureActive = false
        lockedVertical = null
        invalidate()
    }

    /**
     * Accumulates vertical travel and fires a level change each time the threshold is
     * crossed, resetting afterwards so one long swipe can drill through several levels.
     */
    private fun handleVerticalDrag(y: Float) {
        cumulativeVertical = y - downY
        if (cumulativeVertical < -metrics.drillThreshold) {
            onDrillDown?.invoke()
            downY = y
            cumulativeVertical = 0f
        } else if (cumulativeVertical > metrics.drillThreshold) {
            val handled = onDrillUp?.invoke() ?: false
            if (!handled) onDismiss?.invoke()
            downY = y
            cumulativeVertical = 0f
        }
    }

    private fun handleHorizontalDrag(dx: Float) {
        val scroller = activeScroller ?: return
        scroller.dragBy(-dx)
        markScrolling(scroller, true)
        onCentreMaybeChanged(scroller)
        invalidate()
    }

    /**
     * Maps a touch point onto the strip that owns it, for both drags and taps.
     *
     * Each strip claims a band reaching half way into the gaps on either side, so the
     * stack has no dead rows between the strips — a finger aimed at a spine but landing a
     * few dp high still scrolls the books rather than doing nothing, or worse, dismissing.
     * The book strip's band runs to the bottom edge, absorbing the padding below it.
     *
     * The bands are bounded horizontally as well. Testing y alone was right only while the
     * strips spanned the whole width, which is true in portrait and false in landscape,
     * where the stack is capped at 480dp and the reader shows through down both margins: a
     * finger there at strip height was claimed by a strip, so dragging sideways scrolled
     * it and dragging down drilled up — which at book level closes the finder. There is
     * one rule for every point outside the stack, whether it lies above it or beside it.
     */
    private fun bandAt(x: Float, y: Float): LaneScroller? {
        val slop = metrics.stripHitSlopHorizontal
        if (x < contentLeft - slop || x > contentRight + slop) return null
        val margin = metrics.stripSpacing / 2f
        if (y >= bookRect.top - margin) return bookScroll
        if (chapterRect.height() > 0.5f && y >= chapterRect.top - margin) return chapterScroll
        if (verseRect.height() > 0.5f && y >= verseRect.top - margin) return verseScroll
        return null
    }

    private fun flingActiveLane(velocityX: Float) {
        val scroller = activeScroller ?: return
        if (abs(velocityX) >= minFlingVelocity) {
            scroller.fling(-velocityX)
        } else {
            scroller.snap()
        }
        invalidate()
    }

    private fun markScrolling(scroller: LaneScroller, scrolling: Boolean) {
        when (scroller) {
            bookScroll -> bookScrolling = scrolling
            chapterScroll -> chapterScrolling = scrolling
        }
    }

    /**
     * Fires a haptic tick whenever the centred item changes while a strip is moving.
     *
     * The Compose implementation only ticked once, when a scroll finally settled, which
     * left [HapticController]'s throttle — added expressly to keep fast flings from
     * buzzing — with nothing to throttle. Ticking per boundary is what a physical picker
     * does and is what the throttle was written for.
     */
    private fun onCentreMaybeChanged(scroller: LaneScroller) {
        val index = scroller.lane.nearestIndex()
        if (index == scroller.lastHapticIndex) return
        scroller.lastHapticIndex = index
        when (scroller) {
            bookScroll -> haptics.onBookBoundary()
            chapterScroll -> haptics.onChapterBoundary()
            verseScroll -> haptics.onVerseBoundary()
        }
    }

    /**
     * Commits the settled selection back to the state layer.
     *
     * Only ever reached for user-driven scrolls: a programmatic re-center ends without
     * calling this, so a re-center can never be mistaken for the user choosing something.
     */
    private fun onLaneSettled(scroller: LaneScroller) {
        markScrolling(scroller, false)
        val index = scroller.lane.nearestIndex()
        // Suppress the re-center only when this settle will actually move the selection.
        // Marking unconditionally would leave the flag set after a scroll that drifted
        // back onto the item it started on, and the next genuine external change — a tap,
        // say — would then find its re-center already suppressed.
        val changed = when (scroller) {
            bookScroll -> index != state.selectedBookIndex
            chapterScroll -> index + 1 != state.selectedChapter
            else -> index + 1 != state.selectedVerse
        }
        if (changed) scroller.coordinator.markScrollSettled()
        when (scroller) {
            bookScroll -> {
                // A different book means the verse strip stays hidden until the user
                // picks a chapter, rather than pre-committing to verse 1 of the new book.
                if (changed) verseRevealed = false
                onBookSelected?.invoke(index)
            }
            chapterScroll -> {
                verseRevealed = true
                onChapterSelected?.invoke(index + 1)
            }
            verseScroll -> onVerseSelected?.invoke(index + 1)
        }
        invalidate()
    }

    /**
     * Routes a tap.
     *
     * A tap that lands anywhere in a strip's band always picks that strip's nearest item;
     * it can never fall through to dismissing the overlay. That mattered: spines are
     * separated by a 2dp gap, and a tap landing in one used to miss every branch below
     * and close the widget, so repeatedly tapping books shut the finder after a few
     * attempts. The bands also absorb the gaps between strips, so the whole stack is live
     * and only a tap clearly outside it dismisses.
     */
    private fun handleTap(x: Float, y: Float) {
        if (!bubbleRect.isEmpty && bubbleRect.contains(x, y)) {
            onConfirm?.invoke()
            return
        }
        when (bandAt(x, y)) {
            bookScroll -> {
                if (loading || books.isEmpty()) return
                val index = bookIndexAt(x)
                if (index != state.selectedBookIndex) verseRevealed = false
                bookScroll.animateTo(bookLane.snapPointFor(index))
                onBookSelected?.invoke(index)
                onDrillDown?.invoke()
            }
            chapterScroll -> {
                val index = cellIndexAt(chapterLane, isChapterStrip = true, x = x)
                chapterScroll.animateTo(chapterLane.snapPointFor(index))
                verseRevealed = true
                onChapterSelected?.invoke(index + 1)
                onDrillDown?.invoke()
            }
            verseScroll -> {
                val index = cellIndexAt(verseLane, isChapterStrip = false, x = x)
                val alreadySelected = index + 1 == state.selectedVerse
                verseScroll.animateTo(verseLane.snapPointFor(index))
                onVerseSelected?.invoke(index + 1)
                // Tapping the verse that is already selected commits it.
                if (alreadySelected) onConfirm?.invoke()
            }
            // Clear of the strips — dismiss, as the full-screen target behind the strips
            // did in the Compose implementation.
            else -> onDismiss?.invoke()
        }
    }

    /**
     * The spine nearest [x]. Falls back to the closest spine centre rather than reporting
     * a miss, so the gap between two spines belongs to whichever is nearer.
     */
    private fun bookIndexAt(x: Float): Int {
        val range = bookLane.visibleRange(contentLeft, contentRight)
        if (range.isEmpty()) return state.selectedBookIndex
        var nearest = range.first
        var nearestDistance = Float.MAX_VALUE
        for (i in range) {
            val left = bookLane.lefts[i]
            val right = left + bookLane.widths[i]
            if (x in left..right) return i
            val distance = minOf(abs(x - left), abs(x - right))
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = i
            }
        }
        return nearest
    }

    /**
     * The numeric cell under [x], tested against the cells as they are actually drawn.
     *
     * Rounding by the lane's pitch would be wrong here: cells sit on a small constant
     * pitch (20 dp for verses) but the centred one is drawn at nearly three times that,
     * so pitch rounding gives it only the innermost ±10 dp and hands the rest of its
     * visible area to a neighbour hidden underneath it. Tapping the big centred cell would
     * then select its neighbour — which above all breaks tap-the-selected-verse-to-confirm.
     *
     * Cells are tested topmost-first, in the reverse of the order [drawStrip] paints them,
     * so overlapping plates resolve to the one the user can actually see. Falling back to
     * the nearest centre keeps a tap in the thin gaps at the strip's edges meaningful.
     */
    private fun cellIndexAt(lane: UniformLane, isChapterStrip: Boolean, x: Float): Int {
        val cellSize = if (isChapterStrip) metrics.chapterCellSize else metrics.verseCellSize
        val minScale = if (isChapterStrip) metrics.chapterMinScale else metrics.verseMinScale
        val maxScale = if (isChapterStrip) metrics.chapterMaxScale else metrics.verseMaxScale
        val radius = if (isChapterStrip) metrics.chapterLensRadius else metrics.verseLensRadius
        val textSize = if (isChapterStrip) metrics.chapterTextSize else metrics.verseTextSize
        val centre = centreX()
        val centred = lane.nearestIndex()
        val range = lane.visibleRange(contentWidth(), cellSize * maxScale * metrics.cellMaxAspect)
        if (range.isEmpty()) return centred

        fun hits(i: Int): Boolean {
            if (i !in range) return false
            val proximity = lane.proximity(i, radius)
            val sizeProximity = proximity * proximity * proximity * proximity
            val scale = lerp(minScale, maxScale, sizeProximity)
            val half = painter.cellWidth(cellLabel(i + 1), cellSize, textSize, scale) / 2f
            val cellCentre = centre + lane.offsetFromCentre(i)
            return x >= cellCentre - half && x <= cellCentre + half
        }

        if (hits(centred)) return centred
        // The committed selection is painted above the rings too, so it outranks them.
        val selectedIndex = (if (isChapterStrip) state.selectedChapter else state.selectedVerse) - 1
        if (selectedIndex != centred && hits(selectedIndex)) return selectedIndex
        for (distance in 1..maxOf(centred - range.first, range.last - centred)) {
            if (hits(centred - distance)) return centred - distance
            if (hits(centred + distance)) return centred + distance
        }
        return lane.nearestIndex(lane.scroll + (x - centre))
    }

    // ---- Accessibility -------------------------------------------------------------

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    /**
     * The currently visible items, as virtual accessibility nodes.
     *
     * Rebuilt per query rather than cached: accessibility services only ask while an
     * assistive technology is actually running, so the allocation never lands on a
     * normal frame.
     */
    internal fun accessibilityNodes(): List<A11yNode> {
        if (!isShowing || books.isEmpty()) return emptyList()
        val nodes = ArrayList<A11yNode>()
        ensureGeometry()
        val offset = slideOffset.toInt()

        for (i in bookLane.visibleRange(contentLeft, contentRight)) {
            val left = bookLane.lefts[i]
            val top = bookRect.bottom - metrics.spineMaxHeight - metrics.spineFocusOvershoot
            nodes.add(
                A11yNode(
                    id = PassageFinderA11yHelper.ID_BOOK_BASE + i,
                    label = books[i].longName,
                    bounds = Rect(
                        left.toInt(), (top + offset).toInt(),
                        (left + bookLane.widths[i]).toInt(), (bookRect.bottom + offset).toInt(),
                    ),
                    target = A11yTarget.Book(i),
                    selected = i == state.selectedBookIndex,
                ),
            )
        }

        addCellNodes(
            nodes, chapterLane, chapterRect, metrics.chapterCellSize, metrics.chapterMinScale,
            metrics.chapterMaxScale, metrics.chapterLensRadius, metrics.chapterTextSize,
            PassageFinderA11yHelper.ID_CHAPTER_BASE, state.selectedChapter, offset,
        ) { number -> context.getString(R.string.passage_finder_a11y_chapter, number) }

        addCellNodes(
            nodes, verseLane, verseRect, metrics.verseCellSize, metrics.verseMinScale,
            metrics.verseMaxScale, metrics.verseLensRadius, metrics.verseTextSize,
            PassageFinderA11yHelper.ID_VERSE_BASE, state.selectedVerse, offset,
        ) { number -> context.getString(R.string.passage_finder_a11y_verse, number) }

        if (!bubbleRect.isEmpty) {
            val book = books.getOrNull(state.selectedBookIndex)
            if (book != null) {
                val reference = bubbleReference
                nodes.add(
                    A11yNode(
                        id = PassageFinderA11yHelper.ID_CONFIRM,
                        label = context.getString(R.string.passage_finder_a11y_go_to, reference),
                        bounds = Rect(
                            bubbleRect.left.toInt(), (bubbleRect.top + offset).toInt(),
                            bubbleRect.right.toInt(), (bubbleRect.bottom + offset).toInt(),
                        ),
                        target = A11yTarget.Confirm,
                        selected = false,
                    ),
                )
            }
        }
        return nodes
    }

    private inline fun addCellNodes(
        nodes: MutableList<A11yNode>,
        lane: UniformLane,
        rect: RectF,
        cellSize: Float,
        minScale: Float,
        maxScale: Float,
        radius: Float,
        textSize: Float,
        idBase: Int,
        selectedNumber: Int,
        offset: Int,
        label: (Int) -> String,
    ) {
        if (rect.height() <= 0.5f || lane.itemCount <= 0) return
        val widest = cellSize * maxScale * metrics.cellMaxAspect / 2f
        val centre = centreX()
        // Emitted nearest-the-centre first, because the helper resolves a hover to the
        // first node containing the point and the magnified cells overlap heavily. In
        // index order a distant cell — whose node is wide because that is how wide it is
        // drawn — contains the centre point and wins, so explore-by-touch on the focused
        // verse announced one a couple of places away and activating it drilled into the
        // wrong chapter.
        val range = lane.visibleRange(contentWidth(), widest)
        if (range.isEmpty()) return
        val centred = lane.nearestIndex().coerceIn(range.first, range.last)
        val order = IntArray(range.last - range.first + 1)
        var n = 0
        order[n++] = centred
        var distance = 1
        while (n < order.size) {
            if (centred - distance >= range.first) order[n++] = centred - distance
            if (n < order.size && centred + distance <= range.last) order[n++] = centred + distance
            distance++
        }
        for (i in order) {
            // Sized by the scale the cell is actually drawn at, so a node covers its own
            // plate and not its neighbours'.
            val proximity = lane.proximity(i, radius)
            val sizeProximity = proximity * proximity * proximity * proximity
            val scale = lerp(minScale, maxScale, sizeProximity)
            val half = painter.cellWidth(cellLabel(i + 1), cellSize, textSize, scale) / 2f
            val x = centre + lane.offsetFromCentre(i)
            nodes.add(
                A11yNode(
                    id = idBase + i + 1,
                    label = label(i + 1),
                    bounds = Rect(
                        (x - half).toInt(), (rect.top + offset).toInt(),
                        (x + half).toInt(), (rect.bottom + offset).toInt(),
                    ),
                    target = if (idBase == PassageFinderA11yHelper.ID_CHAPTER_BASE) {
                        A11yTarget.Chapter(i + 1)
                    } else {
                        A11yTarget.Verse(i + 1)
                    },
                    selected = i + 1 == selectedNumber,
                ),
            )
        }
    }

    /** Applies an accessibility activation of a virtual node. */
    internal fun onAccessibilityAction(target: A11yTarget) {
        when (target) {
            is A11yTarget.Book -> {
                bookScroll.animateTo(bookLane.snapPointFor(target.index))
                if (target.index != state.selectedBookIndex) verseRevealed = false
                onBookSelected?.invoke(target.index)
                onDrillDown?.invoke()
            }
            is A11yTarget.Chapter -> {
                chapterScroll.animateTo(chapterLane.snapPointFor(target.number - 1))
                verseRevealed = true
                onChapterSelected?.invoke(target.number)
                onDrillDown?.invoke()
            }
            is A11yTarget.Verse -> {
                verseScroll.animateTo(verseLane.snapPointFor(target.number - 1))
                onVerseSelected?.invoke(target.number)
            }
            A11yTarget.Confirm -> onConfirm?.invoke()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelScrolls()
        // Detaching mid-gesture means no lift ever arrives, so clear the gesture state
        // here too rather than leaving flags set for whenever the view is attached again.
        endGesture()
    }

    // ---- Lane scrolling ------------------------------------------------------------

    /**
     * Fling, snap and programmatic re-centring for one lane.
     *
     * The fling runs in screen pixels — that is the space the user's velocity is measured
     * in — and each frame's screen delta is converted into base space through the lane's
     * local scale. On the book strip that scale varies with magnification, so integrating
     * the conversion frame by frame is what keeps a fling over thick and thin books
     * tracking the finger's initial throw.
     */
    private inner class LaneScroller(
        val lane: StripLane,
        /** Fling velocities are multiplied by this before being handed to the scroller. */
        private val flingVelocityScale: Float = 1f,
    ) {
        val coordinator = ScrollCoordinator()
        private val scroller = OverScroller(context)
        private var lastScrollerX = 0
        private var flinging = false

        private val snapTween = Tween1D(0f, durationMs = SNAP_DURATION_MS)
        private var snapping = false
        private var programmatic = false

        /** Last index that fired a haptic tick, so each boundary ticks once. */
        var lastHapticIndex = -1

        /**
         * True while this lane is still moving under its own momentum — a fling, or the
         * snap that follows one. Programmatic re-centres are excluded: those are the
         * widget catching up with the reader, not a gesture worth protecting.
         */
        val isMoving: Boolean get() = flinging || (snapping && !programmatic)

        fun dragBy(deltaScreenPx: Float) {
            stopAnimations()
            val scale = lane.localScale.let { if (it > 0.01f) it else 1f }
            lane.scroll = lane.clampScroll(lane.scroll + deltaScreenPx / scale)
        }

        fun fling(velocityScreenPx: Float) {
            stopAnimations()
            lastHapticIndex = lane.nearestIndex()
            lastScrollerX = 0
            scroller.fling(
                0, 0, (velocityScreenPx * flingVelocityScale).toInt(), 0,
                Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, 0, 0,
            )
            if (disableAnimations) {
                // Every other motion in the widget honours the setting; a fling that still
                // streamed frames for a couple of seconds would be the one thing left
                // repainting an e-ink screen. Jump to where the throw would have ended and
                // settle there, so the gesture keeps its meaning without the animation.
                val screenDelta = (scroller.finalX - lastScrollerX).toFloat()
                lane.scroll += screenDelta / lane.localScale.let { if (it > 0.01f) it else 1f }
                scroller.forceFinished(true)
                snap()
                return
            }
            flinging = true
        }

        /** Eases to the nearest item and reports the settled selection. */
        fun snap() {
            stopAnimations()
            val target = lane.snapPointFor(lane.nearestIndex())
            if (abs(target - lane.scroll) < 0.5f || disableAnimations) {
                lane.scroll = target
                onLaneSettled(this)
                return
            }
            snapTween.snapping = disableAnimations
            snapTween.snapTo(lane.scroll)
            snapTween.animateTo(target)
            snapping = true
        }

        /** Programmatic move onto an exact position, suppressing the settle callback. */
        fun animateTo(target: Float) {
            stopAnimations()
            if (disableAnimations || abs(target - lane.scroll) < 0.5f) {
                lane.scroll = lane.clampScroll(target)
                return
            }
            coordinator.beginProgrammaticScroll()
            programmatic = true
            snapTween.snapping = false
            snapTween.snapTo(lane.scroll)
            snapTween.animateTo(lane.clampScroll(target))
            snapping = true
        }

        fun stop() {
            stopAnimations()
        }

        private fun stopAnimations() {
            if (flinging) {
                scroller.forceFinished(true)
                flinging = false
            }
            if (snapping) {
                snapping = false
                if (programmatic) {
                    programmatic = false
                    coordinator.endProgrammaticScroll()
                }
            }
        }

        /** Steps the fling or snap one frame. Returns true while still moving. */
        fun advance(): Boolean {
            if (flinging) {
                if (scroller.computeScrollOffset()) {
                    val delta = scroller.currX - lastScrollerX
                    lastScrollerX = scroller.currX
                    val scale = lane.localScale.let { if (it > 0.01f) it else 1f }
                    val next = lane.scroll + delta / scale
                    val clamped = lane.clampScroll(next)
                    lane.scroll = clamped
                    onCentreMaybeChanged(this)
                    if (clamped != next) {
                        // Hit an end — stop rather than grinding against the boundary.
                        scroller.forceFinished(true)
                        flinging = false
                        snap()
                    }
                    return true
                }
                flinging = false
                snap()
                return snapping
            }

            if (snapping) {
                // Snap timing is driven by the same frame clock as the other animators.
                val still = snapTween.advance(lastFrameDelta)
                lane.scroll = snapTween.value
                if (!programmatic) onCentreMaybeChanged(this)
                if (!still) {
                    snapping = false
                    if (programmatic) {
                        programmatic = false
                        coordinator.endProgrammaticScroll()
                    } else {
                        onLaneSettled(this)
                    }
                }
                return still
            }
            return false
        }
    }

    private companion object {
        const val SHOW_DURATION_MS = 300
        const val BUBBLE_FADE_IN_MS = 200
        const val BUBBLE_FADE_OUT_MS = 150
        const val BORDER_FADE_MS = 80
        const val SNAP_DURATION_MS = 180

        /** A frame longer than this is treated as a stall, not as elapsed animation time. */
        const val MAX_FRAME_SECONDS = 0.064f

        /**
         * Smallest cell-label cache, sized past the longest chapter and verse counts in
         * the supported versifications so it is allocated once and never grown.
         */
        const val MIN_LABEL_CACHE = 200
    }
}
