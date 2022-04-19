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
import java.util.regex.Pattern

class SearchHighlight(searchTerms: String) {

    val isStrongsSearch = searchTerms.contains("strong:")
    val elementsToExclude: List<String> = listOf("note", "reference")
    val elementsToInclude: List<String> = listOf("w", "transChange", "divineName", "seg")
    val consecutiveWordsRegex = Regex("(<b[^<>]*>)([^<>]*?)(<\\/b>)(\\s+)\\1([\\s\\S]*?)\\3", RegexOption.IGNORE_CASE)
    val strongsSearchPattern: Pattern = Pattern.compile(prepareStrongsSearchTerm(searchTerms) + "\\b", Pattern.CASE_INSENSITIVE)  // search on a word boundary (eg find strong:g0123 not strong:g01234567

    private val preparedSearchWordsPatternList:List<Pattern> = // Build a list of Patterns representing each search word.
        splitSearchTerms(searchTerms).map {
            var searchWord = prepareSearchWord(it)
            searchWord =  if (it.contains("*") or it.contains("~")) {
                "\\b$searchWord[\\w\\'\\-]*\\b" // Match whole words including with hyphens and apostrophes
            } else {
                "\\b$searchWord\\b"
            }
            Pattern.compile(searchWord, Pattern.CASE_INSENSITIVE)
        }

        fun generateSpannableFromVerseElement(verseElement: Element): SpannableString {
            /* Takes a verse in Element form and rebuilds the verse in string form.
             * Interestingly it is faster to get the text in Element form and convert it myself than to call 'getSearchResultVerseText'.
             * Once the verse is in String format which includes <b> tags for Strongs numbers it is passed to 'generateSpannableFromVerseString'
             * which does the actual conversion to a spannable.
            `*/
            var verseString = ""
            try {
                // TODO: Strongs searches are handled differently to normal searches and so cannot be combined either with a normal search term or other strongs searches. This should be done better.

                // Part 1: Highlight any strongs words. The raw verse text is returned with <b> added for the strongs words. We always need to process this just to get the plaintext
                val verses = verseElement.getChildren("verse")

                for (verse in verses) {
                    verseString += processElementChildren(verse, "", false)
                }

                if (isStrongsSearch) {
                    // Check for highlighted consecutive words and merge them into a single highlighted phrase. Some translations will indicate multiple
                    // consecutive lemma spans with the same strongs number when in fact all spans represent the single original language word.
                    // This messes with the search statistics as it uses the bolded words to tally search hits by word.
                    var verseStringLength = 0
                    while (verseStringLength != verseString.length) {
                        verseStringLength = verseString.length
                        // This pattern matches two consecutive <b> tag spans separate only by white space and replaces them with a single <b> tag.
                        val match = consecutiveWordsRegex.find(verseString)
                        if (match != null) {
                            val matches = match.groupValues
                            verseString = verseString.replace(
                                matches[0],
                                "${matches[1]}${matches[2]}${matches[4]}${matches[5]}${matches[3]}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SEARCH", e.message!!)
            } finally {
                return generateSpannableFromVerseString(verseString)
            }
        }

        fun generateSpannableFromVerseString(verseString:String): SpannableString {

            val spannableText = SpannableString(Html.fromHtml(verseString))  // We started with an XML verse which got turned into a string with <b> tags which is now turned into a spannable
            try {

                // Part 2: Find and highlight the normal (non-strongs) words or phrases in the PLAIN text verse
                for (searchPattern in preparedSearchWordsPatternList) {
                    val m = searchPattern.matcher(spannableText)
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
                Log.w("SEARCH", e.message!!)
            } finally {
                return spannableText
            }
        }

        private fun processElementChildren(
            parentElement: Element,
            verseStringInitial: String?,
            isBoldInitial: Boolean
        ): String? {
            // Recursive method to walk the verse element tree ignoring tags like 'note' that should not be shown in the search results
            // and including tags like 'w' that should be included. This routine is needed only to do searches on lemma attributes. That
            // is why bolding only occurs in that part of the code.
            var verseString = verseStringInitial
            var isBold = isBoldInitial
            for (el in parentElement.content) {
                if (el is Element) {
                    if (elementsToInclude.contains(el.name)) {
                        isBold = try {
                            if (isStrongsSearch) {
                                val lemma = el.getAttributeValue("lemma")
                                lemma != null && strongsSearchPattern.matcher(lemma.trim { it <= ' ' }).find()
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            false
                        }
                        // Only leaf nodes should have their text appended. If a node has child tags, the text will be passed as one of the children .
                        if (el.children.isEmpty()) verseString += buildElementText(el.text, isBold)
                    }
                    if (el.children.isNotEmpty() && !elementsToExclude.contains(el.name)) {
                        verseString = processElementChildren(el, verseString, isBold)
                    }
                } else if (el is Text) {
                    verseString += buildElementText(el.text, false)
                } else {
                    verseString += buildElementText(el.toString(), false)
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

        private fun prepareStrongsSearchTerm(_searchTerms: String): String {
            // Replaces strong:g00123 or strong:g123 with REGEX strong:g0*123. This is needed because the search term submitted by the 'Find all occurrences includes extra zeros
            // The capitalisation is not important since we do a case insensitive search
            var searchTerms = _searchTerms
            searchTerms = searchTerms.replace("strong:g0*".toRegex(RegexOption.IGNORE_CASE), "strong:g0*")
            searchTerms = searchTerms.replace("strong:h0*".toRegex(RegexOption.IGNORE_CASE), "strong:h0*")
            searchTerms = searchTerms.replace("+", "")        // Remove + which indicates AND searches

            return searchTerms
        }

        private fun splitSearchTerms(searchTerms: String): Array<String> {
            // Find the individual words that we are looking for so we can highlight them.
            // Proximity searches wrap the words in quotes the quotes need to be removed.
            val fuzzySearch = searchTerms.indexOf("~")
            val newSearchTerms = if (fuzzySearch > -1) {
                searchTerms.substring(0,fuzzySearch).replace("\"","")
            } else searchTerms
            // Split the search terms on space characters that are not enclosed in double quotes.
            // Eg: 'moses "burning bush"' -> "moses" and "burning bush"
            return newSearchTerms.split("\\s+(?=(?:\"(?:\\\\\"|[^\"])+\"|[^\"])+$)".toRegex()).toTypedArray()
        }

        private fun prepareSearchWord(searchWord: String): String {
            // Need to clean up the search word itself before trying to find the searchWord in the text. Routine is called for each part of the search term
            // Eg: '+"burning bush"' -> 'burning bush'
            val fuzzySearch = searchWord.indexOf("~")
            var newSearchWord = if (fuzzySearch > -1) { searchWord.substring(0,fuzzySearch) } else searchWord
            newSearchWord = newSearchWord.replace("\"", "")       // Remove quotes which indicate phrase searches
            newSearchWord = newSearchWord.replace("+", "")        // Remove + which indicates AND searches
            newSearchWord = newSearchWord.replace("?", "\\p{L}")  // Handles any letter from any language
            if (newSearchWord.isNotEmpty()) {
                newSearchWord = if ((newSearchWord.substring(newSearchWord.length - 1) == "*") or (fuzzySearch>-1)){
                    // The last character in the search is a * so remove it since the default sword search assumes a wildcard search
                    newSearchWord.replace("*", "")
                } else {
                    // A * found inside a search term should probably be ignored.
                    // It can happen normally as part of a strongs search since the regex expression uses a * to find any number of leading 0s.
                    newSearchWord.replace("*", "\b") // Match on a word boundary - I am not sure why this was needed.

                }
            }
            return newSearchWord
        }

}
