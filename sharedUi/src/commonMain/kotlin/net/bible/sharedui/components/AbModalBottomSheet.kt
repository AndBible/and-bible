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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoInk

/**
 * THE sheet (fix batch 5 F106). Each material3 1.4.0 ModalBottomSheet is its own full-screen dialog
 * window whose bars default to visible, so `hide_status_bar` and fullscreen leaked through every sheet.
 * This mirrors the host's bar state into that window. `SheetSystemBarsGuardTest` keeps every sheet on it.
 * It does not touch `contentWindowInsets` (`SheetImeInsetGuardTest`, fix batch 2 C1).
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
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(outlineModifier),
        sheetState = sheetState,
        properties = properties,
        containerColor = if (mono) MaterialTheme.colorScheme.surface else BottomSheetDefaults.ContainerColor,
        scrimColor = if (mono) Color.Transparent else BottomSheetDefaults.ScrimColor,
        // M3 1.4.0 defaults tonalElevation to 0.dp and Surface's shadowElevation to 0.dp.
        tonalElevation = 0.dp,
        // With a description the handle is drawn by us as the first row of the content (same place, same pixels as
        // M3's wrapper) so that ONE node carries both the name and the dismiss action; M3's wrapper node merged an
        // empty label on iOS (audit: "Element has no description" on the 32x48 handle).
        dragHandle = if (!showDragHandle || handleDescription != null) null
            else ({ AbSheetDragHandle(description = null, mono = mono) }),
    ) {
        if (showDragHandle && handleDescription != null) {
            AbSheetDragHandle(
                description = handleDescription,
                mono = mono,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        MirrorHostSystemBars()
        if (mono) NoDialogNavigationContrast()
        content()
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
