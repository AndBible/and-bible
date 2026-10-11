package net.bible.test

import android.os.Looper
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertTrue

/** Await the actual destination, including asynchronous settings loading and composition. */
fun awaitHostRoute(activity: NavHostComposeActivity, route: String, advanceComposition: () -> Unit = {}) {
    awaitHostCondition("host must reach ${route.substringBefore('?')}", advanceComposition) {
        activity.currentRouteForTest()?.substringBefore('?') == route.substringBefore('?')
    }
}

/** Wait for real startup effects, not just Activity.resume(): settings loading suspends graph creation. */
fun awaitReadingGraph(activity: NavHostComposeActivity, advanceComposition: () -> Unit = {}) {
    awaitHostCondition("fixture must publish the real visible Reading destination", advanceComposition) {
        activity.currentRouteForTest()?.substringBefore('?') == NavRoutes.READING &&
            activity.composeReadingViewHost != null && ReadingViewVisibility.isVisible
    }
}

private fun awaitHostCondition(message: String, advanceComposition: () -> Unit, ready: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (!ready() && System.nanoTime() < deadline) {
        // One task rather than idle(): a Compose popup can keep the looper busy forever.
        shadowOf(Looper.getMainLooper()).runOneTask()
        advanceComposition()
        Thread.sleep(10)
    }
    assertTrue(ready(), message)
}
