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
        dragHandle = if (handleDescription != null) null else ({ AbSheetDragHandle(description = null, mono = mono) }),
    ) {
        if (handleDescription != null) {
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
 * ([onDismiss]). Pixels are identical to the stock handle. See the call site for why the wrapper is ours.
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
        if (mono) BottomSheetDefaults.DragHandle(color = monoInk(LocalIsDarkTheme.current))
        else BottomSheetDefaults.DragHandle()
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
