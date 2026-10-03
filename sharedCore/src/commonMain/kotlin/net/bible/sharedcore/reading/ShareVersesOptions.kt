/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedcore.reading

/**
 * One verse's raw canonical text, as JSword returns it (`SwordContentFacade.getCanonicalText(...)
 * .trimEnd()`, nothing else applied). [verseNumber] is the verse's own number within its
 * versification (0 for an introduction/pseudo-verse, which [ShareVersesOptions.buildText] never
 * numbers, exactly as the classic code never did).
 */
data class ShareVersesEntry(
    val verseNumber: Int,
    val rawText: String,
)

/**
 * Everything [ShareVersesOptions.buildText] needs to build shareable text for one selection,
 * pre-resolved from JSword/Android so the builder itself can be pure Kotlin/commonMain (Task 26 —
 * the share sheet). This is [net.bible.service.sword.SwordContentFacade.getSelectionText]'s JSword
 * half, ported to a data holder; that function itself is untouched, since it still has one other
 * caller ([net.bible.android.view.activity.page.Selection.copyToClipboard]).
 *
 * [referenceAbbreviated]/[referenceFull] are BOTH resolved eagerly, one per `abbreviateReference`
 * toggle state, because the sheet must redraw its preview as the user flips a switch without
 * re-running any JSword lookup — the classic code only ever computed the one branch a fixed dialog
 * needed. Same reasoning for [notesText] (independent of the `showNotes` toggle) and
 * [versionAbbreviation] (independent of `showVersion`).
 */
data class ShareVersesInput(
    /** The verse range's text, in reading order. Empty when the selection has no book
     *  (`Selection.swordBook == null` in the classic code's `?: return ""` guard) — which is why
     *  [ShareVersesOptions.buildText] returns `""` for an empty list. */
    val verses: List<ShareVersesEntry>,
    val startOffset: Int?,
    val endOffset: Int?,
    val referenceAbbreviated: String,
    val referenceFull: String,
    /** `""` when the selection has no book to name a version for; gated by `showVersion` in
     *  [ShareVersesOptions.buildText], not here. */
    val versionAbbreviation: String,
    /** Notes already resolved to plain text (classic: `htmlToSpan(notesOrig).toString()`), or
     *  `null` when the selection carries no notes at all — independent of the `showNotes` toggle,
     *  which is applied in [ShareVersesOptions.buildText]. */
    val notesText: String?,
    /** The localised "advertise the app" line, WITHOUT the leading blank line — e.g. "Shared from
     *  AndBible Bible Study (https://andbible.github.io)". [ShareVersesOptions.buildText] adds the
     *  `"\n\n"` prefix itself when `advertiseApp` is on. Empty (discrete mode, batch 6 A6) means no
     *  advert at all, whatever `advertiseApp` says. */
    val advertiseText: String,
    /** Mirrors `Selection.hasRange` — whether "Show selected text only" / "Show ellipsis" apply to
     *  this selection at all (classic `ShareWidget`'s GONE-when-false visibility for those two
     *  rows). Not read by [ShareVersesOptions.buildText] itself; it is a UI-visibility hint. */
    val hasRange: Boolean,
)

/**
 * The ten user-facing toggles that decide how [buildText] formats a [ShareVersesInput] into
 * shareable text, and the pure string-assembly logic itself — ported line-for-line from
 * `SwordContentFacade.getSelectionText` (see that function's kdoc for the per-option semantics
 * this mirrors) so it can run with no JSword dependency at all, in `:sharedCore` commonMain.
 *
 * Defaults mirror the classic `ShareWidget`'s pref defaults (`CommonUtils.settings.getBoolean(key,
 * default)`), listed alongside each property below.
 */
