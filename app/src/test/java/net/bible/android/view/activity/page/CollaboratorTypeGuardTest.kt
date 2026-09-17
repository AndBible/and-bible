package net.bible.android.view.activity.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing R5: the eight easy collaborators (`PageTiltScrollControl`,
 * `LlmDialogHelper`, `DocumentViewManager`, `BibleViewFactory`, `BibleGestureListener`, and the
 * three `OptionsMenuItems` preference classes) each held a `MainBibleActivity`-typed value. This
 * guard is the net: no scanned file may declare a `MainBibleActivity`-typed parameter, AND no
 * scanned file may bridge the gap with a local `as MainBibleActivity` downcast either.
 *
 * **Fix round 1 (review) added the second assertion.** The first version only scanned for the
 * declared type (`: MainBibleActivity`) and went green on `DocumentViewManager`/`BibleViewFactory`,
 * which had merely been re-labelled: both still reached the full `MainBibleActivity` surface
 * through `(x as MainBibleActivity)` at every access. A parameter typed `Any`, or one spelled with
 * a fully-qualified name, would have passed the old assertion just as easily -- the cast is the
 * real tell that a "retype" changed nothing behind the signature.
 *
 * **R6a moved the last three files in.** R5 had to leave `BibleViewFactory.kt` excluded and
 * reported BLOCKED: its only reason for holding the value was to hand the whole thing to
 * `BibleView`, which reached 14 distinct `MainBibleActivity` members. R6a decomposed that
 * ([BibleViewHostCallbacks]), so the factory followed mechanically and the exclusion — plus the
 * `theExcludedFileIsNamedAndStillNeedsMainBibleActivity` test that pinned it to the real code — is
 * gone, which is exactly what a self-invalidating exclusion is FOR. `BibleJavascriptInterface.kt`
 * came with them: it read `bibleView.mainBibleActivity` and was the largest single consumer of the
 * property R6a deletes, so it could not be left behind.
 */
class CollaboratorTypeGuardTest {
    private val files = listOf(
        "src/main/java/net/bible/android/control/page/PageTiltScrollControl.kt",
        "src/main/java/net/bible/android/view/activity/ai/LlmDialogHelper.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/DocumentViewManager.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleGestureListener.kt",
        "src/main/java/net/bible/android/view/activity/page/OptionsMenuItems.kt",
        // R6a: the two files R5 could not reach. `BibleView` now takes R4's `ReadingHostActivity`
        // plus a [BibleViewHostCallbacks] bundle, and `BibleViewFactory` -- which only ever held
        // the value to hand it on -- follows it mechanically.
        "src/main/java/net/bible/android/view/activity/page/BibleView.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleViewFactory.kt",
        // R6a pulled this in too: it reads `bibleView.mainBibleActivity` and was the single
        // largest consumer of the property R6a deletes (58 references / 13 members).
        "src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt",
        // R6b: the inset ledger. It held the Activity for seven chrome reads and one padding
        // write; it now takes a [ReadingInsetsHostCallbacks] bundle instead (addendum Ruling C).
        "src/main/java/net/bible/android/view/activity/page/ReadingInsets.kt",
        // R6c2: the command surface and the menu/drawer command handler. Both now take R4's
        // [ReadingHostActivity] plus a [ReadingCommandsHostCallbacks] bundle (the handler takes the
        // bundle's `hostActivity` and two of its suppliers directly).
        "src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt",
        "src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt",
        // R6d, debt 2 of 3: none of the three callback BUNDLES was under a type scan, and every
        // one of them would have passed every guard in this repo with a `val hostActivity` of the
        // Activity's own type added to it. They are the seam the whole batch routes through, so
        // they are scanned like the collaborators they serve. (`ReadingInsetsHostCallbacks` needs
        // no entry of its own: it is declared in `ReadingInsets.kt`, already listed above.)
        "src/main/java/net/bible/android/view/activity/page/BibleViewHostCallbacks.kt",
        "src/main/java/net/bible/android/view/activity/page/ReadingCommandsHostCallbacks.kt",
        // R6d: the reading host itself -- the file this whole batch exists to re-type.
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
        // T8a item 1: the page whose `startKeyChooser` branched on `context !is MainBibleActivity`
        // and returned. It was never in this list even though it is exactly the coupling this guard
        // polices -- and the two assertions above could not have caught it either, which is why
        // [noEasyCollaboratorBranchesOnTheHostBeingMainBibleActivity] exists.
        "src/main/java/net/bible/android/control/page/CurrentGeneralBookPage.kt",
    )

