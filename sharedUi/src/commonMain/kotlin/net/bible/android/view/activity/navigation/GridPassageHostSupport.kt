/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.android.view.activity.navigation

import net.bible.android.control.navigation.BibleBookSortOrder
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.progress.ProgressControl
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedcore.platform.AppSettings
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import kotlin.math.ceil

/**
 * The Book→Chapter→Verse grid's host-side logic, extracted from [GridChoosePassageComposeActivity]
 * in round 13a so that BOTH the activity and the Speak sheet's verse-picker page
 * (`SpeakSettingsSlot`'s verse-picker page) drive the identical
 * [net.bible.sharedcore.navigation.GridChoosePassageController] seams. One implementation, two
 * hosts: every function here was that activity's own `private fun`, with each field it read
 * (`navigationControl`, `windowControl`, `selectedBookNo`, `selectedChapter`, `navigateToVerse`)
 * turned into a parameter.
 */

/** The activity's `initialOptions` block — the persisted grid preferences, verbatim. */
@kotlin.native.HiddenFromObjC
fun initialGridOptions(navigationControl: NavigationControl, isScripture: Boolean, settings: AppSettings) = GridOptions(
    showScripture = isScripture,
    alphabetical = navigationControl.bibleBookSortOrder == BibleBookSortOrder.ALPHABETICAL,
    ltr = settings.getBoolean("book_grid_ltr", false),
    groupByCategory = settings.getBoolean("book_grid_group_by_category", false),
    longNames = settings.getBoolean("book_grid_show_long_name", false),
    showProgress = settings.getBoolean("book_grid_show_progress", true),
)

// ---- buildStep: ports bibleBookButtonInfo / getBibleChaptersButtonInfo / getBibleVersesButtonInfo ----

@kotlin.native.HiddenFromObjC
suspend fun buildGridStep(
    step: GridStep,
    opts: GridOptions,
    baseTitle: String,
    workspaceName: String,
    selectedBookNo: Int,
    selectedChapter: Int,
    navigationControl: NavigationControl,
    windowControl: WindowControl,
    isPortrait: Boolean,
): GridUi = when (step) {
    GridStep.BOOK -> buildBookStep(opts, baseTitle, workspaceName, navigationControl, windowControl, isPortrait)
    GridStep.CHAPTER -> buildChapterStep(opts, selectedBookNo, navigationControl, windowControl, isPortrait)
    GridStep.VERSE -> buildVerseStep(opts, selectedBookNo, selectedChapter, navigationControl, windowControl, isPortrait)
}

private suspend fun buildBookStep(
    opts: GridOptions,
    baseTitle: String,
    workspaceName: String,
    navigationControl: NavigationControl,
    windowControl: WindowControl,
    isPortrait: Boolean,
): GridUi {
    val v11n = navigationControl.versification
    val books = navigationControl.getBibleBooks(opts.showScripture)
    val currentBook = KeyUtil.getVerse(windowControl.activeWindowPageManager.currentBible.key).book
    val shortNamesAvail = v11n.getShortName(BibleBook.GEN) != v11n.getLongName(BibleBook.GEN)
    val buttons = books.map { book ->
        GridButton(
            id = book.ordinal,
            label = shortBookName(v11n, book, shortNamesAvail),
            longLabel = v11n.getLongName(book),
            colorGroup = categoryIndex(book.ordinal),
            isCurrent = book == currentBook,
            readProgress = if (opts.showProgress) ProgressControl.getReadingProgress(v11n, book) else 0f,
            memProgress = if (opts.showProgress) ProgressControl.getMemorizationProgress(v11n, book) else 0f,
        )
    }
    val layout = layoutGrid(buttons.size, isPortrait, isBookGrid = true)
    val columns = layout.columns
    val sections = if (opts.groupByCategory) buttons.groupBy { coarseGroup(it.id) }.values.toList() else null
    val ordered = if (sections == null && isPortrait && !opts.ltr) columnMajor(buttons, columns) else buttons
    val showDeut = navigationControl.getBibleBooks(false).isNotEmpty()
    return assembleGridUi(GridStep.BOOK, "$baseTitle ($workspaceName)", layout,
        showLongNames = opts.longNames, showProgress = opts.showProgress, showDeutToggle = showDeut,
        buttons = ordered, sections = sections)
}

