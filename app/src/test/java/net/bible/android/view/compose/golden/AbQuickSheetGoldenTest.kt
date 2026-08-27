package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.components.AbQuickSheetFooterRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbQuickSheetGoldenTest {

    @Composable
    private fun rows(n: Int) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            repeat(n) { Text("Row ${it + 1}", Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        }
    }

    @Test fun quickSheet_plain() = captureMatrix("AbQuickSheet", "plain") {
        SheetSurface {
            AbQuickSheetContent(title = "Plain", onClose = {}) { rows(4) }
        }
    }

    @Test fun quickSheet_withFooter() = captureGolden("AbQuickSheet", "withFooter", GoldenMode.LIGHT) {
        SheetSurface {
            AbQuickSheetContent(
                title = "With footer",
                onClose = {},
                footer = { AbQuickSheetFooterRow(text = "Manage workspaces…", onClick = {}) },
            ) { rows(4) }
        }
    }

    @Test fun quickSheet_withTabs() = captureGolden("AbQuickSheet", "withTabs", GoldenMode.LIGHT) {
        SheetSurface {
            AbQuickSheetContent(
                title = "With tabs",
                onClose = {},
                tabs = listOf("Recent", "For this verse", "Last filter"),
                selectedTab = 1,
                onTabSelected = {},
                footer = { AbQuickSheetFooterRow(text = "All documents…", onClick = {}) },
            ) { rows(4) }
        }
    }

    @Test fun quickSheet_withBackAndActions() = captureGolden("AbQuickSheet", "withBackAndActions", GoldenMode.LIGHT) {
        SheetSurface {
            AbQuickSheetContent(
                title = "Genesis 1",
                onClose = {},
                canGoBack = { true },
                onBack = {},
                actions = { androidx.compose.material3.Text("⋮", Modifier.padding(end = 16.dp)) },
            ) { rows(4) }
        }
    }

    /**
     * Fix round 2, Finding 3: every capture above uses `rows(4)`, well under
     * [net.bible.sharedui.components.AbSheetContentMaxHeight] (400dp), and none overrides
     * `canScrollForward` from its default `{ false }` — so `Modifier.abBottomFade` never renders in
     * any of them, and a regression that silently disconnected `AbQuickSheetContent`'s
     * `canScrollForward` parameter from `AbSheetScrollBound` would pass every existing test. Enough
     * rows to overflow the bound, plus an explicit `canScrollForward = { true }`, proves the fade is
     * genuinely wired, not merely present in the modifier chain — the same idiom
     * `AbSheetWrappersGoldenTest.choiceOverflow` uses for the 14a wrappers.
     */
    @Test fun quickSheet_scrolling() = captureGolden("AbQuickSheet", "scrolling", GoldenMode.LIGHT) {
        SheetSurface {
            AbQuickSheetContent(title = "Scrolling", onClose = {}, canScrollForward = { true }) { rows(30) }
        }
    }
}
