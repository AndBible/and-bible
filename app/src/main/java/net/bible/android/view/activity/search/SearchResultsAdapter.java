package net.bible.android.view.activity.search;

import android.content.Context;
import android.text.Html;
import android.text.SpannableString;
import android.util.TypedValue;
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

	private void scaleTextView(TextView textView, Float scale) {
		if (textView.getTag()==null) textView.setTag(Float.valueOf(textView.getTextSize()));
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (Float) textView.getTag() * scale);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		SearchResultsData resultData=arrayList.get(position);
		Float scaleText = 1.2F; // TODO: I would like to add an application preference that allows the user to set the size of their results list
		if(convertView==null) {

			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView=inflater.inflate(R.layout.search_results_statistics_row_verse, null);

			TextView reference=convertView.findViewById(R.id.reference);
			reference.setText(resultData.reference);
			scaleTextView(reference, scaleText);

			TextView translation=convertView.findViewById(R.id.translation);
			translation.setText(resultData.translation);
			scaleTextView(translation, scaleText);

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
				scaleTextView(verse, scaleText);

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