    @Test
    fun theScannedFilesExist() = files.forEach { assertTrue("$it not found", File(it).exists()) }

    /**
     * **R6d narrowed the regex by one lookahead, and only that.** `MainBibleActivity` followed by
     * a `.` is a NESTED type, not the Activity: `ComposeReadingViewHost` catches
     * `MainBibleActivity.KeyIsNull` around the reference overlay's text, and that is a nested
     * exception class Ruling E allow-lists and slice 7 Task 13 re-homes. Ruling E's own scan below
     * is what decides which nested members are tolerated, member by member; leaving them to fail
     * HERE would have meant either editing the catch (a behaviour change in a re-typing commit) or
     * excluding the whole file. The Activity's own type still fails, which is the whole point of
     * this assertion -- see the red/green record in the R6d task report.
     */
    @Test
    fun noEasyCollaboratorDeclaresAMainBibleActivityParameter() {
        val offenders = files.filter { path ->
            Regex(""":\s*MainBibleActivity\b(?!\s*\.)""").containsMatchIn(File(path).readText())
        }
        assertEquals("R5 re-types these off the Activity", emptyList<String>(), offenders)
    }

    @Test
    fun noEasyCollaboratorBridgesTheGapWithADowncast() {
        val offenders = files.filter { path -> File(path).readText().contains("as MainBibleActivity") }
        assertEquals(
            "a retype that still downcasts to the full Activity every time it is used is a " +
                "re-label, not a re-type (review Critical 1) -- push the needed value in as a " +
                "constructor argument instead, or exclude the file here and report it blocked",
            emptyList<String>(), offenders,
        )
    }

    /**
     * **T8a item 1: the third bridging shape — a TYPE TEST.**
     *
     * The two assertions above see a declared `: MainBibleActivity` parameter and an
     * `as MainBibleActivity` downcast. `CurrentGeneralBookPage.startKeyChooser` did neither: it
     * took an `ActivityBase` and opened with `if(context !is MainBibleActivity) return`, so both
     * went green on a file whose whole body ran only for the classic Activity — and the failure was
     * the worst shape this batch recognises, a silent no-op with no log line and no crash (a dead
     * key-chooser button on the nav host).
     *
     * `is`/`!is` only. `as? MainBibleActivity` is deliberately NOT matched here: the repository's
     * two other sites (`Dialogs.kt:284`, `LinkControl.kt:389`) use it to choose between a Compose
     * sheet and a plain fallback that still happens, which is a documented non-silent branch and
     * not this guard's business. Neither file is scanned here anyway; widening the regex so that
     * they would fail if they were is how a guard gets weakened later to make them pass.
     */
    @Test
    fun noEasyCollaboratorBranchesOnTheHostBeingMainBibleActivity() {
        val offenders = files.filter { path ->
            Regex("""\bis\s+MainBibleActivity\b""").containsMatchIn(codeOf(File(path)))
        }
        assertEquals(
            "a collaborator that TESTS whether its host is MainBibleActivity is coupled to the " +
                "Activity just as tightly as one that declares or casts it, and the arm it skips " +
                "is invisible: on a second host the body silently does nothing. Push in the value " +
                "the body actually needed instead",
            emptyList<String>(), offenders,
        )
    }

    // ---------------------------------------------------------------- R6a: Ruling E's scan
    //
    // `assertEquals(0, Regex("\\bMainBibleActivity\\b")...)` over these files cannot pass and the
    // addendum's Ruling E says so: decoupling the TYPE does not remove the companion constant and
    // the nested event classes that live on it, and re-homing THOSE is Task 13's. So the honest
    // assertion is the one below -- every surviving `MainBibleActivity` token in code (comments and
    // string literals stripped) must be a member reference that is named in [allowedNestedMembers],
    // which makes the allowance visible rather than implied.

