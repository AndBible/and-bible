package net.bible.sharedcore.bibleview

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class BridgeArgumentException(message: String) : IllegalArgumentException(message)

/**
 * Typed access to one call's JSON arguments. Coerces like Chromium's Java bridge did for the old
 * `@JavascriptInterface` methods (L2a spec §4.2, R4): numbers truncate toward zero, `null` is `0`
 * for numbers and `false` for booleans; everything else that does not fit throws
 * [BridgeArgumentException].
 */
class BridgeArgs internal constructor(private val method: String, private val args: List<JsonElement>) {
    fun str(i: Int): String = primitive(i)?.let { stringOf(i, it) } ?: fail(i, "null for a non-null String")
    fun strOrNull(i: Int): String? = primitive(i)?.let { stringOf(i, it) }
    fun int(i: Int): Int = number(i).toInt()
    fun long(i: Int): Long = number(i)

    fun bool(i: Int): Boolean {
        val p = primitive(i) ?: return false
        if (p.isString) fail(i, "expected a boolean")
        return p.booleanOrNull ?: fail(i, "expected a boolean")
    }

    private fun stringOf(i: Int, p: JsonPrimitive): String = if (p.isString) p.content else fail(i, "expected a string")

    private fun number(i: Int): Long {
        val p = primitive(i) ?: return 0
        if (p.isString) fail(i, "expected a number")
        p.longOrNull?.let { return it }
        val d = p.doubleOrNull ?: fail(i, "expected a number")
        return if (d.isNaN()) 0 else d.toLong() // toLong truncates toward zero
    }

    private fun primitive(i: Int): JsonPrimitive? = when (val e = args[i]) {
        is JsonNull -> null
        is JsonPrimitive -> e
        else -> fail(i, "expected a primitive")
    }

    private fun fail(i: Int, reason: String): Nothing = throw BridgeArgumentException("$method arg $i: $reason")
}

/**
 * Method name + JSON arguments → typed [BibleBridge] call. Hand-written; `kinds` has one letter per
 * parameter (S String, s String?, I Int, L Long, B Boolean) so tests can generate samples.
 * Adding a bridge method: TS type in `android.ts`, a [BibleBridge] function, an entry here, the
 * Android body in `BibleJavascriptInterface` (see `docs/bibleview-bridge.md`).
 */
object BibleBridgeDecoder {
    class Entry(val kinds: String, val invoke: (BridgeArgs, BibleBridge) -> Unit)

    val deferredMethods: Set<String> = setOf(
        "parseRef", "refChooserDialog", "requestMoreToEnd", "requestMoreToBeginning", "getMyDocumentPageRawContent",
    )