data class ShareVersesOptions(
    val showVerseNumbers: Boolean = true,         // pref: share_verse_numbers
    val advertiseApp: Boolean = true,             // pref: share_show_add
    val showReference: Boolean = true,            // pref: share_show_reference
    val abbreviateReference: Boolean = true,       // pref: share_abbreviate_reference
    val showVersion: Boolean = true,              // pref: share_show_version
    val showNotes: Boolean = true,                // pref: show_notes
    val showSelectionOnly: Boolean = true,        // pref: show_selection_only
    val showEllipsis: Boolean = true,             // pref: show_ellipsis
    val showReferenceAtFront: Boolean = true,     // pref: share_show_reference_at_front
    val showQuotes: Boolean = false,              // pref: share_show_quotes
    val separateVersesWithNewlines: Boolean = false, // pref: share_separate_verses_newlines
) {
    /** Builds the shareable text for [input] under these options. `""` when [input] carries no
     *  verses (no book selected). */
    fun buildText(input: ShareVersesInput): String {
        if (input.verses.isEmpty()) return ""

        fun normalizeWhitespace(text: String): String =
            if (separateVersesWithNewlines) text.replace(Regex("\\s+"), " ").trim() else text

        // Pairs of (verse number, text) — the normalized working copy every computation below
        // reads from, exactly as the classic code's `verseTexts` list did.
        val verseTexts = input.verses.map { it.verseNumber to normalizeWhitespace(it.rawText) }

        val startOffset = input.startOffset ?: 0
        var startVerse = verseTexts.first().second
        val endOffset = input.endOffset ?: verseTexts.last().second.length

        val start = startVerse.slice(0 until minOf(startOffset, startVerse.length))

        var startVerseNumber = ""
        if (showVerseNumbers && verseTexts.size > 1 && (!showReferenceAtFront || separateVersesWithNewlines)) {
            startVerseNumber = "${verseTexts.first().first}. "
        }
        if (showSelectionOnly && startOffset > 0 && showEllipsis) {
            startVerseNumber = "$startVerseNumber..."
        }

        val versionText = if (showVersion) input.versionAbbreviation else ""
        val quotationStart = if (showQuotes) "“" else ""
        val quotationEnd = if (showQuotes) "”" else ""

        val reference = if (showReference) {
            if (abbreviateReference) input.referenceAbbreviated else input.referenceFull
        } else ""

        val advertise = if (advertiseApp && input.advertiseText.isNotEmpty()) "\n\n${input.advertiseText}" else ""
        val notes = if (showNotes && input.notesText != null) "\n\n${input.notesText}" else ""

        val verseText = if (verseTexts.size == 1) {
            val end = startVerse.slice(endOffset until startVerse.length)
            val text = startVerse.slice(startOffset until minOf(endOffset, startVerse.length))
            val post = if (showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""
            if (!showSelectionOnly) {
                "$quotationStart$startVerseNumber$start$text$end$quotationEnd"
            } else {
                "$quotationStart$startVerseNumber$text$post$quotationEnd"
            }
        } else {
            startVerse = startVerse.slice(startOffset until startVerse.length)
            val (lastVerseNum, lastVerseFullText) = verseTexts.last()
            val endVerseNum = if (showVerseNumbers) "$lastVerseNum. " else ""
            val endVerse = lastVerseFullText.slice(0 until minOf(lastVerseFullText.length, endOffset))
            val end = lastVerseFullText.slice(endOffset until lastVerseFullText.length)

            val text = if (separateVersesWithNewlines) {
                var result = startVerse.trimEnd()
                if (verseTexts.size > 2) {
                    val middleVerses = verseTexts.subList(1, verseTexts.size - 1).map { (num, t) ->
                        if (showVerseNumbers && num != 0) "$num. $t" else t
                    }
                    for (middleVerse in middleVerses) {
                        result += "\n\n$middleVerse"
                    }
                }
                result += "\n\n$endVerseNum$endVerse"
                result.replace(Regex("\n{3,}"), "\n\n")
            } else {
                var middleVerses = if (verseTexts.size > 2) {
                    verseTexts.subList(1, verseTexts.size - 1).joinToString(" ") { (num, t) ->
                        if (showVerseNumbers && num != 0) "$num. $t" else t
                    }
                } else ""
                if (middleVerses.isNotEmpty()) middleVerses += " "
                "${startVerse.trimEnd()} ${middleVerses.trimStart()}$endVerseNum$endVerse"
            }

            val post = if (showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""

            if (!showSelectionOnly) {
                "$quotationStart$startVerseNumber$start$text$end$post$quotationEnd"
            } else {
                "$quotationStart$startVerseNumber$text$post$quotationEnd"
            }
        }

        return if (showReference) {
            if (showReferenceAtFront) {
                val refText = "$reference $versionText".trim()
                val separator = if (separateVersesWithNewlines) "\n\n" else " "
                "$refText$separator$verseText$notes$advertise"
            } else {
                val refText = if (versionText == "") reference else "$reference, $versionText"
                if (separateVersesWithNewlines) {
                    "$verseText\n\n$refText$notes$advertise"
                } else {
                    "$verseText ($refText)$notes$advertise"
                }
            }
        } else {
            "$verseText$notes$advertise"
        }
    }
}
