/*
 * Copyright (c) 2026-2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.page.ChapterDescriptionProvider
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import javax.inject.Inject

/**
 * Optional one-view passage chooser for selecting Book/Chapter/Verse in one screen.
 */
class OneViewChoosePassage : CustomTitlebarActivityBase(R.menu.choose_passage_one_view_menu) {

    private enum class TestamentFilter { OLD, NEW }

    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var windowControl: WindowControl

    private lateinit var testamentToggle: RadioGroup
    private lateinit var selectedReference: TextView
    private lateinit var bookValue: TextView
    private lateinit var chapterValue: TextView
    private lateinit var verseValue: TextView
    private lateinit var bookSeekBar: SeekBar
    private lateinit var bookStepPrev: ImageButton
    private lateinit var bookStepNext: ImageButton
    private lateinit var chapterSeekBar: SeekBar
    private lateinit var chapterStepPrev: ImageButton
    private lateinit var chapterStepNext: ImageButton
    private lateinit var verseSeekBar: SeekBar
    private lateinit var verseStepPrev: ImageButton
    private lateinit var verseStepNext: ImageButton
    private lateinit var selectButton: Button
    private lateinit var chapterDescriptionPanel: LinearLayout
    private lateinit var chapterDescriptionTitle: TextView
    private lateinit var chapterDescriptionBody: TextView
    private lateinit var chapterDescriptionToggleIcon: ImageView

    private var isCurrentlyShowingScripture = false
    private var navigateToVerse = false
    private var isUpdatingUi = false
    private var chapterDescriptionExpanded = false

    private var testamentFilter = TestamentFilter.OLD
    private var bookList: List<BibleBook> = emptyList()
    private var selectedBookIndex = 0
    private var selectedChapter = 1
    private var selectedVerse = 1

