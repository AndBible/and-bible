package net.bible.sharedui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3Api::class)
class AbModalBottomSheetBorderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun borderFollowsActualSheetAndIncludesHandleInBothThemesAndAnchors() {
        val dark = mutableStateOf(false)
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        lateinit var view: View
        var contentPosition = Offset.Zero
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            scope = rememberCoroutineScope()
            AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
                AbModalBottomSheet(onDismissRequest = {}, sheetState = state) {
                    view = LocalView.current
                    Box(Modifier.fillMaxWidth().height(380.dp).testTag("sheetContent").onGloballyPositioned {
                        contentPosition = it.positionInWindow()
                    })
                }
            }
        }
        fun assertOutline() {
            val root = view.rootView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { root.draw(Canvas(bitmap)) }
            try {
                val ink = if (dark.value) 0xFFFFFF else 0x000000
                val x = root.width / 2
                // Stock handle is 4dp with 22dp top/bottom padding. Check above it, not
                // a content-only rectangle; side sample is below the 28dp rounded corner.
                val top = (contentPosition.y - 48 * density).toInt()
                assertTrue("Sheet must have translated away from dialog top", top > 0)
                val nearTop = (top..top + (2 * density).toInt()).any {
                    (bitmap.getPixel(x, it) and 0xFFFFFF) == ink
                }
                assertTrue("Top outline follows actual sheet: dark=${dark.value} top=$top", nearTop)
                assertEquals("Side outline includes the drag handle area", ink,
                    bitmap.getPixel((contentPosition.x + 0.5f * density).toInt(),
                        (contentPosition.y - 8 * density).toInt()) and 0xFFFFFF)
                assertNotEquals("No static outline remains at dialog top", ink or 0xFF000000.toInt(),
                    bitmap.getPixel(x, 0))
            } finally { bitmap.recycle() }
        }
        rule.waitForIdle()
        for (theme in listOf(false, true)) for (expanded in listOf(false, true)) {
            rule.runOnIdle {
                dark.value = theme
                scope.launch { if (expanded) state.expand() else state.partialExpand() }
            }
            rule.waitForIdle()
            assertOutline()
            if (expanded) {
                val settled = state.requireOffset()
                rule.onNodeWithTag("sheetContent").performTouchInput {
                    down(Offset(center.x, 20f * density))
                    moveBy(Offset(0f, 24f * density))
                    moveBy(Offset(0f, 40f * density))
                }
                rule.waitForIdle()
                assertTrue("Physical drag must move actual M3 offset before release", state.requireOffset() > settled + 10f * density)
                assertOutline()
                rule.onNodeWithTag("sheetContent").performTouchInput { up() }
                rule.waitForIdle()
            }
        }
    }
}
