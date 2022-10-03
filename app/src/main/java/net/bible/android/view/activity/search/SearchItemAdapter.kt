/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.control.search.SearchControl
import android.widget.ArrayAdapter
import android.view.ViewGroup
import android.view.LayoutInflater
import android.text.Html
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.TwoLineListItem
import org.crosswire.jsword.passage.Key
import org.jdom2.Element
import org.jdom2.Text
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


fun prepareSearchTerms(searchTerms_: String): String {
    // Replaces strong:g00123 with REGEX strong:g*123. This is needed because the search term submitted by the 'Find all occcurrences includes extra zeros
    // The capitalisation is not important since we do a case insensitive search
    var searchTerms = searchTerms_
    if (searchTerms.contains("strong:")) {
        searchTerms = searchTerms.replace("strong:g0*".toRegex(), "strong:g0*")
        searchTerms = searchTerms.replace("strong:h0*".toRegex(), "strong:h0*")
    }
    return searchTerms
}

fun splitSearchTerms(searchTerms: String): Array<String> {
    // Split the search terms on space characters that are not enclosed in double quotes
    // Eg: 'moses "burning bush"' -> "moses" and "burning bush"
    return searchTerms.split("\\s+(?=(?:\"(?:\\\\\"|[^\"])+\"|[^\"])+$)").toTypedArray()
}

fun prepareSearchWord(searchWord_: String): String {
    // Need to clean up the search word itself before trying to find the searchWord in the text
    // Eg: '+"burning bush"' -> 'burning bush'
    var searchWord = searchWord_
    searchWord =
        searchWord.replace("\"", "") // Remove quotes which indicate phrase searches
    searchWord = searchWord.replace("+", "") // Remove + which indicates AND searches
    searchWord = searchWord.replace("?", "\\p{L}") // Handles any letter from any language
    if (searchWord.length > 0) {
        searchWord = if (searchWord.substring(searchWord.length - 1) == "*") {
            searchWord.replace("*", "")
        } else {
            searchWord.replace("*", "\b") // Match on a word boundary
        }
    }
    return searchWord
}

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SearchItemAdapter(
    _context: Context,
    private val resource: Int,
    _items: List<Key>,
    private val searchControl: SearchControl
) : ArrayAdapter<Key>(
    _context, resource, _items
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)

        // Pick up the TwoLineListItem defined in the xml file
        val view: TwoLineListItem
        view = if (convertView == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false) as TwoLineListItem
        } else {
            convertView as TwoLineListItem
        }

        // Set value for the first text field
        if (view.text1 != null) {
            val key = item!!.name
            view.text1.text = key
        }

        // set value for the second text field
        if (view.text2 != null) {
            val verseTextElement = searchControl.getSearchResultVerseElement(item)
            val verseTextHtml =
                highlightSearchText(SearchControl.originalSearchString, verseTextElement)
            view.text2.text = verseTextHtml
        }
        return view
    }

    private val elementsToExclude = Arrays.asList("note", "reference")

    //private final List<String> elementsToInclude = Arrays.asList("w","transChange","divineName","seg","q", "p");
    private fun processElementChildren(
        parentElement: Element,
        searchTerms: String,
        verseString_: String,
        isBold_: Boolean
    ): String {
        // Recursive method to walk the verse element tree ignoring tags like 'note' that should not be shown in the search results
        // and including tags like 'w' that should be included. This routine is needed only to do searches on lemma attributes. That
        // is why bolding only occurs in that part of the code.
        var verseString = verseString_
        var isBold = isBold_
        for (o in parentElement.content) {
            if (o is Element) {
                val el = o
                if (!elementsToExclude.contains(el.name)) {
                    isBold = try {
                        val lemma = el.getAttributeValue("lemma")
                        lemma != null && Pattern.compile(searchTerms, Pattern.CASE_INSENSITIVE)
                            .matcher(lemma.trim { it <= ' ' }).find()
                    } catch (e: Exception) {
                        false
                    }
                    // Only leaf nodes should have their text appended. If a node has child tags, the text will be passed as one of the children .
                    if (el.children.isEmpty()) {
                        verseString += buildElementText(el.text, isBold)
                    }
                }
                if (!el.children.isEmpty() && !elementsToExclude.contains(el.name)) {
                    verseString = processElementChildren(el, searchTerms, verseString, isBold)
                }
            } else if (o is Text) {
                verseString += buildElementText(o.text, false)
            } else {
                verseString += buildElementText(o.toString(), false)
            }
        }
        return verseString
    }

    private fun buildElementText(elementText: String, isBold: Boolean): String {
        return if (isBold) {
            String.format("<b>%s</b>", elementText)
        } else {
            elementText
        }
    }

    private fun highlightSearchText(searchTerms_: String, textElement: Element): SpannableString?{
        var searchTerms = searchTerms_
        var spannableText: SpannableString? = null
        try {
            val verseString = StringBuilder()
            searchTerms = prepareSearchTerms(searchTerms)
            val verses = textElement.getChildren("verse")
            for (verse in verses) {
                verseString.append(processElementChildren(verse, searchTerms, "", false))
            }
            spannableText = SpannableString(Html.fromHtml(verseString.toString()))
            var m: Matcher
            val splitSearchArray = splitSearchTerms(searchTerms)
            for (originalSearchWord in splitSearchArray) {
                var searchWord = prepareSearchWord(originalSearchWord)
                searchWord = if (originalSearchWord.contains("*")) {
                    "\\b$searchWord[\\w'\\-]*\\b" // Match whole words including with hyphons and apostrophes
                } else {
                    "\\b$searchWord\\b"
                }
                m = Pattern.compile(searchWord, Pattern.CASE_INSENSITIVE).matcher(spannableText)
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
            Log.e("SEARCH", "Error in highlightSearchText", e)
        }
        return spannableText
    }
}