    // background goes white in some circumstances if theme changes so prevent theme change
    override val allowThemeChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildActivityComponent().inject(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val customTitle = intent?.extras?.getCharSequence("title")
        title = customTitle ?: getString(R.string.choosePassageOneViewName)
        title = "$title (${SharedActivityState.currentWorkspaceName})"

        setContentView(R.layout.choose_passage_one_view)

        testamentToggle = findViewById(R.id.testament_toggle)
        selectedReference = findViewById(R.id.selected_reference)
        bookValue = findViewById(R.id.book_value)
        chapterValue = findViewById(R.id.chapter_value)
        verseValue = findViewById(R.id.verse_value)
        bookSeekBar = findViewById(R.id.book_seekbar)
        bookStepPrev = findViewById(R.id.book_step_prev)
        bookStepNext = findViewById(R.id.book_step_next)
        chapterSeekBar = findViewById(R.id.chapter_seekbar)
        chapterStepPrev = findViewById(R.id.chapter_step_prev)
        chapterStepNext = findViewById(R.id.chapter_step_next)
        verseSeekBar = findViewById(R.id.verse_seekbar)
        verseStepPrev = findViewById(R.id.verse_step_prev)
        verseStepNext = findViewById(R.id.verse_step_next)
        selectButton = findViewById(R.id.select_button)
        chapterDescriptionPanel = findViewById(R.id.chapterDescriptionPanel)
        chapterDescriptionTitle = findViewById(R.id.chapterDescriptionTitle)
        chapterDescriptionBody = findViewById(R.id.chapterDescriptionBody)
        chapterDescriptionToggleIcon = findViewById(R.id.chapterDescriptionToggleIcon)

        isCurrentlyShowingScripture = intent?.getBooleanExtra("isScripture", false) ?: false
        navigateToVerse = intent?.getBooleanExtra(
            "navigateToVerse",
            CommonUtils.settings.getBoolean("navigate_to_verse_pref", false)
        ) ?: false

        val currentVerse = windowControl.activeWindowPageManager.currentVersePage.singleKey as? Verse
        testamentFilter = if ((currentVerse?.book?.ordinal ?: 0) >= BibleBook.MATT.ordinal) {
            TestamentFilter.NEW
        } else {
            TestamentFilter.OLD
        }
        testamentToggle.check(
            if (testamentFilter == TestamentFilter.OLD) R.id.testament_old else R.id.testament_new
        )

        testamentToggle.setOnCheckedChangeListener { _, checkedId ->
            if (!isCurrentlyShowingScripture) return@setOnCheckedChangeListener
            testamentFilter = if (checkedId == R.id.testament_new) TestamentFilter.NEW else TestamentFilter.OLD
            loadBookGroup(currentBookOrNull(), selectedChapter, selectedVerse)
        }

        selectButton.setOnClickListener { selectCurrentPassage() }
        bookStepPrev.setOnClickListener { stepBook(-1) }
        bookStepNext.setOnClickListener { stepBook(1) }
        chapterStepPrev.setOnClickListener { stepChapter(-1) }
        chapterStepNext.setOnClickListener { stepChapter(1) }
        verseStepPrev.setOnClickListener { stepVerse(-1) }
        verseStepNext.setOnClickListener { stepVerse(1) }
        findViewById<View>(R.id.chapterDescriptionHeader).setOnClickListener {
            chapterDescriptionExpanded = !chapterDescriptionExpanded
            applyDescriptionPanelState()
        }

        bookSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isUpdatingUi || !fromUser || bookList.isEmpty()) return
                selectedBookIndex = progress.coerceIn(0, bookList.lastIndex)
                syncAllControls()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        chapterSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isUpdatingUi || !fromUser) return
                selectedChapter = progress + 1
                syncAllControls()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        verseSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isUpdatingUi || !fromUser) return
                selectedVerse = progress + 1
                verseValue.text = selectedVerse.toString()
                updateReferencePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        loadBookGroup(
            preferredBook = currentVerse?.book,
            preferredChapter = currentVerse?.chapter ?: 1,
            preferredVerse = currentVerse?.verse ?: 1
        )
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val deutToggle = menu.findItem(R.id.deut_toggle)
        deutToggle.setTitle(if (isCurrentlyShowingScripture) R.string.bible else R.string.deuterocanonical)
        deutToggle.isVisible = navigationControl.getBibleBooks(false).isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.deut_toggle -> {
            isCurrentlyShowingScripture = !isCurrentlyShowingScripture
            loadBookGroup(currentBookOrNull(), selectedChapter, selectedVerse)
            invalidateOptionsMenu()
            true
        }
        R.id.default_grid_chooser -> {
            GridChoosePassageBook.setOneViewChooserDefault(false)
            val gridIntent = GridChoosePassageBook.createChooserIntent(
                context = this,
                isScripture = isCurrentlyShowingScripture,
                navigateToVerse = navigateToVerse,
                title = intent?.extras?.getCharSequence("title"),
                forceGrid = true
            )
            startActivityForResult(gridIntent, 1)
            true
        }
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadBookGroup(preferredBook: BibleBook?, preferredChapter: Int, preferredVerse: Int) {
        val allBooks = navigationControl.getBibleBooks(isCurrentlyShowingScripture)
        bookList = filterBooks(allBooks)
        testamentToggle.isEnabled = isCurrentlyShowingScripture
        for (i in 0 until testamentToggle.childCount) {
            testamentToggle.getChildAt(i).isEnabled = isCurrentlyShowingScripture
        }
        if (bookList.isEmpty()) {
            selectedReference.text = getString(R.string.verse_not_found)
            selectButton.isEnabled = false
            chapterDescriptionPanel.visibility = View.GONE
            return
        }

        selectedBookIndex = preferredBook?.let { bookList.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        selectedChapter = preferredChapter
        selectedVerse = preferredVerse
        selectButton.isEnabled = true
        syncAllControls()
    }

    private fun syncAllControls() {
        if (bookList.isEmpty()) return
        isUpdatingUi = true

        val currentBook = currentBookOrNull() ?: bookList.first()
        val chapterCount = chapterCountFor(currentBook)
        selectedChapter = selectedChapter.coerceIn(1, chapterCount)

        val verseCount = verseCountFor(currentBook, selectedChapter)
        selectedVerse = selectedVerse.coerceIn(1, verseCount)

        bookSeekBar.max = (bookList.size - 1).coerceAtLeast(0)
        bookSeekBar.progress = selectedBookIndex
        bookValue.text = longNameFor(currentBook)

        chapterSeekBar.max = (chapterCount - 1).coerceAtLeast(0)
        chapterSeekBar.progress = selectedChapter - 1
        chapterValue.text = selectedChapter.toString()

        verseSeekBar.max = (verseCount - 1).coerceAtLeast(0)
        verseSeekBar.progress = selectedVerse - 1
        verseValue.text = selectedVerse.toString()

        isUpdatingUi = false
        updateReferencePreview()
        updateDescriptionPanel()
    }

    private fun filterBooks(allBooks: List<BibleBook>): List<BibleBook> {
        if (!isCurrentlyShowingScripture) return allBooks
        return allBooks.filter { book ->
            when (testamentFilter) {
                TestamentFilter.OLD -> book.ordinal < BibleBook.MATT.ordinal
                TestamentFilter.NEW -> book.ordinal >= BibleBook.MATT.ordinal
            }
        }
    }

    private fun updateReferencePreview() {
        val currentBook = currentBookOrNull() ?: return
        selectedReference.text = "${longNameFor(currentBook)} $selectedChapter:$selectedVerse"
    }

    private fun stepBook(delta: Int) {
        if (bookList.isEmpty()) return
        selectedBookIndex = (selectedBookIndex + delta).coerceIn(0, bookList.lastIndex)
        syncAllControls()
    }

    private fun stepChapter(delta: Int) {
        val currentBook = currentBookOrNull() ?: return
        selectedChapter = (selectedChapter + delta).coerceIn(1, chapterCountFor(currentBook))
        syncAllControls()
    }

    private fun stepVerse(delta: Int) {
        val currentBook = currentBookOrNull() ?: return
        selectedVerse = (selectedVerse + delta).coerceIn(1, verseCountFor(currentBook, selectedChapter))
        syncAllControls()
    }

    private fun applyDescriptionPanelState() {
        val expanded = chapterDescriptionExpanded && chapterDescriptionPanel.visibility == View.VISIBLE
        chapterDescriptionBody.visibility = if (expanded) View.VISIBLE else View.GONE
        chapterDescriptionToggleIcon.setImageResource(
            if (expanded) R.drawable.ic_arrow_drop_up_grey_24dp
            else R.drawable.ic_arrow_drop_down_grey_24dp
        )
    }

    private fun updateDescriptionPanel() {
        if (!CommonUtils.showChapterDescriptionPanel) {
            chapterDescriptionPanel.visibility = View.GONE
            applyDescriptionPanelState()
            return
        }
        val currentBook = currentBookOrNull() ?: run {
            chapterDescriptionPanel.visibility = View.GONE
            applyDescriptionPanelState()
            return
        }
        val description = ChapterDescriptionProvider.find(currentBook, selectedChapter)
        if (description == null) {
            chapterDescriptionPanel.visibility = View.GONE
            applyDescriptionPanelState()
            return
        }
        chapterDescriptionTitle.text = getString(description.titleResId)
        chapterDescriptionBody.text = getString(description.bodyResId)
        chapterDescriptionPanel.visibility = View.VISIBLE
        applyDescriptionPanelState()
    }

    private fun selectCurrentPassage() {
        val currentBook = currentBookOrNull() ?: return
        val selectedVerseNo = if (navigateToVerse) selectedVerse else 1
        val verse = Verse(navigationControl.versification, currentBook, selectedChapter, selectedVerseNo)
        Log.i(TAG, "One-view selected:$verse")
        val resultIntent = Intent(this, GridChoosePassageBook::class.java).apply {
            putExtra("verse", verse.osisID)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun currentBookOrNull(): BibleBook? = bookList.getOrNull(selectedBookIndex)

    private fun longNameFor(book: BibleBook): String {
        return try {
            navigationControl.versification.getLongName(book)
        } catch (e: Exception) {
            book.name
        }
    }

    private fun chapterCountFor(book: BibleBook): Int {
        return try {
            navigationControl.versification.getLastChapter(book).coerceAtLeast(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapter count for $book", e)
            1
        }
    }

    private fun verseCountFor(book: BibleBook, chapter: Int): Int {
        return try {
            navigationControl.versification.getLastVerse(book, chapter).coerceAtLeast(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse count for $book $chapter", e)
            1
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, data)
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val TAG = "OneViewChoosePassage"
    }
}
