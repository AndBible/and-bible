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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import net.bible.sharedui.theme.LocalDisableAnimations
import net.bible.sharedui.theme.LocalMotionDurationScaleHandledByHost
import net.bible.sharedui.theme.isPureMonochrome

/**
 * Shared menu paints and motion policy. Material3 1.9.0's MotionScheme is Kotlin-internal,
 * and iOS/desktop lack Android's zero-scale recomposer. With animations disabled there, use the same
 * Surface/scrollable Column in a Popup, composed only while open: no entry or retained exit frames.
 * Otherwise keep Material's transitions, positioning and keyboard handling unchanged.
 */
@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
@kotlin.native.HiddenFromObjC
@Composable
fun AbDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = if (isPureMonochrome()) 0.dp else MenuDefaults.TonalElevation,
    shadowElevation: Dp = if (isPureMonochrome()) 0.dp else MenuDefaults.ShadowElevation,
    border: BorderStroke? = if (isPureMonochrome()) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    positionProvider: PopupPositionProvider? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val instant = useInstantMenus()
    UncappedFontScale {
        if (!instant) {
            DropdownMenu(
                expanded, onDismissRequest, modifier, offset, scrollState, properties,
                shape, containerColor, tonalElevation, shadowElevation, border, content,
            )
        } else if (expanded) {
            val density = LocalDensity.current
            val position = remember(offset, density) {
                InstantMenuPosition(
                    with(density) { offset.x.roundToPx() },
                    with(density) { offset.y.roundToPx() },
                    with(density) { 48.dp.roundToPx() },
                )
            }
            Popup(popupPositionProvider = positionProvider ?: position, onDismissRequest = onDismissRequest, properties = properties) {
                val focus = LocalFocusManager.current
                val input = LocalInputModeManager.current
                // A requester on a non-focusable parent targets its first focusable child, not an empty focus stop.
                val menuFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (input.inputMode == InputMode.Keyboard) menuFocus.requestFocus()
                }
                Surface(
                    shape = shape, color = containerColor, tonalElevation = tonalElevation,
                    shadowElevation = shadowElevation, border = border,
                ) {
                    Column(
                        modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max).verticalScroll(scrollState)
                            .onMenuArrowKey { next ->
                                input.requestInputMode(InputMode.Keyboard)
                                focus.moveFocus(if (next) FocusDirection.Next else FocusDirection.Previous)
                            }.focusRequester(menuFocus),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
internal fun useInstantMenus(): Boolean =
    LocalDisableAnimations.current && !LocalMotionDurationScaleHandledByHost.current

private fun Modifier.onMenuArrowKey(move: (Boolean) -> Unit): Modifier =
    onKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionDown || it.key == Key.DirectionUp)) {
            move(it.key == Key.DirectionDown)
            true
        } else false
    }

/** Material's anchor-first, opposite-edge, then window-edge placement, including RTL and margins. */
internal data class InstantMenuPosition(val offsetX: Int, val offsetY: Int, val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val ltr = layoutDirection == LayoutDirection.Ltr
        val start = if (ltr) anchorBounds.left + offsetX else anchorBounds.right - width - offsetX
        val end = if (ltr) anchorBounds.right - width + offsetX else anchorBounds.left - offsetX
        val edgeX = when {
            width >= windowSize.width -> 0
            anchorBounds.center.x < windowSize.width / 2 -> 0
            else -> windowSize.width - width
        }
        val x = listOf(start, end).firstOrNull { it >= 0 && it + width <= windowSize.width } ?: edgeX
        val edgeY = when {
            height >= windowSize.height - 2 * margin -> margin
            anchorBounds.center.y < windowSize.height / 2 -> margin
            else -> windowSize.height - margin - height
        }
        val y = listOf(anchorBounds.bottom + offsetY, anchorBounds.top - height + offsetY, anchorBounds.top - height / 2 + offsetY)
            .firstOrNull { it >= margin && it + height <= windowSize.height - margin } ?: edgeY
        return IntOffset(x, y)
    }
}
