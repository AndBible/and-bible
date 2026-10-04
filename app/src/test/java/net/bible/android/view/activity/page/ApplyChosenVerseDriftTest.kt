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
 *
 * Reading-host re-typing R3 repointed this guard, and the repointing is the whole point of the
 * entry: R3 moved `applyChosenVerse`'s BODY to [ReadingCommands] and left a delegating stub of the
 * same name on `MainBibleActivity`. The pre-R3 form counted `fun applyChosenVerse(` in
 * `MainBibleActivity.kt` and required exactly 1 — which the stub satisfies, so the guard would have
 * gone on passing while silently watching the stub instead of the implementation. Moving the path to
 * `ReadingCommands.kt` would have been just as wrong in the other direction: it would stop seeing
 * the stub. So every scan below reads BOTH files and asserts one IMPLEMENTATION across them.
 *
 * Slice 8 F3 repointed the host half from `MainBibleActivity.kt` (deleted in F4) to
 * `NavHostComposeActivity.kt`, where the reading host's result dispatch lives now
 * (`applyInGraphStdResult`/`applyPendingActivityResult`), and deleted
 * `theActivitysCopyIsAPureDelegation`: the stub it watched was `MainBibleActivity`'s own, and the nav
 * host never had one.
 */
class ApplyChosenVerseDriftTest {
    private val hostSrc =
        File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt").readText()
    private val commandsSrc =
        File("src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt").readText()
    private val bothSrc = hostSrc + "\n" + commandsSrc

    @Test fun bothScannedFilesExistAndAreNonEmpty() {
        // Anti-vacuity: every assertion below is a count over these two strings.
        assertTrue("NavHostComposeActivity.kt is empty or missing", hostSrc.length > 1000)
        assertTrue("ReadingCommands.kt is empty or missing", commandsSrc.length > 1000)
    }

    @Test fun thereIsExactlyOneApplyChosenVerseImplementation() {
        // Narrowed per controller amendment C1: a bare `VerseFactory.fromString(` also matched an
        // unrelated "$book.$chapter" call site in the (since deleted) `MainBibleActivity.kt`. Keep it
        // narrow, so a second unrelated call site cannot turn this red for the wrong reason.
        assertEquals(
            "VerseFactory.fromString(navigationControl.versification, …) must appear once across " +
                "NavHostComposeActivity.kt + ReadingCommands.kt, inside applyChosenVerse",
            1,
            Regex("""VerseFactory\.fromString\([\w.]*navigationControl\.versification""")
                .findAll(bothSrc).count(),
        )
        assertEquals(
            "verse_not_found must be posted from exactly one place across both files",
            1, Regex("""R\.string\.verse_not_found""").findAll(bothSrc).count(),
        )
    }

    /**
     * **Repointed by reading-host re-typing T8c, and it had to be.** The arm this test watched —
     * `MainBibleActivity.onActivityResult`'s `PassageGrid`/`Bookmarks`/`ReadingProgress` case —
     * moved wholesale into [ReadingCommands.applyChosenPassageResult], because all four of those
     * screens are destinations of `NavHostComposeActivity`'s own graph now and their results reach
     * the reading host IN-GRAPH, never through an `onActivityResult`. Two dispatchers reading the
     * same extras would have been two things to keep true.
     *
     * The old assertion (`activitySrc.contains("applyChosenVerse(verseStr,")`) still PASSES against
     * the moved tree — but only because the R3 stub's own delegating body reads
     * `readingCommands.applyChosenVerse(verseStr, isFromBookmark)`. Its comment claimed that could
     * not happen; with the arm gone it does. A guard that has quietly stopped seeing its subject is
     * worse than a deleted one, so what is pinned now is the whole chain: the host's arm
     * delegates, and the SHARED applier is the one place that calls `applyChosenVerse`. (Slice 8 F3:
     * "the host" is the reading host, `NavHostComposeActivity.applyInGraphStdResult`, since classic
     * is deleted.)
     */
    @Test fun theActivityResultArmDelegatesRatherThanDuplicating() {
        assertTrue(
            "the reading host's PassageGrid/Bookmarks/ReadingProgress arm must delegate to the " +
                "shared applier rather than re-implementing it",
            hostSrc.contains("readingCommands.applyChosenPassageResult(kind, extras)"),
        )
        val shared = bodyOf(commandsSrc, "fun applyChosenPassageResult(")
        assertTrue(
            "ReadingCommands.applyChosenPassageResult must be what calls applyChosenVerse; got:\n$shared",
            shared.contains("applyChosenVerse(verseStr,"),
        )
    }

    /**
     * The other two arms that moved with it, for the same reason and with the same hazard: a second
     * copy of either would only be noticed by a user whose pick silently did nothing. The
     * `getBook(bookInitials)` count is the copy detector — that identifier appears only inside these
     * two arms, so a re-implementation on the reading host would raise it above zero.
     */
    @Test fun theMyDocumentArmsDelegateToo() {
        assertTrue(
            "the reading host's MyDocumentPages arm must delegate",
            hostSrc.contains("readingCommands.applyChosenMyDocumentPage(extras)"),
        )
        assertTrue(
            "the reading host's MyDocuments arm must delegate",
            hostSrc.contains("readingCommands.applyChosenMyDocument(extras)"),
        )
        assertEquals(
            "the my-document arms must not be re-implemented on the reading host beside the shared ones",
            0,
            Regex("""getBook\(bookInitials\)""").findAll(hostSrc).count(),
        )
        assertEquals(
            "…and must exist exactly once in ReadingCommands (the two arms, one read each)",
            2,
            Regex("""getBook\(bookInitials\)""").findAll(commandsSrc).count(),
        )
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
