package net.bible.android.view.activity.navigation;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.ListActionModeHelper;
import net.bible.android.view.util.widget.TwoLineListItem;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.system.SystemKJV;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentItemAdapter extends ArrayAdapter<Book> {

	private int resource;
	
	private final ListActionModeHelper.ActionModeActivity actionModeActivity;

	private static int ACTIVATED_COLOUR = CommonUtils.getResourceColor(R.color.list_item_activated);

	public DocumentItemAdapter(Context _context, int _resource, List<Book> _items, ListActionModeHelper.ActionModeActivity actionModeActivity) {
		super(_context, _resource, _items);
		resource = _resource;
		this.actionModeActivity = actionModeActivity;
	}

	@Override
	public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

		Book document = getItem(position);

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
			// eBible repo uses abbreviation for initials and initials now contains the repo name!!!
			// but helpfully JSword uses initials if abbreviation does not exist, as will be the case for all other repos.
			String initials = document.getAbbreviation();
			view.getText1().setText(initials);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String name = document.getName();
			if (document instanceof AbstractPassageBook) {
				final AbstractPassageBook bible = (AbstractPassageBook)document;
				// display v11n name if not KJV
				if (!SystemKJV.V11N_NAME.equals(bible.getVersification().getName())) {
					name += " ("+bible.getVersification().getName()+")";
				}
			}
			view.getText2().setText(name);
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			if (actionModeActivity.isItemChecked(position)) {
				view.setBackgroundColor(ACTIVATED_COLOUR);
			} else {
				view.setBackgroundColor(Color.TRANSPARENT);
			}
		}

		return view;
	}
}