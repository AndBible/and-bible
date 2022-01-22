package net.bible.android.view.activity.search

import android.graphics.Typeface
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import org.jdom2.Element
import org.jdom2.Text
import java.lang.Exception
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class SearchHighlight {

    companion object {
        fun getSpannableText(searchTerms: String, textElement: Element): SpannableString? {
            var searchTerms = searchTerms
            var spannableText: SpannableString? = null
            try {
                var verseString: String? = ""
                searchTerms = prepareSearchTerms(searchTerms)
                val verses = textElement.getChildren("verse")
                for (verse in verses) {
                    verseString += processElementChildren(verse, searchTerms, "", false)
                }
                spannableText = SpannableString(Html.fromHtml(verseString))
                var m: Matcher? = null
                val splitSearchArray = splitSearchTerms(searchTerms)
                for (originalSearchWord in splitSearchArray) {
                    var searchWord = prepareSearchWord(originalSearchWord)
                    searchWord = if (originalSearchWord.contains("*")) {
                        "\\b$searchWord[\\w\\'\\-]*\\b" // Match whole words including with hyphons and apostrophes
                    } else {
                        "\\b$searchWord\\b"
                    }
                    if (searchWord.length > 0) {
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
                }
            } catch (e: Exception) {
                Log.w("SEARCH", e.message!!)
            } finally {
                return spannableText
            }
        }

        private fun processElementChildren(
            parentElement: Element,
            searchTerms: String,
            verseString: String?,
            isBold: Boolean
        ): String? {
            // Recursive method to walk the verse element tree ignoring tags like 'note' that should not be shown in the search results
            // and including tags like 'w' that should be included. This routine is needed only to do searches on lemma attributes. That
            // is why bolding only occurs in that part of the code.
            var verseString = verseString
            var isBold = isBold
            for (o in parentElement.content) {
                if (o is Element) {
                    val el = o
                    val elementsToExclude = Arrays.asList("note", "reference")
                    val elementsToInclude = Arrays.asList("w", "transChange", "divineName", "seg")
                    if (elementsToInclude.contains(el.name)) {
                        isBold = try {
                            val lemma = el.getAttributeValue("lemma")
                            lemma != null && Pattern.compile(searchTerms, Pattern.CASE_INSENSITIVE)
                                .matcher(lemma.trim { it <= ' ' }).find()
                        } catch (e: Exception) {
                            false
                        }
                        // Only leaf nodes should have their text appended. If a node has child tags, the text will be passed as one of the children .
                        if (el.children.isEmpty()) verseString += buildElementText(el.text, isBold)
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

        private fun buildElementText(elementText: String, isBold: Boolean): String? {
            return if (isBold) {
                String.format("<b>%s</b>", elementText)
            } else {
                elementText
            }
        }


        private fun prepareSearchTerms(searchTerms: String): String {
            // Replaces strong:g00123 or strong:g123 with REGEX strong:g0*123. This is needed because the search term submitted by the 'Find all occcurrences includes extra zeros
            // The capitalisation is not important since we do a case insensitive search
            var searchTerms = searchTerms
            if (searchTerms.contains("strong:")) {
                searchTerms = searchTerms.replace("strong:g0*".toRegex(RegexOption.IGNORE_CASE), "strong:g0*")
                searchTerms = searchTerms.replace("strong:h0*".toRegex(RegexOption.IGNORE_CASE), "strong:h0*")
                searchTerms += "\\b"  // search on a word boundary (eg find strong:g0123 not strong:g01234567
            }
            return searchTerms
        }

        private fun splitSearchTerms(searchTerms: String): Array<String> {
            // Split the search terms on space characters that are not enclosed in double quotes
            // Eg: 'moses "burning bush"' -> "moses" and "burning bush"
            return searchTerms.split("\\s+(?=(?:\"(?:\\\\\"|[^\"])+\"|[^\"])+$)".toRegex()).toTypedArray()
        }

        private fun prepareSearchWord(searchWord: String): String {
            // Need to clean up the search word itself before trying to find the searchWord in the text
            // Eg: '+"burning bush"' -> 'burning bush'
            var searchWord = searchWord
            searchWord = searchWord.replace("\"", "")       // Remove quotes which indicate phrase searches
            searchWord = searchWord.replace("+", "")        // Remove + which indicates AND searches
            searchWord = searchWord.replace("?", "\\p{L}")  // Handles any letter from any language
            if (searchWord.length > 0) {
                searchWord = if (searchWord.substring(searchWord.length - 1) == "*") {
                    searchWord.replace("*", "")
                } else {
                    searchWord.replace("*", "\b") // Match on a word boundary
                }
            }
            return searchWord
        }
    }
}