private suspend fun buildChapterStep(
    opts: GridOptions,
    selectedBookNo: Int,
    navigationControl: NavigationControl,
    windowControl: WindowControl,
    isPortrait: Boolean,
): GridUi {
    val v11n = navigationControl.versification
    val book = BibleBook.values()[selectedBookNo]
    val chapters = try { v11n.getLastChapter(book) } catch (e: Exception) { 0 }
    val cur = windowControl.activeWindowPageManager.currentVersePage.singleKey as Verse
    val group = categoryIndex(book.ordinal)
    val buttons = (1..chapters).map { ch ->
        GridButton(
            id = ch, label = ch.toString(), colorGroup = group,
            isCurrent = cur.book == book && ch == cur.chapter,
            readProgress = if (opts.showProgress && ProgressControl.isChapterRead(v11n, book, ch)) 1f else 0f,
            memProgress = if (opts.showProgress) ProgressControl.getMemorizationProgress(v11n, book, ch) else 0f,
        )
    }
    val layout = layoutGrid(buttons.size, isPortrait, isBookGrid = false)
    val columns = layout.columns
    val ordered = if (isPortrait && !opts.ltr) columnMajor(buttons, columns) else buttons
    return assembleGridUi(GridStep.CHAPTER, v11n.getLongName(book), layout,
        showLongNames = false, showProgress = opts.showProgress, showDeutToggle = false, buttons = ordered)
}

private suspend fun buildVerseStep(
    opts: GridOptions,
    selectedBookNo: Int,
    selectedChapter: Int,
    navigationControl: NavigationControl,
    windowControl: WindowControl,
    isPortrait: Boolean,
): GridUi {
    val v11n = navigationControl.versification
    val book = BibleBook.values()[selectedBookNo]
    val verses = try { v11n.getLastVerse(book, selectedChapter) } catch (e: Exception) { 0 }
    val cur = windowControl.activeWindowPageManager.currentVersePage.singleKey as Verse
    val group = categoryIndex(book.ordinal)
    val chapterRead = opts.showProgress && ProgressControl.isChapterRead(v11n, book, selectedChapter)
    val buttons = (1..verses).map { vs ->
        GridButton(
            id = vs, label = vs.toString(), colorGroup = group,
            isCurrent = vs == cur.verse && selectedChapter == cur.chapter && book == cur.book,
            readProgress = if (chapterRead) 1f else 0f,
            memProgress = if (opts.showProgress && ProgressControl.isVerseMemorized(v11n, book, selectedChapter, vs)) 1f else 0f,
        )
    }
    val layout = layoutGrid(buttons.size, isPortrait, isBookGrid = false)
    val columns = layout.columns
    val ordered = if (isPortrait && !opts.ltr) columnMajor(buttons, columns) else buttons
    return assembleGridUi(GridStep.VERSE, "${v11n.getLongName(book)} $selectedChapter", layout,
        showLongNames = false, showProgress = opts.showProgress, showDeutToggle = false, buttons = ordered)
}

// ---- pick branches (port bookSelected / chapter buttonPressed) ----

/**
 * [onSelectedChapter] replaces the activity's own `selectedChapter = 1` write — the one field this
 * branch mutates that the caller must see (the caller already owns `selectedBookNo`).
 */
@kotlin.native.HiddenFromObjC
fun pickGridBook(
    bookNo: Int,
    navigateToVerse: Boolean,
    navigationControl: NavigationControl,
    onSelectedChapter: (Int) -> Unit,
): BookPick {
    val v11n = navigationControl.versification
    val book = BibleBook.values()[bookNo]
    return if (!navigationControl.hasChapters(book)) {
        if (!navigateToVerse) BookPick.Finish(Verse(v11n, book, 1, 1).getOsisID())
        else { onSelectedChapter(1); BookPick.GoVerse }
    } else BookPick.GoChapter
}

