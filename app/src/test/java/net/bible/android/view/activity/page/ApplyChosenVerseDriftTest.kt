package net.bible.android.view.activity.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b: the grid quick sheet returns no Intent, so it applies a chosen verse by calling the
 * SAME function the `onActivityResult` arm calls. A second copy is the failure this guards — and
 * the piece a copy would lose is the `NoSuchVerseException` toast, which only fires on a malformed
 * OSIS id and therefore never in testing.
 */
class ApplyChosenVerseDriftTest {
    private val src = File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt").readText()

    @Test fun thereIsExactlyOneApplyChosenVerseImplementation() {
        assertEquals(1, Regex("""fun applyChosenVerse\(""").findAll(src).count())
    }

    @Test fun onlyThatFunctionParsesAChosenVerseAndOnlyItToasts() {
        // Narrowed per controller amendment C1: `VerseFactory.fromString(` occurs twice in this
        // file today — the second, at the "$book.$chapter" call site, is unrelated pre-existing
        // code this task does not touch. Do not widen this back to a bare `VerseFactory\.fromString\(`
        // count, or it goes red for a reason unrelated to a second chosen-verse implementation.
        assertEquals(
            "VerseFactory.fromString(navigationControl.versification, …) must appear once, inside applyChosenVerse",
            1,
            Regex("""VerseFactory\.fromString\(navigationControl\.versification""").findAll(src).count(),
        )
        assertEquals(
            "verse_not_found must be posted from exactly one place",
            1, Regex("""R\.string\.verse_not_found""").findAll(src).count(),
        )
    }

    @Test fun theActivityResultArmDelegatesRatherThanDuplicating() {
        assertTrue(src.contains("applyChosenVerse(verseStr"))
    }
}
