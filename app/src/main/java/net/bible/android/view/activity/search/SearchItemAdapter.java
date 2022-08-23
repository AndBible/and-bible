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

package net.bible.android.view.activity.search;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;
import net.bible.android.control.search.SearchControl;
import org.crosswire.jsword.passage.Key;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jdom2.Element;
import org.jdom2.Text;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class SearchItemAdapter extends ArrayAdapter<Key> {

	private int resource;
	private SearchControl searchControl;

	public SearchItemAdapter(Context _context, int _resource, List<Key> _items, SearchControl searchControl) {
		super(_context, _resource, _items);
		this.resource = _resource;
		this.searchControl = searchControl;
	}

	static String prepareSearchTerms(String searchTerms) {
		// Replaces strong:g00123 with REGEX strong:g*123. This is needed because the search term submitted by the 'Find all occcurrences includes extra zeros
		// The capitalisation is not important since we do a case insensitive search
		if (searchTerms.contains("strong:")) {
			searchTerms = searchTerms.replaceAll("strong:g0*", "strong:g0*");
			searchTerms = searchTerms.replaceAll("strong:h0*", "strong:h0*");
		}
		return searchTerms;
	}

	static String[] splitSearchTerms(String searchTerms) {
		// Split the search terms on space characters that are not enclosed in double quotes
		// Eg: 'moses "burning bush"' -> "moses" and "burning bush"
		return searchTerms.split("\\s+(?=(?:\"(?:\\\\\"|[^\"])+\"|[^\"])+$)");
	}

	static String prepareSearchWord(String searchWord) {
		// Need to clean up the search word itself before trying to find the searchWord in the text
		// Eg: '+"burning bush"' -> 'burning bush'
		searchWord = searchWord.replace("\"", "");  // Remove quotes which indicate phrase searches
		searchWord = searchWord.replace("+", "");	// Remove + which indicates AND searches
		searchWord = searchWord.replace("?", "\\p{L}");  // Handles any letter from any language
		if (searchWord.length() > 0) {
			if (Objects.equals(searchWord.substring(searchWord.length() - 1), "*")) {
				searchWord = searchWord.replace("*", "");
			} else {
				searchWord = searchWord.replace("*", "\b");  // Match on a word boundary
			}
		}
		return searchWord;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Key item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TwoLineListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TwoLineListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (TwoLineListItem) convertView;
		}

		// Set value for the first text field
		if (view.getText1() != null) {
			String key = item.getName();
			view.getText1().setText(key);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			Element verseTextElement = searchControl.getSearchResultVerseElement(item);
			SpannableString verseTextHtml = highlightSearchText(SearchControl.originalSearchString, verseTextElement);
			view.getText2().setText(verseTextHtml);
		}

		return view;
	}

	private final List<String> elementsToExclude = Arrays.asList("note","reference");
	//private final List<String> elementsToInclude = Arrays.asList("w","transChange","divineName","seg","q", "p");

	private String processElementChildren(Element parentElement, String searchTerms, String verseString, Boolean isBold) {
		// Recursive method to walk the verse element tree ignoring tags like 'note' that should not be shown in the search results
		// and including tags like 'w' that should be included. This routine is needed only to do searches on lemma attributes. That
		// is why bolding only occurs in that part of the code.

		for (Object o : parentElement.getContent()) {
			if (o instanceof Element) {
				Element el = (Element) o;
				if (!elementsToExclude.contains(el.getName())) {
					try {
						String lemma = el.getAttributeValue("lemma");
						isBold = (lemma != null && Pattern.compile(searchTerms, Pattern.CASE_INSENSITIVE).matcher(lemma.trim()).find());
					} catch (Exception e) {
						isBold = false;
					}
					// Only leaf nodes should have their text appended. If a node has child tags, the text will be passed as one of the children .
					if (el.getChildren().isEmpty()) {
						verseString += buildElementText(el.getText(), isBold);
					}
				}
				if (!el.getChildren().isEmpty() && !elementsToExclude.contains(el.getName())) {
					verseString = processElementChildren(el, searchTerms, verseString, isBold);
				}
			} else if (o instanceof Text) {
				Text t = (Text) o;
				verseString += buildElementText(t.getText(),false);
			} else {
				verseString += buildElementText(o.toString(),false);
			}
		}
		return verseString;
	}

	private String buildElementText(String elementText, Boolean isBold) {
		if (isBold) {
			return String.format("<b>%s</b>",elementText);
		} else {
			return elementText;
		}
	}

	private SpannableString highlightSearchText(String searchTerms, Element textElement) {

		SpannableString spannableText = null;
		try {
			StringBuilder verseString = new StringBuilder();
			searchTerms = prepareSearchTerms(searchTerms);

			List<Element> verses = textElement.getChildren("verse");
			for (Element verse : verses) {
				verseString.append(processElementChildren(verse, searchTerms, "", false));
			}
			spannableText = new SpannableString(Html.fromHtml(verseString.toString()));
			Matcher m;
			String[] splitSearchArray = splitSearchTerms(searchTerms);
			for (String originalSearchWord : splitSearchArray) {
				String searchWord = prepareSearchWord(originalSearchWord);
				if (originalSearchWord.contains("*")) {
					searchWord = "\\b" + searchWord + "[\\w'\\-]*\\b";  // Match whole words including with hyphons and apostrophes
				} else {
					searchWord = "\\b" + searchWord + "\\b";
				}
				m = Pattern.compile(searchWord, Pattern.CASE_INSENSITIVE).matcher(spannableText);
				while (m.find()) {
					spannableText.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		}
		catch (Exception e) {
			Log.e("SEARCH", "Error in highlightSearchText", e);
		}
		return spannableText;
	}
}
