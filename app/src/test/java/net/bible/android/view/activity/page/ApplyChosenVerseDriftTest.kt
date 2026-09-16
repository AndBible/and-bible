package net.bible.android.view.activity.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b: the grid quick sheet returns no Intent, so it applies a chosen verse by calling the
 * SAME function the `onActivityResult` arm calls. A second copy is the failure this guards — and
 * the piece a copy would lose is the `NoSuchVerseException` toast, which only fires on a malformed
 * OSIS id and therefore never in testing.
 *
 * Reading-host re-typing R3 repointed this guard, and the repointing is the whole point of the
 * entry: R3 moved `applyChosenVerse`'s BODY to [ReadingCommands] and left a delegating stub of the
 * same name on [MainBibleActivity]. The pre-R3 form counted `fun applyChosenVerse(` in
 * `MainBibleActivity.kt` and required exactly 1 — which the stub satisfies, so the guard would have
 * gone on passing while silently watching the stub instead of the implementation. Moving the path to
 * `ReadingCommands.kt` would have been just as wrong in the other direction: it would stop seeing
 * the stub. So every scan below reads BOTH files and asserts one IMPLEMENTATION across them, plus
 * that the Activity's copy really is a pure delegation.
 */
class ApplyChosenVerseDriftTest {
    private val activitySrc =
        File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt").readText()
    private val commandsSrc =
        File("src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt").readText()
    private val bothSrc = activitySrc + "\n" + commandsSrc

    @Test fun bothScannedFilesExistAndAreNonEmpty() {
        // Anti-vacuity: every assertion below is a count over these two strings.
        assertTrue("MainBibleActivity.kt is empty or missing", activitySrc.length > 1000)
        assertTrue("ReadingCommands.kt is empty or missing", commandsSrc.length > 1000)
    }

    @Test fun thereIsExactlyOneApplyChosenVerseImplementation() {
        // Narrowed per controller amendment C1: `VerseFactory.fromString(` occurs twice in
        // `MainBibleActivity.kt` today — the second, at the "$book.$chapter" call site, is unrelated
        // pre-existing code this task does not touch. Do not widen this back to a bare
        // `VerseFactory\.fromString\(` count, or it goes red for a reason unrelated to a second
        // chosen-verse implementation.
        assertEquals(
            "VerseFactory.fromString(navigationControl.versification, …) must appear once across " +
                "MainBibleActivity.kt + ReadingCommands.kt, inside applyChosenVerse",
            1,
            Regex("""VerseFactory\.fromString\([\w.]*navigationControl\.versification""")
                .findAll(bothSrc).count(),
        )
        assertEquals(
            "verse_not_found must be posted from exactly one place across both files",
            1, Regex("""R\.string\.verse_not_found""").findAll(bothSrc).count(),
        )
    }

    @Test fun theActivitysCopyIsAPureDelegation() {
        val body = bodyOf(activitySrc, "fun applyChosenVerse(")
        assertTrue("the stub must delegate; got:\n$body", body.contains("readingCommands.applyChosenVerse("))
        assertFalse("…and must not re-implement; got:\n$body", body.contains("VerseFactory.fromString"))
    }

    @Test fun theActivityResultArmDelegatesRatherThanDuplicating() {
        // `applyChosenVerse(verseStr,` — with the comma — is the CALL in the result arm; the R3
        // stub's own signature reads `applyChosenVerse(verseStr: String`, so this cannot be
        // satisfied by the stub alone.
        assertTrue(activitySrc.contains("applyChosenVerse(verseStr,"))
    }

    /** The text from [signature] to the next member declaration at class-body indentation. */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("$signature not found", start >= 0)
        val rest = src.substring(start + signature.length)
        val end = Regex("""\n    (?:/\*\*|(?:internal |private |public )?(?:fun|val|var|@) )""")
            .find(rest)?.range?.first ?: rest.length
        return signature + rest.substring(0, end)
    }
}
