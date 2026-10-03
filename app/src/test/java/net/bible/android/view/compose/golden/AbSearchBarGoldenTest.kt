package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search bar's [AbScaffold] `searchActions` slot: a caller-supplied action must render to the
 * LEFT of the built-in Clear button, so Clear stays the edge-most action whether or not the caller
 * contributes anything. Round 9a Plan A Task 1 — ManageLabels needs it for its search-mode picker.
 *
 * `imeRequest = null` deliberately: an IME request would make the capture depend on Robolectric's
 * keyboard, and this golden is about the actions row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbSearchBarGoldenTest {

    private fun bar(query: String) = @Composable {
        AbScaffold(
            title = "Labels",
            onNavigateUp = {},
            search = AbTopBarSearchState(query = query, imeRequest = null),
            searchCallbacks = AbTopBarSearchCallbacks(
                onQueryChange = {},
                onClose = {},
                onImeRequestHandled = {},
            ),
            searchActions = { AbActionIcon(Icons.Filled.Tune, "mode") {} },
        ) { Box(androidx.compose.ui.Modifier) }
    }

    /** Empty query: no Clear button, so the caller's action is the only one. */
    @Test fun searchBar_actionsOnly() =
        captureGolden("AbSearchBar", "actionsOnly", EDGE_MODE, heightDp = 120, content = bar(""))

    /** Non-empty query: caller's action then Clear — the ordering this task exists to fix. */
    @Test fun searchBar_actionsAndClear() =
        captureGolden("AbSearchBar", "actionsAndClear", EDGE_MODE, heightDp = 120, content = bar("gen"))
}
