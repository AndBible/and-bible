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
    )

    @Test
    fun theScannedFilesExist() = files.forEach { assertTrue("$it not found", File(it).exists()) }

    @Test
    fun noEasyCollaboratorDeclaresAMainBibleActivityParameter() {
        val offenders = files.filter { path ->
            Regex(""":\s*MainBibleActivity\b""").containsMatchIn(File(path).readText())
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

    // ---------------------------------------------------------------- R6a: Ruling E's scan
    //
    // `assertEquals(0, Regex("\\bMainBibleActivity\\b")...)` over these files cannot pass and the
    // addendum's Ruling E says so: decoupling the TYPE does not remove the companion constant and
    // the nested event classes that live on it, and re-homing THOSE is Task 13's. So the honest
    // assertion is the one below -- every surviving `MainBibleActivity` token in code (comments and
    // string literals stripped) must be a member reference that is named in [allowedNestedMembers],
    // which makes the allowance visible rather than implied.

    /** The three files R6a re-types. A subset of [files]; scanned harder. */
    private val readingViewFiles = listOf(
        "src/main/java/net/bible/android/view/activity/page/BibleView.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleViewFactory.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt",
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
            MAIN_BIBLE_ACTIVITY_REFERENCE.findAll(codeOf(File(path)))
                .filterNot { it.groupValues[1] in allowedNestedMembers }
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