    /**
     * The three files R6a re-types plus R6b's `ReadingInsets`. A subset of [files]; scanned harder.
     *
     * `ReadingInsets.kt` earns the harder scan because after R6b the only `MainBibleActivity`
     * tokens left in its CODE are the two nested event classes it posts
     * (`SearchSheetOffsetsUpdated`, `ImePaddingChanged`), both already on [allowedNestedMembers] and
     * both Task 13's to re-home. Anything else appearing there -- a type position, an import, a bare
     * pass of the Activity into the ledger -- is the regression this list exists to catch.
     */
    private val readingViewFiles = listOf(
        "src/main/java/net/bible/android/view/activity/page/BibleView.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleViewFactory.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt",
        "src/main/java/net/bible/android/view/activity/page/ReadingInsets.kt",
        // R6c2, count corrected by R6d fix round 2. `ReadingCommands.kt` has TWO surviving code
        // tokens: `MainBibleActivity.WORKSPACE_CHANGED` (the request code of the full workspace
        // selector's activity result, which `MainBibleActivity.onActivityResult` still owns) and
        // -- since R6d fix round 1 hoisted `pageTitleText` into this file -- the
        // `MainBibleActivity.KeyIsNull()` that body throws for a null key. Both are on
        // [allowedNestedMembers] and both are Task 13's to re-home, so nothing failed when the
        // second arrived; the count is corrected because an understated comment is how an
        // allow-list quietly stops describing what it allows. `MenuCommandHandler.kt` has none
        // left at all. Anything else here -- a type position, an import, a bare pass of the
        // Activity into either collaborator -- is the regression this list exists to catch.
        "src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt",
        "src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt",
        // R6d: the two callback-bundle files and the reading host itself. The host's surviving
        // code tokens are `MainBibleActivity.WORKSPACE_CHANGED` (the workspace selector's request
        // code), `.KeyIsNull` (caught around the reference overlay's text) and `.FullScreenEvent`
        // (the event it subscribes to) -- all three on [allowedNestedMembers], all three Task 13's
        // to re-home. The bundles have none at all.
        "src/main/java/net/bible/android/view/activity/page/BibleViewHostCallbacks.kt",
        "src/main/java/net/bible/android/view/activity/page/ReadingCommandsHostCallbacks.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
    )

    /**
     * The files that may carry `import net.bible.android.view.activity.page.MainBibleActivity`.
     *
     * Ruling E tolerates the nested members in [allowedNestedMembers], and a file in a DIFFERENT
     * package cannot name one without importing the outer class -- the import is the allowance's
     * cost, not a second coupling. Every other scanned file sits in
     * `net.bible.android.view.activity.page` itself and needs no import, so for them an import
     * stays an offender.
     *
     * Pinned by [everyFileAllowedToImportStillNeedsTheImport]: an entry whose file stops naming an
     * allow-listed member must be deleted, or the allowance silently outlives its reason.
     */
    private val filesAllowedToImportMainBibleActivity = setOf(
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
    )

    /**
     * Ruling E's allow-list: nested EVENT classes and one companion constant, reached as
     * `MainBibleActivity.<member>`. These are not the Activity TYPE -- nothing here couples the
     * reading view to the Activity's surface -- and slice 7 Task 13 re-homes them. Each entry is
     * pinned by [everyAllowedNestedMemberIsStillReferenced], so an entry that stops being used must
     * be deleted from this list rather than quietly widening what the guard tolerates.
     */
    private val allowedNestedMembers = listOf(
        "ConfigurationChanged",
        "FullScreenEvent",
        "SystemInsetsChangedEvent",
        "AgentLogOffsetsUpdated",
        "SearchSheetOffsetsUpdated",
        "ImePaddingChanged",
        "WORKSPACE_CHANGED",
        // R6d: `ComposeReadingViewHost.readOverlayText()` catches it around the reference
        // overlay's text. A nested exception class, not the Activity's surface; Task 13's to
        // re-home with the rest.
        "KeyIsNull",
    )

    /**
     * Anti-vacuity. A scan of a file that is missing, empty, or no longer the class it is named for
     * proves nothing at all, and both failure modes are silent -- [File.readText] on a renamed file
     * throws, but a file that merely lost its class would pass every assertion below forever.
     */
    @Test
    fun theScannedReadingViewFilesAreTheClassesTheyAreNamedFor() {
        readingViewFiles.forEach { path ->
            val file = File(path)
            assertTrue("$path not found", file.isFile)
            val declaration = "class " + path.substringAfterLast('/').removeSuffix(".kt")
            assertTrue(
                "$path no longer declares `$declaration` -- the scans below would be vacuous",
                codeOf(file).contains(declaration),
            )
        }
    }

