package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.navigation.ChooseDocument;

import org.crosswire.jsword.book.Book;

import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;

public class CustomTitlebarActivityBase extends ActivityBase {

	private View mTitleBar;
	
	private Button mDocumentTitleLink;
	private Button mPageTitleLink;
	private ProgressBar mProgressBarIndeterminate;
	private Button mQuickBibleChangeLink;
	private Book mSuggestedBible;
	private Button mQuickCommentaryChangeLink;
	private Book mSuggestedCommentary;
	private Button mQuickDictionaryChangeLink;
	private Book mSuggestedDictionary;
	private Button mQuickGenBookChangeLink;
	private Book mSuggestedGenBook;
	
	private View mContentView;
	
	private static final String TAG = "CustomTitlebarActivityBase";
	
	/** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
	 * and set the new titlebar layout just after
	 */
    @Override
	public void setContentView(int layoutResID) {
    	requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.setContentView(layoutResID);

        mTitleBar = findViewById(R.id.titleBar);
        mContentView = mTitleBar.getRootView();
        
        mDocumentTitleLink = (Button)findViewById(R.id.titleDocument);
        mPageTitleLink = (Button)findViewById(R.id.titlePassage);
        mProgressBarIndeterminate = (ProgressBar)findViewById(R.id.progressCircular);
        mQuickBibleChangeLink = (Button)findViewById(R.id.quickBibleChange);
        mQuickCommentaryChangeLink = (Button)findViewById(R.id.quickCommentaryChange);
        mQuickDictionaryChangeLink = (Button)findViewById(R.id.quickDictionaryChange);
        mQuickGenBookChangeLink = (Button)findViewById(R.id.quickGenBookChange);

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

        mQuickDictionaryChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDictionary);
            }
        });
        
        mQuickGenBookChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedGenBook);
            }
        });

    }
    
    public void toggleFullScreen() {
    	super.toggleFullScreen();
    	
    	if (!isFullScreen()) {
    		Log.d(TAG, "Showing title bar");
    		mTitleBar.setVisibility(View.VISIBLE);
    	} else {
    		Log.d(TAG, "Hiding title bar");
    		mTitleBar.setVisibility(View.GONE);
    	}

    	mContentView.requestLayout();
    }
    


    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		// page title varies in length depending on orientation so need to redisplay it on rotation
		setPageTitle(ControlFactory.getInstance().getPageControl().getCurrentPageTitle());

		// show or hide right 2 buttons depending on screen width and book availability
		if (showAllButtons()) {
			if (mSuggestedDictionary!=null) {
				mQuickDictionaryChangeLink.setVisibility(View.VISIBLE);
			}
			if (mSuggestedGenBook!=null) {
				mQuickGenBookChangeLink.setVisibility(View.VISIBLE);
			}
		} else {
			mQuickDictionaryChangeLink.setVisibility(View.GONE);
			mQuickGenBookChangeLink.setVisibility(View.GONE);
		}
		
//		// the title bar has different widths depending on the orientation
//		int titleBarTitleWidthPixels = getResources().getDimensionPixelSize(R.dimen.title_bar_title_width);
//		Log.d(TAG, "Title bar width:"+titleBarTitleWidthPixels);
//		mPageTitle.setWidth(titleBarTitleWidthPixels);
	}

    /** number of buttons varies depending on screen size and orientation
     */
    private boolean showAllButtons() {
    	return 4 <= getResources().getInteger(R.integer.number_of_quick_buttons);
    }
	/** update the quick links in the title bar
     */
    public void updateSuggestedDocuments() {
    	
        mSuggestedBible = ControlFactory.getInstance().getDocumentControl().getSuggestedBible();
        updateQuickButton(mSuggestedBible, mQuickBibleChangeLink, true);
    	
        mSuggestedCommentary = ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();
        updateQuickButton(mSuggestedCommentary, mQuickCommentaryChangeLink, true);

        boolean canShow = showAllButtons();
        mSuggestedDictionary = ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();
        updateQuickButton(mSuggestedDictionary, mQuickDictionaryChangeLink, canShow);

        mSuggestedGenBook = ControlFactory.getInstance().getDocumentControl().getSuggestedGenBook();
        updateQuickButton(mSuggestedGenBook, mQuickGenBookChangeLink, canShow);
    }

	private void updateQuickButton(Book suggestedBook, Button quickButton, boolean canShow) {
		if (suggestedBook!=null) {
        	quickButton.setText(suggestedBook.getInitials());
        	if (canShow) {
        		quickButton.setVisibility(View.VISIBLE);
        	}
        } else {
        	quickButton.setVisibility(View.GONE);
        }
	}

    /** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setPageTitleVisible(boolean show) {
		mPageTitleLink.setVisibility(show ? View.VISIBLE : View.GONE);
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
		mProgressBarIndeterminate.setVisibility(on ? View.VISIBLE : View.GONE);
	}
}
