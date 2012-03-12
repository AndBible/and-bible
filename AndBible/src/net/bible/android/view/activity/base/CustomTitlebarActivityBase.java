package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.navigation.ChooseDocument;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;

import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

public abstract class CustomTitlebarActivityBase extends ActivityBase {
	
	protected enum HeaderButton {DOCUMENT, PAGE, BIBLE, COMMENTARY, DICTIONARY, GEN_BOOK, MAP, TOGGLE_STRONGS};

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
	private Button mQuickMapChangeLink;
	private Book mSuggestedMap;
	
	private ToggleButton mStrongsToggle;
	
	private View mContentView;
	
	private static final String TAG = "CustomTitlebarActivityBase";

	// called whenever something like strong preferences have been changed by the user.  Should refresh the screen
	protected abstract void preferenceSettingsChanged();
	
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
        mQuickMapChangeLink = (Button)findViewById(R.id.quickMapChange);
        
        mStrongsToggle = (ToggleButton)findViewById(R.id.strongsToggle);
        
        mDocumentTitleLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.DOCUMENT);
            }
        });

        mPageTitleLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.PAGE);
            }
        });

        mQuickBibleChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.BIBLE);
            }
        });
        
        mQuickCommentaryChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.COMMENTARY);
            }
        });

        mQuickDictionaryChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.DICTIONARY);
            }
        });
        
        mQuickGenBookChangeLink.setOnClickListener(new OnClickListener() {
			@Override
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.GEN_BOOK);
            }
        });
        
        mQuickMapChangeLink.setOnClickListener(new OnClickListener() {
			@Override
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.MAP);
            }
        });

        mStrongsToggle.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.TOGGLE_STRONGS);
			}
		});
    }
    
    /** Central method to initiate handling of header button presses
     *  Also allows subclasses to know when a button has been pressed
     * 
     * @param buttonType
     */
    protected void handleHeaderButtonPress(HeaderButton buttonType) {
    	try {
	    	switch (buttonType) {
	    	case BIBLE:
	    		quickChange(mSuggestedBible);
	    		break;
	    	case COMMENTARY:
	    		quickChange(mSuggestedCommentary);
	    		break;
	    	case DICTIONARY:
	    		quickChange(mSuggestedDictionary);
	    		break;
	    	case GEN_BOOK:
	    		quickChange(mSuggestedGenBook);
	    		break;
	    	case MAP:
	    		quickChange(mSuggestedMap);
	    		break;
	    	case DOCUMENT:
	        	Intent docHandlerIntent = new Intent(CustomTitlebarActivityBase.this, ChooseDocument.class);
	        	startActivityForResult(docHandlerIntent, 1);
	    		break;
	    	case PAGE:
	        	Intent pageHandlerIntent = new Intent(CustomTitlebarActivityBase.this, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
	        	startActivityForResult(pageHandlerIntent, 1);
	    		break;
	    	case TOGGLE_STRONGS:
				// update the show-strongs pref setting according to the ToggleButton
				CommonUtils.getSharedPreferences().edit().putBoolean("show_strongs_pref", mStrongsToggle.isChecked()).commit();
				// redisplay the current page
				preferenceSettingsChanged();
	    		break;
	    	default:
	    		Log.e(TAG, "Unknown button pressed");
	    	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error pressing header button");
    		showErrorMsg(R.string.error_occurred);
    	}
    }
    
    private void quickChange(Book changeToBook) {
    	CurrentPageManager.getInstance().setCurrentDocument(changeToBook);
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
		
		updatePageTitle();

		// show or hide right 2 buttons depending on screen width and book availability
		if (numButtonsToShow()>=5) {
			if (mSuggestedDictionary!=null) {
				mQuickDictionaryChangeLink.setVisibility(View.VISIBLE);
			}
			if (mSuggestedGenBook!=null) {
				mQuickGenBookChangeLink.setVisibility(View.VISIBLE);
			}
			if (mSuggestedMap!=null) {
				mQuickMapChangeLink.setVisibility(View.VISIBLE);
			}
		} else {
			mQuickDictionaryChangeLink.setVisibility(View.GONE);
			mQuickGenBookChangeLink.setVisibility(View.GONE);
			mQuickMapChangeLink.setVisibility(View.GONE);
		}
		
//		// the title bar has different widths depending on the orientation
//		int titleBarTitleWidthPixels = getResources().getDimensionPixelSize(R.dimen.title_bar_title_width);
//		Log.d(TAG, "Title bar width:"+titleBarTitleWidthPixels);
//		mPageTitle.setWidth(titleBarTitleWidthPixels);
	}

	/** refresh page title which shows current verse for bibles and commentaries or chapter for books etc 
	 */
	protected void updatePageTitle() {
		// page title varies in length depending on orientation so need to redisplay it on rotation
		setPageTitle(ControlFactory.getInstance().getPageControl().getCurrentPageTitle());
	}

    /** number of buttons varies depending on screen size and orientation
     */
    private int numButtonsToShow() {
    	return getResources().getInteger(R.integer.number_of_quick_buttons);
    }
    
    protected Book getSuggestedDocument(HeaderButton buttonType) {
    	Book suggestedDoc = null;

    	switch (buttonType) {
    	case BIBLE:
    		suggestedDoc = ControlFactory.getInstance().getDocumentControl().getSuggestedBible();;
    		break;
    	case COMMENTARY:
    		suggestedDoc = ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();;
    		break;
    	case DICTIONARY:
    		suggestedDoc = ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();;
    		break;
    	case GEN_BOOK:
    		suggestedDoc = ControlFactory.getInstance().getDocumentControl().getSuggestedGenBook();;
    		break;
    	case MAP:
    		suggestedDoc = ControlFactory.getInstance().getDocumentControl().getSuggestedMap();;
    		break;
    	}
    	return suggestedDoc;
    }
    
	/** update the quick links in the title bar
     */
    public void updateSuggestedDocuments() {
        int numButtonsToShow = numButtonsToShow();
    	
        mSuggestedBible = getSuggestedDocument(HeaderButton.BIBLE); 
        updateQuickButton(mSuggestedBible, mQuickBibleChangeLink, true);
    	
        mSuggestedCommentary = getSuggestedDocument(HeaderButton.COMMENTARY);
        updateQuickButton(mSuggestedCommentary, mQuickCommentaryChangeLink, true);

        mSuggestedDictionary = getSuggestedDocument(HeaderButton.DICTIONARY);
        updateQuickButton(mSuggestedDictionary, mQuickDictionaryChangeLink, numButtonsToShow>=3);

        mSuggestedGenBook = getSuggestedDocument(HeaderButton.GEN_BOOK);
        updateQuickButton(mSuggestedGenBook, mQuickGenBookChangeLink, numButtonsToShow>=4);
        
        mSuggestedMap = getSuggestedDocument(HeaderButton.MAP);
        updateQuickButton(mSuggestedMap, mQuickMapChangeLink, numButtonsToShow>=5);

        boolean showStrongsToggle = isStrongsRelevant();
        mStrongsToggle.setVisibility(showStrongsToggle? View.VISIBLE : View.GONE);
        if (showStrongsToggle) {
	        boolean isShowstrongs = CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	        mStrongsToggle.setChecked(isShowstrongs);
        }
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

	/** return true if Strongs numbers are shown */
	public boolean isStrongsShown() {
		return isStrongsRelevant() && 
			   CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	public boolean isStrongsRelevant() {
		return ControlFactory.getInstance().getDocumentControl().isStrongsInBook();
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
