package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbScaffold
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F50: the shared top-bar title autosizes down instead of letting the M3 TopAppBar grow.
 *
 * Three states, and the FIRST is the important one: a short title must still render at full
 * titleLarge, because the previous attempt at this problem (maxLines = 1 + ellipsis, reverted in
 * d2e8ecd71) broke 19 goldens by truncating ordinary titles.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbTopBarTitleGoldenTest {

    @Test fun short_title_unchanged() = captureMatrix("AbTopBarTitle", "short", heightDp = 120) {
        AbScaffold(title = "History", onNavigateUp = {}) { }
    }

    @Test fun long_title_shrinks_to_two_lines() = captureMatrix("AbTopBarTitle", "long", heightDp = 120) {
        AbScaffold(title = "Workspace color setting defaults", onNavigateUp = {}) { }
    }

    @Test fun very_long_title_ellipsises_at_the_floor() = captureGolden("AbTopBarTitle", "veryLong", EDGE_MODE, heightDp = 120) {
        AbScaffold(
            title = "Bookmarks & My Notes and every other exceedingly long screen title we could imagine here",
            onNavigateUp = {},
        ) { }
    }
}