    val entries: Map<String, Entry> = mapOf(
        "scrolledToOrdinal" to Entry("SI") { a, b -> b.scrolledToOrdinal(a.str(0), a.int(1)) },
        "setClientReady" to Entry("") { _, b -> b.setClientReady() },
        "setLimitAmbiguousModalSize" to Entry("B") { a, b -> b.setLimitAmbiguousModalSize(a.bool(0)) },
        "requestMoreToBeginning" to Entry("L") { a, b -> b.requestMoreToBeginning(a.long(0)) },
        "requestMoreToEnd" to Entry("L") { a, b -> b.requestMoreToEnd(a.long(0)) },
        "goToNextChapter" to Entry("") { _, b -> b.goToNextChapter() },
        "goToPreviousChapter" to Entry("") { _, b -> b.goToPreviousChapter() },
        "parseRef" to Entry("LS") { a, b -> b.parseRef(a.long(0), a.str(1)) },
        "refChooserDialog" to Entry("L") { a, b -> b.refChooserDialog(a.long(0)) },
        "saveBookmarkNote" to Entry("Ss") { a, b -> b.saveBookmarkNote(a.str(0), a.strOrNull(1)) },
        "saveGenericBookmarkNote" to Entry("Ss") { a, b -> b.saveGenericBookmarkNote(a.str(0), a.strOrNull(1)) },
        "removeBookmark" to Entry("S") { a, b -> b.removeBookmark(a.str(0)) },
        "removeGenericBookmark" to Entry("S") { a, b -> b.removeGenericBookmark(a.str(0)) },
        "assignLabels" to Entry("S") { a, b -> b.assignLabels(a.str(0)) },
        "genericAssignLabels" to Entry("S") { a, b -> b.genericAssignLabels(a.str(0)) },
        "setBookmarkEditAction" to Entry("SS") { a, b -> b.setBookmarkEditAction(a.str(0), a.str(1)) },
        "console" to Entry("SS") { a, b -> b.console(a.str(0), a.str(1)) },
        "selectionCleared" to Entry("") { _, b -> b.selectionCleared() },
        "reportInputFocus" to Entry("B") { a, b -> b.reportInputFocus(a.bool(0)) },
        "openEpubLink" to Entry("SSS") { a, b -> b.openEpubLink(a.str(0), a.str(1), a.str(2)) },
        "openExternalLink" to Entry("S") { a, b -> b.openExternalLink(a.str(0)) },
        "openDownloads" to Entry("") { _, b -> b.openDownloads() },
        "setEditing" to Entry("B") { a, b -> b.setEditing(a.bool(0)) },
        "createNewStudyPadEntry" to Entry("SSS") { a, b -> b.createNewStudyPadEntry(a.str(0), a.str(1), a.str(2)) },
        "deleteStudyPadEntry" to Entry("S") { a, b -> b.deleteStudyPadEntry(a.str(0)) },
        "removeBookmarkLabel" to Entry("SS") { a, b -> b.removeBookmarkLabel(a.str(0), a.str(1)) },
        "removeGenericBookmarkLabel" to Entry("SS") { a, b -> b.removeGenericBookmarkLabel(a.str(0), a.str(1)) },
        "updateOrderNumber" to Entry("SS") { a, b -> b.updateOrderNumber(a.str(0), a.str(1)) },
        "setStudyPadCursor" to Entry("SI") { a, b -> b.setStudyPadCursor(a.str(0), a.int(1)) },
        "toast" to Entry("S") { a, b -> b.toast(a.str(0)) },
        "updateStudyPadTextEntry" to Entry("S") { a, b -> b.updateStudyPadTextEntry(a.str(0)) },
        "updateStudyPadTextEntryText" to Entry("SS") { a, b -> b.updateStudyPadTextEntryText(a.str(0), a.str(1)) },
        "updateBookmarkToLabel" to Entry("S") { a, b -> b.updateBookmarkToLabel(a.str(0)) },
        "updateGenericBookmarkToLabel" to Entry("S") { a, b -> b.updateGenericBookmarkToLabel(a.str(0)) },
        "shareBookmarkVerse" to Entry("S") { a, b -> b.shareBookmarkVerse(a.str(0)) },
        "shareVerse" to Entry("SII") { a, b -> b.shareVerse(a.str(0), a.int(1), a.int(2)) },
        "copyVerse" to Entry("SII") { a, b -> b.copyVerse(a.str(0), a.int(1), a.int(2)) },
        "addBookmark" to Entry("SIIB") { a, b -> b.addBookmark(a.str(0), a.int(1), a.int(2), a.bool(3)) },
        "addGenericBookmark" to Entry("SSIIB") { a, b -> b.addGenericBookmark(a.str(0), a.str(1), a.int(2), a.int(3), a.bool(4)) },
        "addParagraphBreakBookmark" to Entry("SII") { a, b -> b.addParagraphBreakBookmark(a.str(0), a.int(1), a.int(2)) },
        "addGenericParagraphBreakBookmark" to Entry("SSII") { a, b -> b.addGenericParagraphBreakBookmark(a.str(0), a.str(1), a.int(2), a.int(3)) },
        "createWholePageBookmark" to Entry("SS") { a, b -> b.createWholePageBookmark(a.str(0), a.str(1)) },
        "compare" to Entry("SII") { a, b -> b.compare(a.str(0), a.int(1), a.int(2)) },
        "memorize" to Entry("SII") { a, b -> b.memorize(a.str(0), a.int(1), a.int(2)) },
        "markAsMemorized" to Entry("SII") { a, b -> b.markAsMemorized(a.str(0), a.int(1), a.int(2)) },
        "addMemorizationTarget" to Entry("SII") { a, b -> b.addMemorizationTarget(a.str(0), a.int(1), a.int(2)) },
        "unmarkMemorized" to Entry("SII") { a, b -> b.unmarkMemorized(a.str(0), a.int(1), a.int(2)) },
        "removeMemorizationTarget" to Entry("SII") { a, b -> b.removeMemorizationTarget(a.str(0), a.int(1), a.int(2)) },
        "setReadingProgressSettings" to Entry("S") { a, b -> b.setReadingProgressSettings(a.str(0)) },
        "openReadingProgress" to Entry("I") { a, b -> b.openReadingProgress(a.int(0)) },
        "openReadingProgressSettings" to Entry("") { _, b -> b.openReadingProgressSettings() },
        "showHelpDialog" to Entry("S") { a, b -> b.showHelpDialog(a.str(0)) },
        "recordChapterRead" to Entry("SIIS") { a, b -> b.recordChapterRead(a.str(0), a.int(1), a.int(2), a.str(3)) },
        "openChapterReadHistory" to Entry("SII") { a, b -> b.openChapterReadHistory(a.str(0), a.int(1), a.int(2)) },
        "saveState" to Entry("S") { a, b -> b.saveState(a.str(0)) },
        "openStudyPad" to Entry("SS") { a, b -> b.openStudyPad(a.str(0), a.str(1)) },
        "openMyNotes" to Entry("SI") { a, b -> b.openMyNotes(a.str(0), a.int(1)) },
        "openAiDocPage" to Entry("SS") { a, b -> b.openAiDocPage(a.str(0), a.str(1)) },
        "speak" to Entry("SSII") { a, b -> b.speak(a.str(0), a.str(1), a.int(2), a.int(3)) },
        "speakMemorizationLoop" to Entry("SSII") { a, b -> b.speakMemorizationLoop(a.str(0), a.str(1), a.int(2), a.int(3)) },
        "speakGeneric" to Entry("SSII") { a, b -> b.speakGeneric(a.str(0), a.str(1), a.int(2), a.int(3)) },
        "setAsPrimaryLabel" to Entry("SS") { a, b -> b.setAsPrimaryLabel(a.str(0), a.str(1)) },
        "setAsPrimaryLabelGeneric" to Entry("SS") { a, b -> b.setAsPrimaryLabelGeneric(a.str(0), a.str(1)) },
        "toggleBookmarkLabel" to Entry("SS") { a, b -> b.toggleBookmarkLabel(a.str(0), a.str(1)) },
        "toggleGenericBookmarkLabel" to Entry("SS") { a, b -> b.toggleGenericBookmarkLabel(a.str(0), a.str(1)) },
        "setBookmarkCustomIcon" to Entry("Ss") { a, b -> b.setBookmarkCustomIcon(a.str(0), a.strOrNull(1)) },
        "setGenericBookmarkCustomIcon" to Entry("Ss") { a, b -> b.setGenericBookmarkCustomIcon(a.str(0), a.strOrNull(1)) },
        "reportModalState" to Entry("B") { a, b -> b.reportModalState(a.bool(0)) },
        "setBookmarkWholeVerse" to Entry("SB") { a, b -> b.setBookmarkWholeVerse(a.str(0), a.bool(1)) },
        "setGenericBookmarkWholeVerse" to Entry("SB") { a, b -> b.setGenericBookmarkWholeVerse(a.str(0), a.bool(1)) },
        "toggleCompareDocument" to Entry("S") { a, b -> b.toggleCompareDocument(a.str(0)) },
        "helpDialog" to Entry("Ss") { a, b -> b.helpDialog(a.str(0), a.strOrNull(1)) },
        "helpBookmarks" to Entry("") { _, b -> b.helpBookmarks() },
        "shareHtml" to Entry("S") { a, b -> b.shareHtml(a.str(0)) },
        "onKeyDown" to Entry("S") { a, b -> b.onKeyDown(a.str(0)) },
        "crash" to Entry("") { _, b -> b.crash() },
        "llmAction" to Entry("SIIS") { a, b -> b.llmAction(a.str(0), a.int(1), a.int(2), a.str(3)) },
        "llmActionGeneric" to Entry("SSIIS") { a, b -> b.llmActionGeneric(a.str(0), a.str(1), a.int(2), a.int(3), a.str(4)) },
        "noteEditorLlmAction" to Entry("S") { a, b -> b.noteEditorLlmAction(a.str(0)) },
        "getMyDocumentPageRawContent" to Entry("LSS") { a, b -> b.getMyDocumentPageRawContent(a.long(0), a.str(1), a.str(2)) },
        "shareMyDocumentContent" to Entry("SS") { a, b -> b.shareMyDocumentContent(a.str(0), a.str(1)) },
        "copyMyDocumentContent" to Entry("SS") { a, b -> b.copyMyDocumentContent(a.str(0), a.str(1)) },
        "saveMyDocumentPageContent" to Entry("SSSs") { a, b -> b.saveMyDocumentPageContent(a.str(0), a.str(1), a.str(2), a.strOrNull(3)) },
        "reloadMyDocumentPage" to Entry("S") { a, b -> b.reloadMyDocumentPage(a.str(0)) },
        "regenerateMyDocumentPage" to Entry("S") { a, b -> b.regenerateMyDocumentPage(a.str(0)) },
        "deleteMyDocumentPage" to Entry("S") { a, b -> b.deleteMyDocumentPage(a.str(0)) },
        "openPromptEditor" to Entry("S") { a, b -> b.openPromptEditor(a.str(0)) },
    )

    /**
     * Decodes [args] and calls [method] on [bridge]. Returns false for an unknown method.
     * @throws BridgeArgumentException on a wrong arity or an argument that does not fit.
     */
    fun decode(method: String, args: List<JsonElement>, bridge: BibleBridge): Boolean {
        val entry = entries[method] ?: return false
        if (args.size != entry.kinds.length) {
            throw BridgeArgumentException("$method: expected ${entry.kinds.length} arguments, got ${args.size}")
        }
        entry.invoke(BridgeArgs(method, args), bridge)
        return true
    }

    /** The `callId` of a deferred call, or null when the first argument is absent or not a number. */
    fun callIdOf(args: List<JsonElement>): Long? {
        val p = args.firstOrNull() as? JsonPrimitive ?: return null
        if (p is JsonNull || p.isString || p.booleanOrNull != null) return null
        p.longOrNull?.let { return it }
        val number = p.doubleOrNull ?: return null
        return number.toLong() // Match BridgeArgs.long() truncation and saturation.
    }
}
