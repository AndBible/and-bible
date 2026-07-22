package net.bible.android.view.activity.page.screen

import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.sharedcore.window.WindowCommands
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** No-op fake — mockk was removed from this repo (Task 5 fix wave 1); no mocking framework is used. */
private val noopCommands = object : WindowCommands {
    override fun setActive(windowId: String) {}
    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {}
}

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ComposeReadingViewHostTest {
    // Verifies the host mounts a ComposeView into the supplied container (no MainBibleActivity boot).
    @Test fun installAddsComposeViewChild() {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())
        // ComposeReadingViewHost.mountComposeView is a static/companion helper taking the
        // collaborators explicitly so it can be unit-tested without a full activity.
        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightMode = false,
            pane = { },
        )
        assertTrue((0 until container.childCount).any { container.getChildAt(it) is ComposeView })
    }
}

/**
 * Fix wave 1 (stale-BibleView remount): [ComposeReadingViewGeneration] is the framework-free
 * counter [ComposeReadingViewHost.rebuild] delegates to. This is exercised directly (no
 * MainBibleActivity/Koin boot needed) because a headless unit test can't observe the real effect
 * (a destroyed-vs-fresh BibleView) — see `ComposeReadingViewHost`'s `generation`/`rebuild` kdoc for
 * why the bump is needed: `key(gen) { ... }` around `ReadingViewScreen` forces every pane's
 * `AndroidView` factory to re-run after a same-workspace reload clears the BibleView cache.
 */
class ComposeReadingViewGenerationTest {
    @Test fun rebuildBumpsGeneration() {
        val generation = ComposeReadingViewGeneration()
        assertEquals(0, generation.state.value)
        generation.rebuild()
        assertEquals(1, generation.state.value)
        generation.rebuild()
        generation.rebuild()
        assertEquals(3, generation.state.value)
    }
}
