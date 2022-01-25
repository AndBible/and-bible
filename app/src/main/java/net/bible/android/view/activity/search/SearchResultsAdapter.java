package net.bible.android.view.activity.search;

import android.content.Context;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;

import java.util.ArrayList;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;

public class SearchResultsAdapter extends ArrayAdapter<SearchResultsData> {

	private int resource;
	private ArrayList<SearchResultsData> arrayList;
	private Context context;

	public SearchResultsAdapter(Context _context, int _resource, ArrayList<SearchResultsData> arrayList) {
		super(_context, _resource, arrayList);
		this.resource = _resource;
		this.arrayList=arrayList;
		this.context=_context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		SearchResultsData resultData=arrayList.get(position);
		if(convertView==null) {

			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView=inflater.inflate(R.layout.search_results_statistics_row_verse, null);

			TextView reference=convertView.findViewById(R.id.reference);
			reference.setText(resultData.reference);

			TextView translation=convertView.findViewById(R.id.translation);
			translation.setText(resultData.translation);

			// Get the text of the verse
			Book book = Books.installed().getBook(resultData.translation);
			try {
				Key key = book.getKey(resultData.osisKey);

				String verseHtml = resultData.verseHtml.trim();

				// The 'toHtml' method wraps the string in <p> which puts a large margin at the bottom of the verse. Need to remove this.
				try{
					verseHtml= replaceString(verseHtml,"<p dir=\"ltr\">", "");
					verseHtml= replaceString(verseHtml,"</p>", "");
				}catch (Exception e) {}

				SpannableString verseTextHtml =  new SpannableString(Html.fromHtml(verseHtml));

				TextView verse=convertView.findViewById(R.id.verse);
				verse.setText(verseTextHtml);

			} catch (NoSuchKeyException e) {
				e.printStackTrace();
			}
		}
		return convertView;
	}

	private String replaceString(String initialString, String first, String second)
	{
		StringBuilder b = new StringBuilder(initialString);
		b.replace(initialString.lastIndexOf(first), initialString.lastIndexOf(first)+first.length(),second );
		return b.toString();
	}

	@Override
	public int getItemViewType(int position) {
		return position;
	}
	@Override
	public int getViewTypeCount() {
		if(getCount()<1) return 1;
		return getCount();
	}
	@Override
	public boolean isEmpty() {
		return false;
	}


}