@kotlin.native.HiddenFromObjC
fun pickGridChapter(
    chapter: Int,
    selectedBookNo: Int,
    navigateToVerse: Boolean,
    navigationControl: NavigationControl,
    windowControl: WindowControl,
): ChapterPick {
    val v11n = navigationControl.versification
    val book = BibleBook.values()[selectedBookNo]
    val notSingleKey = !windowControl.activeWindowPageManager.currentPage.isSingleKey
    return if (!navigateToVerse && notSingleKey) ChapterPick.Finish(Verse(v11n, book, chapter, 1).getOsisID())
    else ChapterPick.GoVerse
}

// ---- options persistence (mirror classic saveOptions + inline show_progress + sort order) ----

@kotlin.native.HiddenFromObjC
fun persistGridOptions(o: GridOptions, navigationControl: NavigationControl, settings: AppSettings) {
    settings.setBoolean("book_grid_ltr", o.ltr)
    settings.setBoolean("book_grid_group_by_category", o.groupByCategory)
    settings.setBoolean("book_grid_show_long_name", o.longNames)
    settings.setBoolean("book_grid_show_progress", o.showProgress)
    navigationControl.bibleBookSortOrder = if (o.alphabetical) BibleBookSortOrder.ALPHABETICAL else BibleBookSortOrder.BIBLE_BOOK
    // showScripture is in-memory only (classic isCurrentlyShowingScripture) — not persisted.
}

// ---- name / color / layout helpers (ports of the classic private helpers) ----

private fun shortBookName(v11n: Versification, book: BibleBook, shortAvailable: Boolean): String {
    if (shortAvailable) return v11n.getShortName(book)
    val long = v11n.getLongName(book)
    val sb = StringBuilder(4)
    var i = 0
    while (sb.length < 4 && i < long.length) { val ch = long[i]; if (ch != ' ' && ch != '.') sb.append(ch); i++ }
    return sb.toString()
}

/** Port of getBookColorAndGroup → a 0..9 category index (-1 = other/deuterocanonical). */
private fun categoryIndex(ordinal: Int): Int = when {
    ordinal <= BibleBook.DEUT.ordinal -> 0
    ordinal <= BibleBook.ESTH.ordinal -> 1
    ordinal <= BibleBook.SONG.ordinal -> 2
    ordinal <= BibleBook.DAN.ordinal -> 3
    ordinal <= BibleBook.MAL.ordinal -> 4
    ordinal <= BibleBook.JOHN.ordinal -> 5
    ordinal <= BibleBook.ACTS.ordinal -> 6
    ordinal <= BibleBook.PHLM.ordinal -> 7
    ordinal <= BibleBook.JUDE.ordinal -> 8
    ordinal <= BibleBook.REV.ordinal -> 9
    else -> -1
}

/** Coarse GroupB grouping (merges Gospel+Acts and General+Revelation) for group-by-category. */
private fun coarseGroup(ordinal: Int): Int = when (categoryIndex(ordinal)) {
    6 -> 5          // Acts → Gospel+Acts
    9 -> 8          // Revelation → General+Revelation
    else -> categoryIndex(ordinal)
}

/** Reorder a flat list so a row-major LazyVerticalGrid renders it column-major (portrait, LTR off). */
private fun columnMajor(items: List<GridButton>, columns: Int): List<GridButton> {
    if (columns <= 1 || items.isEmpty()) return items
    val rows = ceil(items.size.toDouble() / columns).toInt()
    val out = ArrayList<GridButton>(items.size)
    for (r in 0 until rows) for (c in 0 until columns) {
        val idx = c * rows + r
        if (idx < items.size) out.add(items[idx])
    }
    return out
}

/** Column AND row count reproducing LayoutDesigner's intent (66-book special case + MIN_COLS). */
@kotlin.native.HiddenFromObjC
data class GridLayout(val columns: Int, val rows: Int)

/**
 * Classic sizes a grid cell as `height / rows` for a FIXED row count and pads the shortfall
 * with invisible spacers (`ButtonGrid.addButtons`), which is why a one-chapter book gets one
 * normal-sized button rather than a screen-filling one. The row count is therefore part of
 * the layout, not a throwaway intermediate — see LayoutDesigner.kt:84's comment about
 * "a couple of large buttons on the screen".
 */
