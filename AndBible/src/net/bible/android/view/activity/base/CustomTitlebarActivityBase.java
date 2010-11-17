package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class CustomTitlebarActivityBase extends ActivityBase {

	private TextView mTitle;
	private ProgressBar mProgressBarIndeterminate;
	private Button mQuickBibleChangeLink;
	private Book mSuggestedBible;
	private Button mQuickCommentaryChangeLink;
	private Book mSuggestedCommentary;
	
	//*** custom title bar code
    @Override
	public void setContentView(int layoutResID) {
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        
		super.setContentView(layoutResID);
		
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar);
        mTitle = (TextView)findViewById(R.id.title);
        mProgressBarIndeterminate = (ProgressBar)findViewById(R.id.progressCircular);

        mQuickBibleChangeLink = (Button)findViewById(R.id.quickBibleChange);
        mQuickBibleChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedBible);
            }
        });
        
        mQuickCommentaryChangeLink = (Button)findViewById(R.id.quickCommentaryChange);
        mQuickCommentaryChangeLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedCommentary);
            }
        });
        
        updateSuggestedDocuments();
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
	@Override
	public void setTitle(CharSequence title) {
		mTitle.setText(title);
	}
	
	public void setProgressBar(boolean on) {
		mProgressBarIndeterminate.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
	}
}
