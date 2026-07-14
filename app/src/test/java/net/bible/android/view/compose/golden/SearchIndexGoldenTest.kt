package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.search.SearchIndexScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchIndexGoldenTest {
    @Test fun searchIndex_create() {
        captureMatrix("SearchIndex", "create") {
            SearchIndexScreen("Search Index", documentName = "ESV", isRebuild = false, {}, {}, {})
        }
    }

    @Test fun searchIndex_rebuild() {
        captureGolden("SearchIndex", "rebuild", EDGE_MODE) {
            SearchIndexScreen("Search Index", documentName = "ESV", isRebuild = true, {}, {}, {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun searchIndex_create_rtl() {
        captureRtl("SearchIndex", "create") {
            SearchIndexScreen("Search Index", documentName = "ESV", isRebuild = false, {}, {}, {})
        }
    }
}
