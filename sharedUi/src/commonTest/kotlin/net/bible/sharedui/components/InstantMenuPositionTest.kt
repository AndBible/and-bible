package net.bible.sharedui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class InstantMenuPositionTest {
    @Test fun anchorsBelowWithDirectionalOffsets() {
        val position = InstantMenuPosition(10, 5, 48)
        val anchor = IntRect(100, 100, 140, 140)
        assertEquals(IntOffset(110, 145), position.calculatePosition(anchor, IntSize(400, 600), LayoutDirection.Ltr, IntSize(80, 100)))
        assertEquals(IntOffset(50, 145), position.calculatePosition(anchor, IntSize(400, 600), LayoutDirection.Rtl, IntSize(80, 100)))
    }

    @Test fun bottomRightAnchorUsesOppositeEdges() {
        assertEquals(IntOffset(300, 400), InstantMenuPosition(0, 0, 48).calculatePosition(
            IntRect(360, 500, 400, 540), IntSize(400, 600), LayoutDirection.Ltr, IntSize(100, 100),
        ))
    }

    @Test fun oversizedMenuStartsAtWindowEdgeNotOffscreenCentre() {
        assertEquals(IntOffset(0, 48), InstantMenuPosition(0, 0, 48).calculatePosition(
            IntRect(180, 480, 220, 520), IntSize(400, 600), LayoutDirection.Ltr, IntSize(450, 620),
        ))
    }

    @Test fun exposedMenuMovesAboveFieldWhenKeyboardLeavesTooLittleSpaceBelow() {
        assertEquals(IntOffset(100, 200), InstantFieldMenuPosition(48, 800, 300).calculatePosition(
            IntRect(100, 350, 300, 400), IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 150),
        ))
    }

    @Test fun exposedMenuDoesNotSubtractKeyboardTwiceFromResizedPopupWindow() {
        assertEquals(IntOffset(100, 200), InstantFieldMenuPosition(48, 800, 300).calculatePosition(
            IntRect(100, 350, 300, 400), IntSize(400, 500), LayoutDirection.Rtl, IntSize(200, 150),
        ))
    }

    @Test fun exposedMenuFallsBackToWindowEdgeInsteadOfCoveringAnchorCentre() {
        assertEquals(IntOffset(100, 48), InstantFieldMenuPosition(48, 500, 0).calculatePosition(
            IntRect(100, 220, 300, 260), IntSize(400, 500), LayoutDirection.Ltr, IntSize(200, 350),
        ))
    }
}
