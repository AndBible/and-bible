/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.search

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MultiSearchResultItemBinding
import net.bible.android.control.search.GroupedSearchResult
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.TranslationMatch
import net.bible.service.common.htmlToSpan
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.jdom2.Element
import org.jdom2.Text
import java.util.regex.Pattern

/**
 * Adapter for multi-translation search results with expandable items
 */
class MultiSearchItemAdapter(
    context: Context,
    items: List<GroupedSearchResult>,
    private val onTranslationClick: (SwordBook, Key) -> Unit
) : ArrayAdapter<GroupedSearchResult>(context, R.layout.multi_search_result_item, items) {

    private val expandedItems = mutableSetOf<Int>()
    private val inflater = LayoutInflater.from(context)

    private class ViewHolder(val binding: MultiSearchResultItemBinding)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val view: View

        if (convertView == null) {
            val binding = MultiSearchResultItemBinding.inflate(inflater, parent, false)
            holder = ViewHolder(binding)
            view = binding.root
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = getItem(position) ?: return view
        val binding = holder.binding

        val isSingleMatch = item.translationMatches.size == 1
        val firstMatch = item.translationMatches.first()

        // Set verse reference - include translation abbreviation for single match
        binding.verseReference.text = if (isSingleMatch) {
            "${item.displayName} (${firstMatch.book.abbreviation})"
        } else {
            item.displayName
        }

        // Set verse preview from first translation
        try {
            val textElement = SwordContentFacade.readOsisFragment(firstMatch.book, firstMatch.key)
            val searchTerms = SearchControl.originalSearchString ?: ""
            val highlightedText = highlightSearchText(searchTerms, textElement)
            binding.versePreview.text = highlightedText
            // For single match, show full text without line limit
            binding.versePreview.maxLines = if (isSingleMatch) Int.MAX_VALUE else 2
        } catch (e: Exception) {
            Log.e(TAG, "Error reading verse preview", e)
            binding.versePreview.text = ""
        }

        // Get the HorizontalScrollView parent of translationPills
        val translationPillsContainer = binding.translationPills.parent as View

        // Clicking the header row always navigates directly to the verse
        binding.headerRow.setOnClickListener {
            onTranslationClick(firstMatch.book, firstMatch.key)
        }

        if (isSingleMatch) {
            // Single translation match - show directly without expand functionality
            binding.expandIcon.visibility = View.GONE
            translationPillsContainer.visibility = View.GONE
            binding.expandableContent.visibility = View.GONE
            binding.expandIcon.setOnClickListener(null)
        } else {
            // Multiple translation matches - show expandable view
            binding.expandIcon.visibility = View.VISIBLE
            translationPillsContainer.visibility = View.VISIBLE

            // Build translation pills
            binding.translationPills.removeAllViews()
            for (match in item.translationMatches) {
                val pill = createTranslationPill(match)
                binding.translationPills.addView(pill)
            }

            // Handle expansion state
            val isExpanded = expandedItems.contains(position)
            binding.expandableContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.expandIcon.rotation = if (isExpanded) 90f else 0f

            // Populate expanded content if visible
            if (isExpanded) {
                populateExpandedContent(binding.expandableContent, item)
            }

            // Only the expand icon toggles expansion
            binding.expandIcon.setOnClickListener {
                toggleExpansion(position, binding, item)
            }
        }

        return view
    }

    private fun createTranslationPill(match: TranslationMatch): TextView {
        val pill = TextView(context)
        pill.text = match.book.abbreviation
        pill.setBackgroundResource(R.drawable.translation_pill_background)
        pill.setPadding(
            dpToPx(12),
            dpToPx(4),
            dpToPx(12),
            dpToPx(4)
        )
        pill.textSize = 12f

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = dpToPx(4)
        pill.layoutParams = params

        pill.setOnClickListener {
            onTranslationClick(match.book, match.key)
        }

        return pill
    }

    private fun toggleExpansion(
        position: Int,
        binding: MultiSearchResultItemBinding,
        item: GroupedSearchResult
    ) {
        val isExpanding = !expandedItems.contains(position)

        // Animate icon rotation
        val fromDegree = if (isExpanding) 0f else 90f
        val toDegree = if (isExpanding) 90f else 0f
        val rotateAnimation = RotateAnimation(
            fromDegree, toDegree,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.duration = 200
        rotateAnimation.fillAfter = true
        binding.expandIcon.startAnimation(rotateAnimation)

        if (isExpanding) {
            expandedItems.add(position)
            populateExpandedContent(binding.expandableContent, item)
            binding.expandableContent.visibility = View.VISIBLE
        } else {
            expandedItems.remove(position)
            binding.expandableContent.visibility = View.GONE
        }
    }

    private fun populateExpandedContent(container: LinearLayout, item: GroupedSearchResult) {
        container.removeAllViews()

        for (match in item.translationMatches) {
            val verseView = inflater.inflate(R.layout.translation_verse_item, container, false) as TextView

            // Get verse text with abbreviation prefix
            try {
                val textElement = SwordContentFacade.readOsisFragment(match.book, match.key)
                val searchTerms = SearchControl.originalSearchString ?: ""
                val highlightedText = highlightSearchText(searchTerms, textElement)

                // Create text with bold abbreviation prefix, preserving highlighting
                val prefix = "${match.book.abbreviation}: "
                val fullText = SpannableStringBuilder()
                fullText.append(prefix)
                fullText.setSpan(StyleSpan(Typeface.BOLD), 0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                fullText.append(highlightedText)
                verseView.text = fullText
            } catch (e: Exception) {
                Log.e(TAG, "Error reading verse text", e)
                verseView.text = "${match.book.abbreviation}: ${context.getString(R.string.error_occurred)}"
            }

            // Make the verse clickable to navigate
            verseView.setOnClickListener {
                onTranslationClick(match.book, match.key)
            }

            container.addView(verseView)
        }
    }

    private fun highlightSearchText(searchTermsInput: String, textElement: Element): SpannableString {
        val searchTerms = prepareSearchTerms(searchTermsInput)
        val isStrongsSearch = searchTerms.contains("strong:", ignoreCase = true)
        val strongsPattern = if (isStrongsSearch) Pattern.compile(searchTerms, Pattern.CASE_INSENSITIVE) else null
        val verseString = StringBuilder()

        val verses = textElement.getChildren("verse")
        for (verse in verses) {
            if (strongsPattern != null) {
                verseString.append(processElementChildrenWithLemmaHighlight(verse, strongsPattern, false))
            } else {
                verseString.append(processElementChildren(verse))
            }
        }

        val spannableText = SpannableString(htmlToSpan(verseString.toString()))

        if (!isStrongsSearch) {
            try {
                val splitTerms = splitSearchTerms(searchTerms)
                for (originalSearchWord in splitTerms) {
                    var searchWord = prepareSearchWord(originalSearchWord)
                    searchWord = if (originalSearchWord.contains("*")) {
                        "\\b$searchWord[\\w'\\-]*\\b"
                    } else {
                        "\\b$searchWord\\b"
                    }
                    val m = Pattern.compile(searchWord, Pattern.CASE_INSENSITIVE).matcher(spannableText)
                    while (m.find()) {
                        spannableText.setSpan(
                            StyleSpan(Typeface.BOLD),
                            m.start(),
                            m.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error highlighting search text", e)
            }
        }

        return spannableText
    }

    private val elementsToExclude = listOf("note", "reference")

    private fun processElementChildren(parentElement: Element): String {
        val verseString = StringBuilder()
        for (o in parentElement.content) {
            when (o) {
                is Element -> {
                    if (!elementsToExclude.contains(o.name)) {
                        if (o.children.isEmpty()) {
                            verseString.append(o.text)
                        } else {
                            verseString.append(processElementChildren(o))
                        }
                    }
                }
                is Text -> {
                    verseString.append(o.text)
                }
                else -> {
                    verseString.append(o.toString())
                }
            }
        }
        return verseString.toString()
    }

    /**
     * Process element children for Strong's searches, checking lemma attributes to determine
     * which words should be bolded. Returns HTML string with <b> tags for matched words.
     */
    private fun processElementChildrenWithLemmaHighlight(
        parentElement: Element,
        strongsPattern: Pattern,
        isBold: Boolean
    ): String {
        val verseString = StringBuilder()
        for (o in parentElement.content) {
            when (o) {
                is Element -> {
                    if (!elementsToExclude.contains(o.name)) {
                        val currentIsBold = isBold || try {
                            val lemma = o.getAttributeValue("lemma")
                            lemma != null && strongsPattern.matcher(lemma.trim()).find()
                        } catch (e: Exception) {
                            false
                        }
                        if (o.children.isEmpty()) {
                            val text = o.text ?: ""
                            verseString.append(if (currentIsBold) "<b>$text</b>" else text)
                        } else {
                            verseString.append(processElementChildrenWithLemmaHighlight(o, strongsPattern, currentIsBold))
                        }
                    }
                }
                is Text -> {
                    verseString.append(if (isBold) "<b>${o.text}</b>" else o.text)
                }
                else -> {
                    verseString.append(o.toString())
                }
            }
        }
        return verseString.toString()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "MultiSearchItemAdapter"
    }
}
