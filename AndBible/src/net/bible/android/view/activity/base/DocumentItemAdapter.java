package net.bible.android.view.activity.base;

import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.util.widget.TwoLineListItemWithImage;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * @author denha1m
 *
 */
public class DocumentItemAdapter extends ArrayAdapter<Book> {

	private int resource;
	
	private boolean isInstallStatusItemsShown;
	
	private DownloadControl downloadControl = ControlFactory.getInstance().getDownloadControl();

	public DocumentItemAdapter(Context _context, int _resource, List<Book> _items, boolean isInstallStatusItemsShown) {
		super(_context, _resource, _items);
		resource = _resource;
		this.isInstallStatusItemsShown = isInstallStatusItemsShown;
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
			String initials = item.getInitials();
			view.getText1().setText(initials);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String name = item.getName();
			if (item instanceof AbstractPassageBook) {
				name += "("+((AbstractPassageBook)item).getVersification().getName()+")";
			}
			view.getText2().setText(name);
		}

		return view;
	}
}