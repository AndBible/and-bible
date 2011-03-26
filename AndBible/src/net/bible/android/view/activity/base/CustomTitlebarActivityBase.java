package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.navigation.ChooseDocument;

import org.crosswire.jsword.book.Book;

import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;

public class CustomTitlebarActivityBase extends ActivityBase {

	private Button mDocumentTitleLink;
	private Button mPageTitleLink;
	private ProgressBar mProgressBarIndeterminate;
	private Button mQuickBibleChangeLink;
	private Book mSuggestedBible;
	private Button mQuickCommentaryChangeLink;
	private Book mSuggestedCommentary;
	
	private static final String TAG = "CustomTitlebarActivityBase";
	
	/** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
	 * and set the new titlebar layout just after
	 */
    @Override
	public void setContentView(int layoutResID) {
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		super.setContentView(layoutResID);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar);

        mDocumentTitleLink = (Button)findViewById(R.id.titleDocument);
        mPageTitleLink = (Button)findViewById(R.id.titlePassage);
        mProgressBarIndeterminate = (ProgressBar)findViewById(R.id.progressCircular);
        mQuickBibleChangeLink = (Button)findViewById(R.id.quickBibleChange);
        mQuickCommentaryChangeLink = (Button)findViewById(R.id.quickCommentaryChange);

        mDocumentTitleLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	Intent handlerIntent = new Intent(CustomTitlebarActivityBase.this, ChooseDocument.class);
            	startActivityForResult(handlerIntent, 1);
            }
        });

        mPageTitleLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	Intent handlerIntent = new Intent(CustomTitlebarActivityBase.this, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
            	startActivityForResult(handlerIntent, 1);
            }
        });

        mQuickBibleChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedBible);
            }
        });
        
        mQuickCommentaryChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedCommentary);
            }
        });
    }

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		// page title varies in length depending on orientation so need to redisplay it on rotation
		setPageTitle(ControlFactory.getInstance().getPageControl().getCurrentPageTitle());
		
//		// the title bar has different widths depending on the orientation
//		int titleBarTitleWidthPixels = getResources().getDimensionPixelSize(R.dimen.title_bar_title_width);
//		Log.d(TAG, "Title bar width:"+titleBarTitleWidthPixels);
//		mPageTitle.setWidth(titleBarTitleWidthPixels);
	}

	/** update the quick links in the title bar
     */
    public void updateSuggestedDocuments() {
    	
        mSuggestedBible = ControlFactory.getInstance().getDocumentControl().getSuggestedBible();
        if (mSuggestedBible!=null) {
        	mQuickBibleChangeLink.setText(mSuggestedBible.getInitials());
        	mQuickBibleChangeLink.setVisibility(View.VISIBLE);
        } else {
        	mQuickBibleChangeLink.setVisibility(View.INVISIBLE);
        }
    	
        mSuggestedCommentary = ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();
        if (mSuggestedCommentary!=null) {
        	mQuickCommentaryChangeLink.setText(mSuggestedCommentary.getInitials());
        	mQuickCommentaryChangeLink.setVisibility(View.VISIBLE);
        } else {
        	mQuickCommentaryChangeLink.setVisibility(View.INVISIBLE);
        }
    }

    /** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setPageTitleVisible(boolean show) {
		mPageTitleLink.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
	}

	/** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setPageTitle(CharSequence title) {
		mPageTitleLink.setText(title);
	}
	
    /** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setDocumentTitle(CharSequence title) {
		mDocumentTitleLink.setText(title);
	}

	public void setProgressBar(boolean on) {
		mProgressBarIndeterminate.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
	}
}
