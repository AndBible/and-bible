package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexError
import net.bible.sharedui.search.SearchIndexProgressScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchIndexProgressGoldenTest {

    private val sampleJobs = listOf(
        ProgressJob(id = "kjv", label = "Indexing KJV", percent = 40, indeterminate = false),
        ProgressJob(id = "esv", label = "Preparing ESV", percent = 0, indeterminate = true),
    )

    @Test
    fun searchIndexProgress_primary() {
        captureMatrix("SearchIndexProgress", "primary") {
            SearchIndexProgressScreen(
                title = "Search index",
                jobs = sampleJobs,
                noTasks = false,
                error = null,
                onHide = {},
                onDismissError = {},
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun searchIndexProgress_primary_rtl() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        captureRtl("SearchIndexProgress", "primary") {
            SearchIndexProgressScreen(
                title = context.getString(net.bible.android.activity.R.string.search_index),
                jobs = sampleJobs,
                noTasks = false,
                error = null,
                onHide = {},
                onDismissError = {},
            )
        }
    }

    @Test
    fun searchIndexProgress_noTasks() {
        captureGolden("SearchIndexProgress", "noTasks", EDGE_MODE) {
            SearchIndexProgressScreen(
                title = "Search index",
                jobs = emptyList(),
                noTasks = true,
                error = null,
                onHide = {},
                onDismissError = {},
            )
        }
    }

    @Test
    fun searchIndexProgress_error() {
        captureGolden("SearchIndexProgress", "error", EDGE_MODE) {
            SearchIndexProgressScreen(
                title = "Search index",
                jobs = sampleJobs,
                noTasks = false,
                error = SearchIndexError.FAILED,
                onHide = {},
                onDismissError = {},
            )
        }
    }
}