    @Test
    fun noReadingViewFileNamesMainBibleActivityOutsideTheAllowList() {
        val offenders = readingViewFiles.flatMap { path ->
            val code = codeOf(File(path))
            MAIN_BIBLE_ACTIVITY_REFERENCE.findAll(code)
                .filterNot { it.groupValues[1] in allowedNestedMembers }
                // R6d: a file outside `…activity.page` must import the outer class to reach an
                // allow-listed nested member at all. Allowed only for the named files, and only
                // on the import line itself.
                .filterNot {
                    path in filesAllowedToImportMainBibleActivity && isTheImportLine(code, it.range.first)
                }
                .map { "$path: ${it.value.replace(Regex("\\s+"), "")}" }
                .toList()
        }
        assertEquals(
            "R6a takes the reading view off MainBibleActivity. Every remaining mention must be a " +
                "nested event class or companion constant named in `allowedNestedMembers` " +
                "(addendum Ruling E) -- a type position, an import or a bare pass is an offender. " +
                "Note the sibling assertions: a parameter typed `Any`, or one recovered with " +
                "`as MainBibleActivity`, is caught there, not here.",
            emptyList<String>(), offenders,
        )
    }

    @Test
    fun everyAllowedNestedMemberIsStillReferenced() {
        val code = readingViewFiles.joinToString("\n") { codeOf(File(it)) }
        val referenced = MAIN_BIBLE_ACTIVITY_REFERENCE.findAll(code).map { it.groupValues[1] }.toSet()
        assertEquals(
            "these allow-list entries are no longer referenced anywhere in the reading view -- " +
                "delete them, or the guard silently tolerates a member nothing uses",
            emptyList<String>(), allowedNestedMembers.filterNot { it in referenced },
        )
    }

    /**
     * Anti-staleness for [filesAllowedToImportMainBibleActivity] -- the shape Ruling E asks for: an
     * allow-list entry that stops being needed must FAIL, not linger. A file whose only reason to
     * import the Activity was an allow-listed nested member, and which no longer names one, is
     * carrying a dead import and a live allowance.
     */
    @Test
    fun everyFileAllowedToImportStillNeedsTheImport() {
        val stale = filesAllowedToImportMainBibleActivity.filterNot { path ->
            MAIN_BIBLE_ACTIVITY_REFERENCE.findAll(codeOf(File(path)))
                .any { it.groupValues[1] in allowedNestedMembers }
        }
        assertEquals(
            "these files may import MainBibleActivity only so they can reach an allow-listed " +
                "nested member, and they no longer reach one -- drop the import and the entry",
            emptyList<String>(), stale.sorted(),
        )
    }

    /** Whether the match at [offset] sits on an `import ` line of [code]. */
    private fun isTheImportLine(code: String, offset: Int): Boolean =
        code.substring(code.lastIndexOf('\n', offset) + 1, offset).trimStart().startsWith("import ")

    /**
     * [file]'s source with comments, string literals and character literals blanked out, so a scan
     * reads CODE. A regex-per-construct pass is not good enough here: `BibleView.kt` alone carries
     * nested block comments, raw (`"""`) strings holding JavaScript, and `//` sequences inside URL
     * string literals, each of which defeats one of the obvious one-liners. This is a single
     * left-to-right pass instead, which handles all three by construction.
     */
    private fun codeOf(file: File): String {
        val src = file.readText()
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val rest = src.length - i
            when {
                rest >= 2 && src.startsWith("//", i) -> {
                    while (i < src.length && src[i] != '\n') { out.append(' '); i++ }
                }
                rest >= 2 && src.startsWith("/*", i) -> {
                    var depth = 0
                    while (i < src.length) {
                        if (src.startsWith("/*", i)) { depth++; out.append("  "); i += 2 }
                        else if (src.startsWith("*/", i)) {
                            depth--; out.append("  "); i += 2
                            if (depth == 0) break
                        } else { out.append(if (src[i] == '\n') '\n' else ' '); i++ }
                    }
                }
                rest >= 3 && src.startsWith("\"\"\"", i) -> {
                    out.append("   "); i += 3
                    while (i < src.length && !src.startsWith("\"\"\"", i)) {
                        out.append(if (src[i] == '\n') '\n' else ' '); i++
                    }
                    out.append("   "); i = minOf(src.length, i + 3)
                }
                src[i] == '"' -> {
                    out.append(' '); i++
                    while (i < src.length && src[i] != '"') {
                        if (src[i] == '\\') { out.append(' '); i++ }
                        if (i < src.length) { out.append(' '); i++ }
                    }
                    if (i < src.length) { out.append(' '); i++ }
                }
                else -> { out.append(src[i]); i++ }
            }
        }
        return out.toString()
    }

    private companion object {
        /** `MainBibleActivity` and, if it is a member access, the member's name in group 1. */
        val MAIN_BIBLE_ACTIVITY_REFERENCE = Regex("""\bMainBibleActivity\b\s*(?:\.\s*(\w+))?""")
    }
}
