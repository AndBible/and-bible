package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b Task 9: `ComposeReadingViewHost.currentKeyChooserPage()` classifies the active JSword
 * page for `KeyChooserRoute`, and its `is` chain has an ordering trap that no type checker and no
 * `:sharedCore` test can see.
 *
 * `CurrentMyNotePage` EXTENDS `CurrentCommentaryPage` and does not override `startKeyChooser`, so a
 * chain that tests the commentary branch first silently classifies every my-note page as a
 * commentary. Today both route to the same Grid sheet, so the bug would be invisible — which is
 * exactly why it is worth pinning now, before the two ever diverge (see `KeyChooserPage`'s kdoc).
 *
 * A source-shape assertion is crude, but the alternative — constructing a real `CurrentMyNotePage`,
 * which needs a `CurrentPageManager`, a `BibleTraverser` and a live Bible document — costs orders of
 * magnitude more to set up than the defect costs to state.
 */
class KeyChooserDispatchGuardTest {
    private val host = net.bible.test.ProductionSources.resolve("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt")

    private fun dispatchBody(): String {
        val src = host.readText()
        val start = src.indexOf("internal fun currentKeyChooserPage()")
        assertTrue("the host must declare currentKeyChooserPage()", start >= 0)
        val end = src.indexOf("\n    }", start)
        assertTrue("currentKeyChooserPage() must have a body", end > start)
        return src.substring(start, end)
    }

    @Test fun theMyNoteBranchIsTestedBeforeTheCommentaryBranchItExtends() {
        val body = dispatchBody()
        val myNote = body.indexOf("is CurrentMyNotePage ->")
        val commentary = body.indexOf("is CurrentCommentaryPage ->")
        assertTrue("currentKeyChooserPage() must classify CurrentMyNotePage", myNote >= 0)
        assertTrue("currentKeyChooserPage() must classify CurrentCommentaryPage", commentary >= 0)
        assertTrue(
            "CurrentMyNotePage extends CurrentCommentaryPage, so its branch must come FIRST " +
                "(my-note at $myNote, commentary at $commentary)",
            myNote < commentary,
        )
    }

    /** The other subtype pair in the same chain: every page shape KeyChooserPage names is decided. */
    @Test fun everyPageShapeIsClassified() {
        val body = dispatchBody()
        listOf(
            "is CurrentBiblePage ->", "is CurrentMyNotePage ->", "is CurrentCommentaryPage ->",
            "is CurrentDictionaryPage ->", "is CurrentMapPage ->", "is CurrentGeneralBookPage ->",
        ).forEach { branch ->
            assertTrue("currentKeyChooserPage() must contain `$branch`", body.contains(branch))
        }
    }

    // ---- The apply rule moved to the common slot (QuickSheets.kt): pinned at its new owner. ----

    private val quickSheets = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/host/slots/QuickSheets.kt")

    /** The body of `applyKeyChooserKey` in [src]; an EPUB entry must carry its OWN document. */
    private fun applyRuleViolations(src: String): List<String> {
        val start = src.indexOf("private fun applyKeyChooserKey(")
        if (start < 0) return listOf("applyKeyChooserKey is missing")
        // Only this function's own body: cut at the next top-level declaration, so text further down the
        // file cannot satisfy a pin.
        val end = Regex("""\n(?:private fun|internal fun|fun|@Composable)""").find(src, start + 1)?.range?.first ?: src.length
        val body = src.substring(start, end)
        val out = mutableListOf<String>()
        val bookAndKey = body.indexOf("key is BookAndKey")
        if (bookAndKey < 0) out += "the GeneralBook arm no longer tests `key is BookAndKey`"
        else if (!Regex("""key is BookAndKey\)\s*\{\s*actions\.applyChosenGenBookKey\(key\.document, key\)""").containsMatchIn(body)
        ) out += "a BookAndKey must be applied with key.document"
        if (!Regex("""KeyChooserKind\.Map\s*->\s*actions\.applyChosenGenBookKey\(slots\.keyChooserPage\.currentDocument\(kind\), key\)""")
                .containsMatchIn(body)
        ) out += "a map key must be applied with the page's current document"
        if (!Regex("""\}\s*else\s*\{\s*actions\.applyChosenGenBookKey\(slots\.keyChooserPage\.currentDocument\(kind\), key\)""")
                .containsMatchIn(body)
        ) out += "a plain general-book key must be applied with the page's own document"
        return out
    }

    @Test fun theKeyApplyRuleAtItsCommonOwnerKeepsTheEpubDocument() {
        assertTrue("QuickSheets.kt must exist at ${quickSheets.path}", quickSheets.exists())
        assertEquals(emptyList<String>(), applyRuleViolations(quickSheets.readText()))
    }

    @Test fun plantedApplyRuleViolationsAreCaught() {
        val real = quickSheets.readText()
        val noEpub = real.replace("key is BookAndKey", "key is Nothing")
        assertTrue("dropping the BookAndKey arm must be reported", applyRuleViolations(noEpub).isNotEmpty())
        val wrongBook = real.replace("applyChosenGenBookKey(key.document, key)", "applyChosenGenBookKey(null, key)")
        assertTrue("applying an EPUB entry to the wrong book must be reported", applyRuleViolations(wrongBook).isNotEmpty())
        val elseNull = real.replace("} else {\n                actions.applyChosenGenBookKey(slots.keyChooserPage.currentDocument(kind), key)", "} else {\n                actions.applyChosenGenBookKey(null, key)")
        assertNotEquals("the planted else-arm edit must change the source", real, elseNull)
        assertTrue("a plain key applied to no book must be reported", applyRuleViolations(elseNull).isNotEmpty())
        // The pin must not be satisfied by text that sits AFTER the function (the old scan ran to EOF).
        val moved = real.replace("currentDocument(kind), key)\n            }\n        KeyChooserKind.Grid", "null, key)\n            }\n        KeyChooserKind.Grid") +
            "\nprivate fun later() { } // } else { actions.applyChosenGenBookKey(slots.keyChooserPage.currentDocument(kind), key)\n"
        assertTrue("text after the function must not satisfy the pin", applyRuleViolations(moved).isNotEmpty())
        val mapNull = real.replace("currentDocument(kind), key)\n        KeyChooserKind.GeneralBook", "null, key)\n        KeyChooserKind.GeneralBook")
        assertTrue("a map key applied to no book must be reported", applyRuleViolations(mapNull).isNotEmpty())
    }
}
