@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import net.bible.android.control.page.Document
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowView
import net.bible.android.control.versification.wholeChapters
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.DocumentLoader
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

/**
 * The iOS [WindowView]: what `Window.bibleView` points at so that `Window.loadText` finds a view to deliver the
 * document to (Android's `BibleView` implements the same interface and is assigned by `BibleViewFactory`). Without
 * it `waitForBibleView` times out and no document is ever loaded. Forwards to the window's [DocumentLoader] and
 * [BibleWindowSession], like `BibleView` does.
 */
@kotlin.native.HiddenFromObjC
internal class IosWindowView(
    private val session: BibleWindowSession,
    private val loader: DocumentLoader,
) : WindowView {
    override var lastUpdated: Long = 0L
    override var listenEvents: Boolean = true
    override val firstDocument: Document? get() = session.firstDocument

    override val verseRangeLoaded: VerseRange?
        get() {
            val key = session.firstKey as? Verse ?: return null
            return wholeChapters(key.versification, key.book, session.minChapter, session.maxChapter)
        }

    override fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) =
        loader.highlightOrdinalRange(bookInitials, osisRef, range)

    override fun highlightBibleOrdinalRange(range: IntRange) = loader.highlightBibleOrdinalRange(range)

    override fun destroy() {}

    override suspend fun loadDocument(
        document: Document,
        updateLocation: Boolean,
        key: Key?,
        anchorOrdinal: OrdinalRange?,
        htmlId: String?,
        windowContentGeneration: Long?,
    ) = loader.loadDocument(document, updateLocation, key, anchorOrdinal, htmlId, windowContentGeneration)

    override fun scrollOrJumpToVerse(key: Key, forceNow: Boolean) = loader.scrollOrJumpToVerse(key, forceNow)

    override fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean) =
        loader.scrollOrJumpToOrdinal(ordinal, htmlId, bookInitials, osisRef, forceNow)

    override fun hasChapterLoaded(chapter: Int): Boolean = session.hasChapterLoaded(chapter)

    override fun requestFocus(): Boolean = false

    override fun updateTextDisplaySettings(onAttach: Boolean) = loader.updateConfig(onAttach)

    override fun adjustLoadingCount(adj: Int): Boolean = session.emit(BibleViewEvents.adjustLoadingCount(adj))
}
