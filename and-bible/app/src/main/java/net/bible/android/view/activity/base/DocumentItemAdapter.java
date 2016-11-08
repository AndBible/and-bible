package net.bible.android.view.activity.base;

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
import net.bible.android.control.ControlFactory;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.util.widget.DocumentListItem;
import net.bible.service.common.CommonUtils;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;
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

	private final boolean isProgressBarShown;

	private DownloadControl downloadControl = ControlFactory.getInstance().getDownloadControl();

	private DocumentDownloadProgressCache documentDownloadProgressCache;

	private final ListActionModeHelper.ActionModeActivity actionModeActivity;

	private static int ACTIVATED_COLOUR = CommonUtils.getResourceColor(R.color.list_item_activated);

	public DocumentItemAdapter(Context _context, int _resource, List<Book> _items, boolean isInstallStatusItemsShown, boolean isProgressBarShown, ListActionModeHelper.ActionModeActivity actionModeActivity) {
		super(_context, _resource, _items);
		resource = _resource;
		this.isInstallStatusItemsShown = isInstallStatusItemsShown;
		this.isProgressBarShown = isProgressBarShown;
		this.actionModeActivity = actionModeActivity;

		if (isProgressBarShown) {
			documentDownloadProgressCache = new DocumentDownloadProgressCache();

			//TODO must unregister when view ends
			// listen for Progress changes and call the above Runnable to update the ui
			JobManager.addWorkListener( new WorkListener() {
				@Override
				public void workProgressed(WorkEvent ev) {
					updateProgress(ev);
				}

				@Override
				public void workStateChanged(WorkEvent ev) {
					updateProgress(ev);
				}

				private void updateProgress(WorkEvent ev) {
					Progress prog = ev.getJob();
					documentDownloadProgressCache.updateProgress(prog);
				}
			});
		}
	}

	@Override
	public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

		Book document = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		DocumentListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (DocumentListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (DocumentListItem) convertView;
			if (isProgressBarShown) {
				//  Get previous convertView doc to uncache
				documentDownloadProgressCache.documentHidden(view.getDocument());
			}
		}

		// remember which item is being shown
		view.setDocument(document);
		if (isProgressBarShown) {
			documentDownloadProgressCache.documentShown(document, view.getProgressBar());
		}

		if (view.getIcon() != null) {
			// Only Download screen shows progress bar
			view.getProgressBar().setVisibility(isProgressBarShown? View.VISIBLE : View.GONE);

			if (isInstallStatusItemsShown) {
				switch (downloadControl.getBookInstallStatus(document)) {
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