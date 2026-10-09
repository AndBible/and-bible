package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import net.bible.android.TEST_SDK
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.components.AbSheetContentMaxHeight
import net.bible.sharedui.workspaces.WorkspaceQuickContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// @GraphicsMode NATIVE is REQUIRED: without it Roborazzi captures Robolectric's legacy-graphics
// dump of an empty ComposeView instead of a render, and every variant comes out byte-identical.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Every golden test in this directory carries this @Config. Without it Robolectric boots the
// real BibleApplication (the full app startup), which a golden of a stateless screen must not depend on.
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceQuickGoldenTest {
    private fun rows() = listOf(
        WorkspaceRowVd("a", "Study", null, 0xFF1B5E20.toInt(), isCurrent = false),
        WorkspaceRowVd("b", "Devotional", null, 0xFF444444.toInt(), isCurrent = true),
        WorkspaceRowVd("c", "Greek work", null, 0xFF6A1B9A.toInt(), isCurrent = false),
    )

    // I2 (whole-branch review fix wave): wrapped in SheetSurface, the same helper
    // AbQuickSheetGoldenTest uses, so the body's real (now content-sized, not 400dp-pinned) extent
    // is visible against a painted surface instead of invisible on the harness's bare background.
    @Test fun workspaceQuick_populated() = captureMatrix("WorkspaceQuick", "populated") {
        SheetSurface {
            Box(Modifier.heightIn(max = AbSheetContentMaxHeight)) {
                WorkspaceQuickContent(rows = rows(), onSelect = {})
            }
        }
    }
}
