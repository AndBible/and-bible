package net.bible.android.view.compose

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * R1: the reading host does not ask its Activity for values that are reachable without one.
 *
 * Six members (spec §2.1 bucket (a)) are Koin singletons or derivable from the host's OWN
 * `windowControl` field. Asking the Activity for them is what made the 99-reference coupling look
 * larger than it is, and every one of them survives the Activity's deletion untouched.
 *
 * **R6d corrected one of the six, and strengthened the guard rather than weakening it.**
 * `windowRepository` is NOT in the "reachable without the Activity" class after all, and R6c1's fix
 * round proved it one rung down: `windowControl.windowRepository` is whichever reading host most
 * recently RESUMED, which is a DIFFERENT object from the owning host's own repository for a frozen
 * or not-yet-resumed second host (`MainBibleActivity.onResume`/`unFreeze()` exist only to reconcile
 * the two). R1 substituted seven reads here for it and its reviewer verified that safe at the time;
 * R7 then made `MainBibleActivity.windowRepository` a view onto `ReadingAppBootstrap` instead of a
 * `lateinit var` kept in sync, which made the verification stale. So the ban on
 * `activity.windowRepository` STAYS (the reading view must not reach a host field by that name),
 * and [theHostDoesNotReadWindowControlsRepositoryEither] is added beside it: the correct spelling
 * is `activity.hostWindowRepository`, R6d's interface member, which every owning host answers with
 * its own.
 *
 * **Anti-vacuity.** The positive assertions are load-bearing: without them this file would pass
 * against an empty or renamed source, which is the failure mode spec §5 exists to prevent.
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

    /**
     * R6d, debt 1 of 3. The counterpart of
     * `ReadingCommandsHostDelegationGuardTest.windowRepositoryIsAnOwningHostSupplierNotWindowControls`,
     * applied at the top of the chain: seven sites in this file read `windowControl.windowRepository`
     * until R6d, and each of them meant "the repository of the host I am installed in".
     *
     * Both halves are pinned. The negative one alone would pass against a file that simply stopped
     * asking; the positive one alone would pass against a file that asked BOTH ways.
     */
    @Test
    fun theHostDoesNotReadWindowControlsRepositoryEither() {
        assertEquals(
            0, Regex("""(?<![.\w])windowControl\.windowRepository\b""").findAll(source).count(),
            "windowControl.windowRepository is whichever host most recently resumed, not " +
                "necessarily THIS host's own — read activity.hostWindowRepository (R6d/R6c1)",
        )
        assertTrue(
            Regex("""(?<![.\w])activity\.hostWindowRepository\b""").containsMatchIn(source),
            "the replacement is the owning host's own repository, reached through R6d's " +
                "ReadingHostActivity.hostWindowRepository at call time; if no site reads it, the " +
                "assertion above has nothing to protect and this guard is vacuous",
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
