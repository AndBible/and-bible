package net.bible.android.view.activity.download;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import net.bible.android.activity.R;
import net.bible.android.control.download.DocumentStatus;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent;
import net.bible.android.view.util.widget.TwoLineListItem;

import org.crosswire.jsword.book.Book;

import de.greenrobot.event.EventBus;

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

		ensureRegisteredForDownloadEvents();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		// View is now detached, and about to be destroyed.
		// de-register from EventBus
		EventBus.getDefault().unregister(this);
	}

	/**
	 * Download progress event
	 */
	public void onEventMainThread(DocumentDownloadEvent event) {
		if (document!=null && event.getInitials().equals(document.getInitials())) {
			updateControlState(event.getDocumentStatus());
		}
	}

	public void updateControlState(DocumentStatus documentStatus) {
		if (getIcon()!=null && getProgressBar()!=null) {
			switch (documentStatus.getDocumentInstallStatus()) {
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
					setProgressPercent(documentStatus.getPercentDone());
					progressBar.setVisibility(View.VISIBLE);
					break;
				case UPGRADE_AVAILABLE:
					getIcon().setImageResource(R.drawable.ic_arrow_upward_amber_24dp);
					progressBar.setVisibility(View.INVISIBLE);
					break;
				case ERROR_DOWNLOADING:
					getIcon().setImageResource(R.drawable.ic_warning_red_24dp);
					progressBar.setVisibility(View.INVISIBLE);
					break;
			}
		}
	}

	/**
	 * Should not need to check the initials but other items were being updated and I don't know why
	 */
	private void setProgressPercent(int percentDone) {
		if (progressBar != null) {
			progressBar.setProgress(percentDone);
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

		ensureRegisteredForDownloadEvents();
	}

	/**
	 * Items are detached more often than inflated so always have to check the item is registered for download events.
	 * https://code.google.com/p/android/issues/detail?id=65617
	 */
	private void ensureRegisteredForDownloadEvents() {
		ABEventBus.getDefault().safelyRegister(this);
	}
}
