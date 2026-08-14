package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbTopBarSearchGoldenTest {
    private val callbacks = AbTopBarSearchCallbacks(
        onQueryChange = {}, onClose = {}, onImeRequestHandled = {},
    )

    @Composable
    private fun bar(query: String) = AbScaffold(
        title = "Select workspace",
        onNavigateUp = {},
        search = AbTopBarSearchState(query = query),
        searchCallbacks = callbacks,
    ) { }

    // Light AND dark: the bar's content colour is the thing most likely to break, and the
    // untinted-content-colour defect family (batch 3 F1) is invisible in light mode alone.
    @Test fun abTopBarSearch_empty() { captureMatrix("AbTopBarSearch", "empty", heightDp = 120) { bar("") } }

    @Test fun abTopBarSearch_typed() { captureMatrix("AbTopBarSearch", "typed", heightDp = 120) { bar("Ser") } }
}
