package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A test that builds a `NavHostComposeActivity` must close it before the next DB reset.
 *
 * An activity left open keeps its composition alive. Its start-up `produceState` runs
 * `DocumentSyncSettings.ensureLoaded()`, which holds `dbMutex` across a real IO read; Robolectric then
 * drops the main-looper message that would resume it, so on a slow disk the mutex stays locked for the
 * rest of the fork and every later `DatabaseResetter.resetDocumentSyncFixture` times out
 * (`stage=settings-mutex mutexLocked=true`, 58 failures in one full verify). Closing the controller
 * destroys the activity and cancels the composition, which releases the mutex.
 *
 * Closing means going through the fixture: [closeReadingHostsBeforeReset], `closeController`, or an explicit
 * `.close()` / `.destroy()` of the controller.
 */
class NavHostFixtureClosureGuardTest {
    private val closers = listOf("closeReadingHostsBeforeReset", "closeController", ".close()", ".destroy()")

    /** Files outside the scan: the rule that documents the looper reset itself builds no host. */
    private val exempt = setOf("ComposeUiDispatcherResetRule.kt", "NavHostFixtureClosureGuardTest.kt")

    internal fun offenders(files: List<File>): List<String> = files
        .filter { it.extension == "kt" && it.name !in exempt }
        .filter { f ->
            val t = f.readText()
            "buildActivity(NavHostComposeActivity" in t && closers.none { it in t }
        }
        .map { it.path }

    @Test fun everyNavHostBuildingTestClosesItsHost() =
        assertEquals(emptyList<String>(), offenders(File("src/test/java").walkTopDown().toList()))

    @Test fun theScanSeesTheTestSources() =
        assertTrue(
            "run from app/ -- the scan would pass vacuously",
            File("src/test/java").walkTopDown().any { it.name == "HostInsetOwnershipTest.kt" || it.name == "UsableBibleGateTest.kt" },
        )

    @Test fun theGuardCanFail() {
        val probe = File.createTempFile("navHostProbe", ".kt").apply {
            writeText("val a = Robolectric.buildActivity(NavHostComposeActivity::class.java).setup().get()")
            deleteOnExit()
        }
        assertEquals(listOf(probe.path), offenders(listOf(probe)))
        val closed = File.createTempFile("navHostClosedProbe", ".kt").apply {
            writeText("val c = Robolectric.buildActivity(NavHostComposeActivity::class.java); closeReadingHostsBeforeReset(list)")
            deleteOnExit()
        }
        assertEquals(emptyList<String>(), offenders(listOf(closed)))
    }
}
