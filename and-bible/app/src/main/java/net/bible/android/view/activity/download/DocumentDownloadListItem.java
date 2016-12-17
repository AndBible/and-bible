package net.bible.android.view.activity.download;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import net.bible.android.activity.R;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.util.widget.TwoLineListItem;

import org.crosswire.jsword.book.Book;

/** Add an image to the normal 2 line list item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentDownloadListItem extends TwoLineListItem {

	/** document being shown */
	private Book document;

	private ImageView mIcon;

	private ProgressBar progressBar;
	
	public DocumentDownloadListItem(Context context) {
		super(context);
	}

	public DocumentDownloadListItem(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public DocumentDownloadListItem(Context context, AttributeSet attrs,
									int defStyle) {
		super(context, attrs, defStyle);
	}

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIcon = (ImageView) findViewById(R.id.icon);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
    }

	/**
	 * Should not need to check the initials but other items were being updated and I don't know why
	 */
	public void setProgressPercent(String initials, int percentDone) {
		if (progressBar != null && progressBar.getParent() != null && initials.equals(document.getInitials())) {
			progressBar.setProgress(percentDone);
			if (percentDone > 0 && percentDone < 100) {
				updateControlState(DownloadControl.BookInstallStatus.BEING_INSTALLED);
			} else if (percentDone==100) {
				// final percent update during install automatically hides progress bar and changes icon to tick
				updateControlState(DownloadControl.BookInstallStatus.INSTALLED);
			}
		}
	}

	public void updateControlState(DownloadControl.BookInstallStatus bookInstallStatus) {
		if (getIcon()!=null && getProgressBar()!=null) {
			switch (bookInstallStatus) {
				case INSTALLED:
					getIcon().setImageResource(R.drawable.ic_check_green_24dp);
					progressBar.setVisibility(View.INVISIBLE);
					break;
				case NOT_INSTALLED:
					getIcon().setImageDrawable(null);
					progressBar.setVisibility(View.INVISIBLE);
					break;
				case BEING_INSTALLED:
					getIcon().setImageResource(R.drawable.ic_arrow_downward_green_24dp);
					progressBar.setVisibility(View.VISIBLE);
					break;
				case UPGRADE_AVAILABLE:
					getIcon().setImageResource(R.drawable.amber_up_arrow);
					progressBar.setVisibility(View.INVISIBLE);
					break;
			}
		}
	}

	public ImageView getIcon() {
		return mIcon;
	}

	public void setIcon(ImageView icon) {
		mIcon = icon;
	}

	public ProgressBar getProgressBar() {
		return progressBar;
	}

	public Book getDocument() {
		return document;
	}

	public void setDocument(Book document) {
		this.document = document;
	}
}
