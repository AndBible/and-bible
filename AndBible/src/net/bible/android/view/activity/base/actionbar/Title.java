package net.bible.android.view.activity.base.actionbar;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

/** 
 * Show current verse/key and document on left of actionBar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class Title {

	private ActionBar actionBar;
	private Activity activity;
	
	private TextView documentTitle;
	private TextView documentSubtitle;
	private TextView pageTitle;
	private TextView pageSubtitle;
	
	private static final TitleSplitter titleSplitter = new TitleSplitter();

	abstract protected String getDocumentTitle();
	abstract protected String getPageTitle();
	abstract protected void onDocumentTitleClick();
	abstract protected void onPageTitleClick();
	
	public void addToBar(ActionBar actionBar, final Activity activity) {
		this.actionBar = actionBar;
		this.activity = activity;

		actionBar.setCustomView(R.layout.title);
		
	    documentTitle = (TextView) actionBar.getCustomView().findViewById(R.id.documentTitle);
	    documentSubtitle = (TextView) actionBar.getCustomView().findViewById(R.id.documentSubtitle);
	    pageTitle = (TextView) actionBar.getCustomView().findViewById(R.id.pageTitle);
	    pageSubtitle = (TextView) actionBar.getCustomView().findViewById(R.id.pageSubtitle);

	    // clicking document title shows document selector
	    ViewGroup documentTitleContainer = (ViewGroup) actionBar.getCustomView().findViewById(R.id.documentTitleContainer);
	    documentTitleContainer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onDocumentTitleClick();
			}
	    });

	    // clicking page title shows appropriate key selector
	    ViewGroup pageTitleContainer = (ViewGroup) actionBar.getCustomView().findViewById(R.id.pageTitleContainer);
	    pageTitleContainer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onPageTitleClick();
			}
	    });

	    actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME);

	    update(true);
		
		// do not display the app icon in the actionbar
		actionBar.setDisplayShowHomeEnabled(false);
	}
	
	public void update() {
		// update everything if called externally
		update(true);
	}
	

	protected void update(final boolean everything) {
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (actionBar!=null) {
					// always update verse number
					String[] pageParts = getPageTitleParts();
					if (pageParts.length>0) pageTitle.setText(pageParts[0]);
					if (pageParts.length>1) {
						pageSubtitle.setText(pageParts[1]);
					}
					pageSubtitle.setVisibility(pageParts.length>1? View.VISIBLE : View.GONE);
					// don't always need to redisplay document name
					if (everything) {
						String[] documentParts = getDocumentTitleParts();
						if (documentParts.length>0) documentTitle.setText(documentParts[0]);
						if (documentParts.length>1) documentSubtitle.setText(documentParts[1]);
						documentSubtitle.setVisibility(documentParts.length>1? View.VISIBLE : View.GONE);
					}
				}
			}
		});
	}

	protected Activity getActivity() {
		return activity;
	}
	
	private String[] getPageTitleParts() {
		return titleSplitter.split(getPageTitle());
	}

	private String[] getDocumentTitleParts() {
		return titleSplitter.split(getDocumentTitle());
	}
}