@kotlin.native.HiddenFromObjC
fun layoutGrid(count: Int, isPortrait: Boolean, isBookGrid: Boolean): GridLayout {
    if (isBookGrid && count == 66) {
        return if (isPortrait) GridLayout(columns = 6, rows = 11) else GridLayout(columns = 11, rows = 6)
    }
    val rows = when {
        count <= 50 -> if (isPortrait) 10 else 5
        count <= 100 -> 10
        else -> if (isPortrait) 15 else 10
    }
    val cols = ceil(count.toDouble() / rows).toInt()
    val minCols = if (isPortrait) 5 else 8
    return GridLayout(columns = maxOf(minCols, cols), rows = rows)
}

/**
 * Assembles the final [GridUi] for a step, wiring [layout]'s row count into [GridUi.minRows]
 * (and its column count into [GridUi.columns]). The three `build*Step` functions differ only in
 * step/title/flags/buttons — they all funnel through here for the actual `GridUi`
 * construction, so this one function is what a dropped `minRows` wiring would actually break,
 * and it can be tested without an Activity instance (see `GridLayoutRowsTest`).
 */
@kotlin.native.HiddenFromObjC
fun assembleGridUi(
    step: GridStep,
    title: String,
    layout: GridLayout,
    showLongNames: Boolean,
    showProgress: Boolean,
    showDeutToggle: Boolean,
    buttons: List<GridButton>,
    sections: List<List<GridButton>>? = null,
): GridUi = GridUi(
    step, title, layout.columns,
    showLongNames = showLongNames, showProgress = showProgress, showDeutToggle = showDeutToggle,
    buttons = buttons, sections = sections, minRows = layout.rows,
)

/**
 * Builds the [GridChoosePassageController]s of the sheet hosts (the Speak sheet's verse picker and
 * the reading view's key-chooser grid) from the seams above: the seven lambdas
 * `GridChoosePassageComposeActivity` builds, assembled once. The two step fields classic held as
 * Activity state (`selectedBookNo`, `selectedChapter`) are locals of each [create] call, so they
 * belong to the entry that asked for the controller. [workspaceName] and [isPortrait] are read
 * per use, not at construction.
 */
@kotlin.native.HiddenFromObjC
class GridPassageControllerFactory(
    private val navigationControl: NavigationControl,
    private val windowControl: WindowControl,
    private val settings: AppSettings,
    private val workspaceName: () -> String,
    private val isPortrait: () -> Boolean,
) {
    /** The versification of the current passage document, for turning a picked OSIS id into a verse. */
    val versification: Versification get() = navigationControl.versification

    fun create(
        scope: CoroutineScope,
        baseTitle: String,
        isScripture: Boolean,
        navigateToVerse: Boolean,
        onFinish: (String) -> Unit,
    ): GridChoosePassageController {
        var selectedBookNo = 0
        var selectedChapter = 1
        val v11n = navigationControl.versification
        val workspaceName = workspaceName()
        return GridChoosePassageController(
            scope = scope,
            initialOptions = initialGridOptions(navigationControl, isScripture, settings),
            buildStep = { step, opts ->
                buildGridStep(step, opts, baseTitle, workspaceName, selectedBookNo, selectedChapter,
                    navigationControl, windowControl, isPortrait())
            },
            onPersistOptions = { persistGridOptions(it, navigationControl, settings) },
            onPickBook = { bookNo ->
                selectedBookNo = bookNo
                pickGridBook(bookNo, navigateToVerse, navigationControl) { selectedChapter = it }
            },
            onPickChapter = { chapter ->
                selectedChapter = chapter
                pickGridChapter(chapter, selectedBookNo, navigateToVerse, navigationControl, windowControl)
            },
            onPickVerse = { verse ->
                Verse(v11n, BibleBook.values()[selectedBookNo], selectedChapter, verse).getOsisID()
            },
            onFinish = onFinish,
        )
    }
}
