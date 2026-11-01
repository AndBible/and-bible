package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.sharedui.theme.LocalDisableAnimations
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.NoMotionDurationScale
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoInk

/**
 * THE sheet (fix batch 5 F106). Each material3 1.4.0 ModalBottomSheet is its own full-screen dialog
 * window whose bars default to visible, so `hide_status_bar` and fullscreen leaked through every sheet.
 * This mirrors the host's bar state into that window. `SheetSystemBarsGuardTest` keeps every sheet on it.
 * It does not touch `contentWindowInsets` (`SheetImeInsetGuardTest`, fix batch 2 C1).
 *
 * With "Disable animations" on (`LocalDisableAnimations`) a caller's own `sheetState.hide()` ends in
 * [onDismissRequest] (the sheet's slide is taken over, see [InstantSheetMotion]), and code after that `hide()` in the
 * same coroutine may not run: the takeover cancels the caller's animation.
 *
 * [showDragHandle] only controls the handle, not swipe dismissal. Disable it when the content has an
 * accessible close button and no partial/expanded resize states. Such headers provide their own top spacing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    // R3: the screen-reader name of the drag handle (iOS audit: "Element has no description"). Null keeps the
    // library default; callers that have `LocalStrings` pass `strings.sheetDragHandle`.
    dragHandleDescription: String? = null,
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    TrackOverlay()
    val instantMotion = LocalDisableAnimations.current
    // With animations off, the library's own dismiss and ours both end in the takeover below; report it once.
    val dismissOnce = rememberDismissOnce(sheetState, onDismissRequest, instantMotion)
    val dismiss = if (instantMotion) dismissOnce else onDismissRequest
    val mono = isPureMonochrome()
    val handleDescription = dragHandleDescription ?: LocalSheetDragHandleDescription.current
    val scope = rememberCoroutineScope()
    val ink = monoInk(LocalIsDarkTheme.current)
    val shape = BottomSheetDefaults.ExpandedShape
    val outlineModifier = if (mono) Modifier.drawWithContent {
        drawContent()
        // M3 appends draggableAnchors AFTER the caller's modifier. Draw at its public offset
        // so the outline follows the entire surface (including M3's accessible drag handle).
        // Reading in draw also invalidates the outline during a drag, without changing layout.
        if (sheetState.isVisible || sheetState.isAnimationRunning) {
            val offset = sheetState.requireOffset().roundToInt().toFloat()
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            translate(top = offset) {
                clipPath(path) {
                    // Clip the outer half: the visible 1dp stroke stays inside the sheet shape.
                    drawOutline(outline, ink, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    } else Modifier
    ModalBottomSheet(
        onDismissRequest = dismiss,
        modifier = modifier.then(outlineModifier),
        sheetState = sheetState,
        properties = properties,
        containerColor = if (mono) MaterialTheme.colorScheme.surface else BottomSheetDefaults.ContainerColor,
        scrimColor = if (mono) Color.Transparent else BottomSheetDefaults.ScrimColor,
        // M3 1.4.0 defaults tonalElevation to 0.dp and Surface's shadowElevation to 0.dp.
        tonalElevation = 0.dp,
        // With a description the compact handle is drawn by us as the first row of the content
        // so that ONE node carries both the name and the dismiss action; M3's wrapper node merged an
        // empty label on iOS (audit: "Element has no description" on the 32x48 handle).
        dragHandle = if (!showDragHandle || handleDescription != null) null
            else ({ AbSheetDragHandle(description = null, mono = mono) }),
    ) {
        if (showDragHandle && handleDescription != null) {
            AbSheetDragHandle(
                description = handleDescription,
                mono = mono,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) dismiss() }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        MirrorHostSystemBars()
        if (mono) NoDialogNavigationContrast()
        content()
    }
    if (instantMotion) InstantSheetMotion(sheetState, dismissOnce)
}

/** Holds whether the current showing of a sheet has reported its dismiss. Not snapshot state: never read to draw. */
private class DismissReported(var value: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDismissOnce(sheetState: SheetState, onDismissRequest: () -> Unit, active: Boolean): () -> Unit {
    val latest by rememberUpdatedState(onDismissRequest)
    val reported = remember(sheetState) { DismissReported() }
    // A sheet shown again with the same state may be dismissed again.
    // Read only while instant motion is active: with the setting off this adds no recomposition.
    if (active && sheetState.targetValue != SheetValue.Hidden) reported.value = false
    return remember(sheetState) {
        {
            if (!reported.value) {
                reported.value = true
                latest()
            }
        }
    }
}

/**
 * "Disable animations" for the sheet where the platform gives the composition no zero motion-duration scale (iOS and
 * desktop; osTicket 3499). Android's `AppWindowRecomposer` puts one into every effect context, so there this is a
 * no-op.
 *
 * Material runs the sheet's slide from its own coroutines (show on entry; hide on a scrim tap, back or a drag settling
 * to hidden), whose context the app cannot reach. Whenever one of them starts, this takes the sheet over: it moves it
 * to the same target from a coroutine whose context has a zero [MotionDurationScale], so it arrives on the next frame.
 * The sheet's drag mutex cancels the library's animation, which also cancels the library's own dismiss report; so
 * a takeover that ends hidden reports the dismiss itself ([dismiss] is the once-only callback).
 * The scrim's short fade is the library's own and stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstantSheetMotion(sheetState: SheetState, dismiss: () -> Unit) {
    LaunchedEffect(sheetState) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        withContext(NoMotionDurationScale) {
            while (true) {
                // Re-evaluated every round: an animation already running when this is reached (one that started during
                // the previous takeover) is taken over at once, not missed.
                snapshotFlow { sheetState.isAnimationRunning }.first { it }
                val target = sheetState.targetValue
                try {
                    when (target) {
                        SheetValue.Hidden -> sheetState.hide()
                        SheetValue.Expanded -> sheetState.expand()
                        SheetValue.PartiallyExpanded -> sheetState.partialExpand()
                    }
                } catch (e: CancellationException) {
                    // Another sheet-state user (a finger down, the library's own show) cancelled this takeover: carry
                    // on with the next animation. Only the effect's own cancellation ends the loop.
                    coroutineContext.ensureActive()
                }
                if (target == SheetValue.Hidden && !sheetState.isVisible) dismiss()
                // Never spin on an animation that outlives its takeover.
                if (sheetState.isAnimationRunning) withFrameNanos { }
            }
        }
    }
}

/**
 * The sheet's drag handle. With a [description] the stock M3 handle is wrapped in ONE node that replaces its
 * semantics (`clearAndSetSemantics`): the screen-reader name plus the dismiss action M3's own wrapper offers
 * ([onDismiss]). The stock 32x4dp bar and localized semantics are retained, with 8dp vertical padding
 * instead of M3's 22dp. See the call site for why the semantics wrapper is ours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbSheetDragHandle(
    description: String?,
    mono: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val handle: @Composable () -> Unit = {
        // Keep M3's localized handle node and bar, but trim 14dp from each padding edge.
        val compact = Modifier.layout { measurable, constraints ->
            val trim = 14.dp.roundToPx()
            val placeable = measurable.measure(constraints.copy(
                maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * trim else constraints.maxHeight,
            ))
            layout(placeable.width, (placeable.height - 2 * trim).coerceAtLeast(0)) {
                placeable.placeRelative(0, -trim)
            }
        }
        if (mono) BottomSheetDefaults.DragHandle(modifier = compact, color = monoInk(LocalIsDarkTheme.current))
        else BottomSheetDefaults.DragHandle(modifier = compact)
    }
    if (description == null) handle()
    else Box(modifier.clearAndSetSemantics {
        contentDescription = description
        if (onDismiss != null) dismiss { onDismiss(); true }
    }) { handle() }
}

/**
 * R3: the drag-handle name for every [AbModalBottomSheet] that has no explicit `dragHandleDescription`. Provided
 * where the iOS host provides `LocalStrings`; null elsewhere (Android keeps M3's own localized handle).
 */
val LocalSheetDragHandleDescription = staticCompositionLocalOf<String?> { null }
