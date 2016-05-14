package net.bible.android.view.activity.base;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.util.widget.TwoLineListItemWithImage;
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
	
	private boolean isInstallStatusItemsShown;
	
	private DownloadControl downloadControl = ControlFactory.getInstance().getDownloadControl();

	private final ListActionModeHelper.ActionModeActivity actionModeActivity;

	private static int ACTIVATED_COLOUR = CommonUtils.getResourceColor(R.color.list_item_activated);

	public DocumentItemAdapter(Context _context, int _resource, List<Book> _items, boolean isInstallStatusItemsShown, ListActionModeHelper.ActionModeActivity actionModeActivity) {
		super(_context, _resource, _items);
		resource = _resource;
		this.isInstallStatusItemsShown = isInstallStatusItemsShown;
		this.actionModeActivity = actionModeActivity;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Book item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TwoLineListItemWithImage view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TwoLineListItemWithImage) inflater.inflate(resource, parent, false);
		} else {
			view = (TwoLineListItemWithImage) convertView;
		}

		if (view.getIcon() != null) {
			if (isInstallStatusItemsShown) {
				switch (downloadControl.getBookInstallStatus(item)) {
				case INSTALLED:
					view.getIcon().setImageResource(R.drawable.btn_check_buttonless_on);
					break;
				case NOT_INSTALLED:
					view.getIcon().setImageResource(R.drawable.btn_check_buttonless_off);
					break;
				case BEING_INSTALLED:
					view.getIcon().setImageResource(R.drawable.btn_check_buttonless_on);
					break;
				case UPGRADE_AVAILABLE:
					view.getIcon().setImageResource(R.drawable.amber_up_arrow);
					break;
				}
			} else {
				view.getIcon().setVisibility(View.GONE);
			}
		}
		
		// Set value for the first text field
		if (view.getText1() != null) {
			// eBible repo uses abbreviation for initials and initials now contains the repo name!!!
			// but helpfully JSword uses initials if abbreviation does not exist, as will be the case for all other repos.
			String initials = item.getAbbreviation();
			view.getText1().setText(initials);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String name = item.getName();
			if (item instanceof AbstractPassageBook) {
				final AbstractPassageBook bible = (AbstractPassageBook)item;
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