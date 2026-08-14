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
package net.bible.sharedcore.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GridStep { BOOK, CHAPTER, VERSE }

/** One grid cell. [colorGroup] 0..9 = OT/NT category (see the screen palette), -1 = neutral. */
data class GridButton(
    val id: Int,
    val label: String,
    val longLabel: String? = null,
    val colorGroup: Int = -1,
    val isCurrent: Boolean = false,
    val readProgress: Float = 0f,
    val memProgress: Float = 0f,
)

/** A whole grid step's view-data (host-built). [sections] non-null = grouped Book view. */
data class GridUi(
    val step: GridStep,
    val title: String,
    val columns: Int,
    val showLongNames: Boolean,
    val showProgress: Boolean,
    val showDeutToggle: Boolean,
    val buttons: List<GridButton>,
    val sections: List<List<GridButton>>? = null,
    /**
     * Floor for the row count the screen sizes its cells against — classic's fixed `LayoutDesigner`
     * row count. Without it a short list (Jude: one chapter) computes one row and inflates its one
     * button to the full viewport height.
     */
    val minRows: Int = 1,
)

/**
 * Rows to divide the viewport height by when sizing one grid cell: the content's own row count, but
 * never fewer than the layout designer's [minRows]. See `GridUi.minRows`.
 */
fun gridCellRows(buttonCount: Int, columns: Int, minRows: Int): Int {
    val cols = columns.coerceAtLeast(1)
    val contentRows = ((buttonCount + cols - 1) / cols).coerceAtLeast(1)
    return maxOf(contentRows, minRows)
}

/** Persisted grid preferences (host maps these to the divergent classic stores). */
data class GridOptions(
    val showScripture: Boolean,
    val alphabetical: Boolean,
    val ltr: Boolean,
    val groupByCategory: Boolean,
    val longNames: Boolean,
    val showProgress: Boolean,
)

enum class GridOption { DEUTEROCANONICAL, ALPHABETICAL, LTR, GROUP_BY_CATEGORY, LONG_NAMES, SHOW_PROGRESS }

sealed class BookPick {
    data class Finish(val osisId: String) : BookPick()
    data object GoChapter : BookPick()
    data object GoVerse : BookPick()
}

sealed class ChapterPick {
    data class Finish(val osisId: String) : ChapterPick()
    data object GoVerse : ChapterPick()
}

/**
 * Framework-free controller driving the Book→Chapter→Verse grid flow inside a single host.
 * A hand-rolled step back-stack replaces the classic three-Activity chain (no nav library —
 * Batch Z). [buildStep] produces the whole [GridUi] for a step under the current options (all
 * JSword/versification/progress/color/column logic is host-side). The pick seams reproduce the
 * classic branch logic; [onFinish] sets the result + finishes.
 */
class GridChoosePassageController(
    initialOptions: GridOptions,
    private val buildStep: (GridStep, GridOptions) -> GridUi,
    private val onPersistOptions: (GridOptions) -> Unit,
    private val onPickBook: (Int) -> BookPick,
    private val onPickChapter: (Int) -> ChapterPick,
    private val onPickVerse: (Int) -> String,
    private val onFinish: (String) -> Unit,
) {
    private val backStack = ArrayDeque<GridStep>()

    private val _step = MutableStateFlow(GridStep.BOOK)
    val step: StateFlow<GridStep> = _step.asStateFlow()

    private val _options = MutableStateFlow(initialOptions)
    val options: StateFlow<GridOptions> = _options.asStateFlow()

    private val _ui = MutableStateFlow(buildStep(GridStep.BOOK, initialOptions))
    val ui: StateFlow<GridUi> = _ui.asStateFlow()

    fun pick(id: Int) {
        when (_step.value) {
            GridStep.BOOK -> when (val p = onPickBook(id)) {
                is BookPick.Finish -> onFinish(p.osisId)
                BookPick.GoChapter -> goTo(GridStep.CHAPTER)
                BookPick.GoVerse -> goTo(GridStep.VERSE)
            }
            GridStep.CHAPTER -> when (val p = onPickChapter(id)) {
                is ChapterPick.Finish -> onFinish(p.osisId)
                ChapterPick.GoVerse -> goTo(GridStep.VERSE)
            }
            GridStep.VERSE -> onFinish(onPickVerse(id))
        }
    }

    private fun goTo(next: GridStep) {
        backStack.addLast(_step.value)
        _step.value = next
        _ui.value = buildStep(next, _options.value)
    }

    /** Pops one step. Returns false when already at the root (host then finishes). */
    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        _step.value = prev
        _ui.value = buildStep(prev, _options.value)
        return true
    }

    fun toggle(option: GridOption) {
        val o = _options.value
        val n = when (option) {
            GridOption.DEUTEROCANONICAL -> o.copy(showScripture = !o.showScripture)
            GridOption.ALPHABETICAL -> o.copy(alphabetical = !o.alphabetical, groupByCategory = false)
            GridOption.LTR -> o.copy(ltr = !o.ltr, groupByCategory = false)
            GridOption.GROUP_BY_CATEGORY -> o.copy(groupByCategory = !o.groupByCategory, alphabetical = false, ltr = true)
            GridOption.LONG_NAMES -> o.copy(longNames = !o.longNames)
            GridOption.SHOW_PROGRESS -> o.copy(showProgress = !o.showProgress)
        }
        _options.value = n
        onPersistOptions(n)
        _ui.value = buildStep(_step.value, n)
    }
}
