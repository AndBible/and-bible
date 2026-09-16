package net.bible.android.view.compose

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * R1: the reading host does not ask its Activity for values that are reachable without one.
 *
 * Six members (spec §2.1 bucket (a)) are Koin singletons or derivable from the host's OWN
 * `windowControl` field (`ComposeReadingViewHost.kt:478`). Asking the Activity for them is what made
 * the 99-reference coupling look larger than it is, and every one of them survives the Activity's
 * deletion untouched.
 *
 * **Anti-vacuity.** The positive assertion is load-bearing: without it this file would pass against
 * an empty or renamed source, which is the failure mode spec §5 exists to prevent.
 */
class ReadingHostDelegationGuardTest {
    private val hostFile =
        File("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt")

    private val source: String get() = hostFile.readText()

    @Test
    fun theHostSourceIsActuallyThere() {
        assertTrue(hostFile.exists(), "ComposeReadingViewHost.kt not found — has it moved?")
        assertTrue(
            source.contains("class ComposeReadingViewHost("),
            "the scanned file must be the host itself, or this guard proves nothing",
        )
    }

    @Test
    fun theHostDoesNotAskTheActivityForKoinSingletons() {
        val delegated = listOf(
            "documentControl",
            "windowControl",
            "windowRepository",
            "toolbarButtonSetting",
            "speakControl",
            "linkControl",
        )
        val offenders = delegated.flatMap { member ->
            Regex("""(?<![.\w])activity\.$member\b""").findAll(source).map { member }
        }
        assertEquals(
            emptyList(), offenders.distinct().sorted(),
            "these are reachable without the Activity — inject them or read the host's own " +
                "windowControl field (ComposeReadingViewHost.kt:478)",
        )
    }

    @Test
    fun theHostStillHasItsOwnWindowControlField() {
        assertTrue(
            source.contains("private val windowControl: WindowControl by inject()"),
            "the replacement for activity.windowControl is the host's own injected field; if it is " +
                "gone, the deletions above were done by re-introducing a different coupling",
        )
    }
}
