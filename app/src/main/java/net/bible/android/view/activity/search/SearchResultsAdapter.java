package net.bible.android.view.activity.search;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import net.bible.android.activity.R;
import net.bible.android.control.search.SearchControl;
import net.bible.service.sword.SwordDocumentFacade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bible.android.view.activity.base.ActivityBase;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.jdom2.Element;
import org.jdom2.Text;


public class SearchResultsAdapter extends BaseAdapter {

	private ArrayList<SearchResultsData> arrayList;
	private Context context;
	private SearchControl searchControl;

	public SearchResultsAdapter(Context context, ArrayList<SearchResultsData> arrayList, SearchControl searchControl) {
		this.arrayList=arrayList;
		this.context=context;
		this.searchControl = searchControl;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}
	@Override
	public boolean isEnabled(int position) {
		return true;
	}
	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
	}
	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
	}
	@Override
	public int getCount() {
		return arrayList.size();
	}
	@Override
	public Object getItem(int position) {
		return position;
	}

	public Object getItemAtPosition(int position) {
		return position;
	}
	@Override
	public long getItemId(int position) {
		return position;
	}
	@Override
	public boolean hasStableIds() {
		return false;
	}
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		SearchResultsData subjectData=arrayList.get(position);
		if(convertView==null) {
			LayoutInflater layoutInflater = LayoutInflater.from(context);
			convertView=layoutInflater.inflate(R.layout.search_results_list_row, null);
//			convertView.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//				}
//			});

			TextView reference=convertView.findViewById(R.id.reference);
			reference.setText(subjectData.reference);

			TextView translation=convertView.findViewById(R.id.translation);
			translation.setText(subjectData.translation);

			// Get the text of the verse
			Book book = Books.installed().getBook("KJV");
			try {
				Key key = book.getKey(subjectData.osisKey);
				Element verseTextElement = searchControl.getSearchResultVerseElement(key);
				SpannableString verseTextHtml = highlightSearchText(SearchControl.originalSearchString, verseTextElement);

				TextView verse=convertView.findViewById(R.id.verse);
				verse.setText(verseTextHtml);
//				verse.setOnClickListener(new View.OnClickListener() {
//					@Override
//					public void onClick(View v) {
//						// This works but may stop if i have android:descendantFocusability="blocksDescendants" working
//						Toast.makeText(v.getContext() , "Clicked verse control " + position + " " + subjectData.id ,Toast.LENGTH_SHORT).show();
//
//					}});
			} catch (NoSuchKeyException e) {
				e.printStackTrace();
			}
		}
		return convertView;
	}
	@Override
	public int getItemViewType(int position) {
		return position;
	}
	@Override
	public int getViewTypeCount() {
		return arrayList.size();
	}
	@Override
	public boolean isEmpty() {
		return false;
	}

	private String processElementChildren(Element parentElement, String searchTerms, String verseString, Boolean isBold) {
		// Recursive method to walk the verse element tree ignoring tags like 'note' that should not be shown in the search results
		// and including tags like 'w' that should be included. This routine is needed only to do searches on lemma attributes. That
		// is why bolding only occurs in that part of the code.
		for (Object o : parentElement.getContent()) {
			if (o instanceof Element) {
				Element el = (Element) o;
				List<String> elementsToExclude = Arrays.asList("note","reference");
				List<String> elementsToInclude = Arrays.asList("w","transChange","divineName","seg");
				if (elementsToInclude.contains(el.getName())) {
					try {
						String lemma = el.getAttributeValue("lemma");
						isBold = (lemma != null && Pattern.compile(searchTerms, Pattern.CASE_INSENSITIVE).matcher(lemma.trim()).find());
					} catch (Exception e) {
						isBold = false;
					}
					// Only leaf nodes should have their text appended. If a node has child tags, the text will be passed as one of the children .
					if (el.getChildren().isEmpty()) verseString += buildElementText(el.getText(),isBold);
				}
				if (!el.getChildren().isEmpty() && !elementsToExclude.contains(el.getName())) {verseString = processElementChildren(el, searchTerms, verseString, isBold);};
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
			String verseString = "";
			searchTerms = prepareSearchTerms(searchTerms);

			List<Element> verses = textElement.getChildren("verse");
			for (Element verse : verses) {
				verseString += processElementChildren(verse, searchTerms, "", false);
			}
			spannableText = new SpannableString(Html.fromHtml(verseString));
			Matcher m = null;
			String[] splitSearchArray = splitSearchTerms(searchTerms);
			for (String searchWord : splitSearchArray) {
				searchWord = prepareSearchWord(searchWord);
				if (searchWord.length() > 0) {
					m = Pattern.compile(searchWord, Pattern.CASE_INSENSITIVE).matcher(spannableText);
					while (m.find()) {
						spannableText.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					}
				}
			}
		}
		catch (Exception e) {
			Log.w("SEARCH", e.getMessage());
		}
		finally {
			return spannableText;
		}
	}

	public static class testSearch {
		String searchTerms;
		String testType;
		public testSearch(String searchTerms) {
			this.searchTerms = searchTerms;
			this.testType = testType;
		}
		public String PrepareSearchTerms() {return prepareSearchTerms(searchTerms);}
		public String[] SplitSearchTerms() {return splitSearchTerms(searchTerms);}
		public String PrepareSearchWord() {return prepareSearchWord(searchTerms);}
	}

	private static String prepareSearchTerms(String searchTerms) {
		// Replaces strong:g00123 with REGEX strong:g*123. This is needed because the search term submitted by the 'Find all occcurrences includes extra zeros
		// The capitalisation is not important since we do a case insensitive search
		if (searchTerms.contains("strong:")) {
			searchTerms = searchTerms.replaceAll("strong:g0*", "strong:g0*");
			searchTerms = searchTerms.replaceAll("strong:h0*", "strong:h0*");
		}
		return searchTerms;
	}

	private static String[] splitSearchTerms(String searchTerms) {
		// Split the search terms on space characters that are not enclosed in double quotes
		// Eg: 'moses "burning bush"' -> "moses" and "burning bush"
		return searchTerms.split("\\s+(?=(?:\"(?:\\\\\"|[^\"])+\"|[^\"])+$)");
	}

	private static String prepareSearchWord(String searchWord) {
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
}
