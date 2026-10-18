package net.bible.android.view.activity.nav

import net.bible.android.view.compose.ClassicRemovalScan
import net.bible.test.ProductionSources
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * I2 Stream A Task 2: the single `NavHost` lives in `:sharedUi`'s `AppRoot`, the activity only calls it.
 * A source scan, so it must prove its subject exists before asserting an absence (a scan of a moved
 * file passes vacuously).
 */
class AppRootWiringGuardTest {
    private val code: String by lazy {
        val host = ProductionSources.resolve("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")
        assertTrue(host.exists(), "the nav host source moved; this scan is watching nothing")
        ClassicRemovalScan.codeLinesOf(host.path)
    }

    @Test
    fun activityHasNoNavHostCall() {
        assertTrue(code.contains("setContent"), "scan reads the wrong text: no setContent in the stripped source")
        assertFalse(
            Regex("""(?<![A-Za-z])NavHost\s*\(""").containsMatchIn(code),
            "NavHostComposeActivity composes a NavHost( itself; the NavHost belongs to AppRoot in :sharedUi",
        )
    }

    @Test
    fun activityCallsAppRoot() {
        assertTrue(Regex("""(?<![A-Za-z])AppRoot\s*\(""").containsMatchIn(code), "the activity must call AppRoot(")
    }
}
