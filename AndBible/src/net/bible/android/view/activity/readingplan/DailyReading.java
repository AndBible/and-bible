package net.bible.android.view.activity.readingplan;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.readingplan.ReadingStatus;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.readingplan.OneDaysReadingsDto;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

/** Allow user to enter search criteria
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DailyReading extends CustomTitlebarActivityBase {
	
	private static final String TAG = "DailyReading";
	
	private TextView mDescriptionView;
	private TextView mDayView;
	private TextView mDateView;
//	private TextView mStatusMsgView; //unused
	private List<ImageTickOnOff> mImageTickOnOffList;
	private Button mDoneButton;
	
	private int mDay;
	public static final String DAY = "day";
	
	private OneDaysReadingsDto mReadings;

	// user has not done any readings since navigating here
	private boolean mIsUserJustEntered = true;
	
	private ReadingPlanControl mReadingPlanControl = ControlFactory.getInstance().getReadingPlanControl();
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying one day reading plan");
        setContentView(R.layout.reading_plan_one_day);
        
        try {
        	mIsUserJustEntered = true;
        	
			// may not be for current day if user presses forward or backward
	        mDay = mReadingPlanControl.getCurrentPlanDay();
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				mDay = extras.getInt(DAY, mDay);
			}
	
	        mReadings = mReadingPlanControl.getDaysReading(mDay);
	        
	        mDescriptionView =  (TextView)findViewById(R.id.description);
	        mDescriptionView.setText(mReadings.getReadingPlanInfo().getDescription());
	
	        // date display
	        mDayView =  (TextView)findViewById(R.id.day);
	        mDayView.setText(mReadings.getDayDesc());
	        mDateView =  (TextView)findViewById(R.id.date);
	        mDateView.setText(mReadings.getReadingDateString());
	
	        mDoneButton = (Button)findViewById(R.id.doneButton);
//	        mStatusMsgView =  (TextView)findViewById(R.id.status_message);
	        
	        mImageTickOnOffList = new ArrayList<ImageTickOnOff>();
	        
	        TableLayout layout = (TableLayout)findViewById(R.id.reading_container);
	        for (int i=0; i<mReadings.getNumReadings(); i++) {
	        	final int readingNo = i;
	            View child = getLayoutInflater().inflate(R.layout.reading_plan_one_reading, null);
	
	            // Ticks
	            ImageTickOnOff imageTickOnOff = new ImageTickOnOff();
	            imageTickOnOff.unticked = (ImageView)child.findViewById(R.id.tick_off);
	            imageTickOnOff.ticked = (ImageView)child.findViewById(R.id.tick_on);
	            mImageTickOnOffList.add(imageTickOnOff);
	            
	            // Passage description
	            TextView rdgText = (TextView)child.findViewById(R.id.passage);
	            rdgText.setText(mReadings.getReadingKey(readingNo).getName());

	            // handle read button clicks
	            Button readBtn = (Button)child.findViewById(R.id.readButton);
	            readBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						onRead(readingNo);
					}
				});

	            // handle speak button clicks
	            Button speakBtn = (Button)child.findViewById(R.id.speakButton);
	            speakBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						onSpeak(readingNo);
					}
				});
	            
	            layout.addView(child, readingNo);
	        }
	
	        updateTicksAndDone();
	        
	        // All
	        View child = getLayoutInflater().inflate(R.layout.reading_plan_one_reading, null);
	
	        // hide the tick
	        ImageView tick = (ImageView)child.findViewById(R.id.tick_off);
	        tick.setVisibility(View.INVISIBLE);
	        
	        // Passage description
	        TextView rdgText = (TextView)child.findViewById(R.id.passage);
	        rdgText.setText(getResources().getString(R.string.all));
	
	        Button passageBtn = (Button)child.findViewById(R.id.readButton);
	        passageBtn.setVisibility(View.INVISIBLE);
	        
	        Button speakBtn = (Button)child.findViewById(R.id.speakButton);
	        speakBtn.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					onSpeakAll(null);
				}
			});
	        layout.addView(child, mReadings.getNumReadings());
	        // end All
	        
	        // show reading plan and current day in titlebar
	        updatePageTitle();
	        
	        Log.d(TAG, "Finished displaying Reading view");
        } catch (Exception e) {
        	Log.e(TAG, "Error showing daily readings", e);
        	Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
        }
    }
    
    /** user pressed read button by 1 reading
	 */
    public void onRead(int readingNo) {
    	Log.i(TAG, "Read "+readingNo);
    	Key readingKey = mReadings.getReadingKey(readingNo);
    	mReadingPlanControl.read(mDay, readingNo, readingKey);
    	
    	finish();
    }

    /** user pressed speak button by 1 reading
	 */
    public void onSpeak(int readingNo) {
    	Log.i(TAG, "Speak "+readingNo);
    	Key readingKey = mReadings.getReadingKey(readingNo);
    	mReadingPlanControl.speak(mDay, readingNo, readingKey);
    	
    	updateTicksAndDone();
    }
    /** user pressed speak button by All
	 */
    public void onSpeakAll(View view) {
    	Log.i(TAG, "Speak all");
    	mReadingPlanControl.speak(mDay, mReadings.getReadingKeys());
    	
    	updateTicksAndDone();
    }

    public void onNext(View view) {
    	Log.i(TAG, "Next");
    	if (mDay<mReadings.getReadingPlanInfo().getNumberOfPlanDays()) {
	    	Intent handlerIntent = new Intent(this, DailyReading.class);
	    	handlerIntent.putExtra(DAY, mDay+1);
	    	startActivity(handlerIntent);
	    	finish();
    	}
    }

    public void onPrevious(View view) {
    	Log.i(TAG, "Previous");
    	if (mDay>1) {
	    	Intent handlerIntent = new Intent(this, DailyReading.class);
	    	handlerIntent.putExtra(DAY, mDay-1);
	    	startActivity(handlerIntent);
	    	finish();
    	}
    }
    
    public void onDone(View view) {
    	Log.i(TAG, "Done");
    	// all readings must be ticked for this to be enabled
    	//TODO mark readings complete
    	mReadingPlanControl.done(mReadings.getReadingPlanInfo(), mDay);
    	//if user has just entered then go to next days readings
    	if (mIsUserJustEntered) {
    		onNext(null);
    	} else {
    		// looks like user has actively read today's readings and just finished
        	finish();
    	}
    }

    /** I don't think this is used because of hte finish() in onSearch()
     */
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToPreviousScreen();
    	}
    }

	private void updateTicksAndDone() {
		ReadingStatus status = mReadingPlanControl.getReadingStatus(mDay);
		
		for (int i=0; i<mImageTickOnOffList.size(); i++) {
			ImageTickOnOff imageTickOnOff = mImageTickOnOffList.get(i);
			if (status.isRead(i)) {
				imageTickOnOff.ticked.setVisibility(View.VISIBLE);
				imageTickOnOff.unticked.setVisibility(View.GONE);
			} else {
				imageTickOnOff.ticked.setVisibility(View.GONE);
				imageTickOnOff.unticked.setVisibility(View.VISIBLE);
			}
		}
		
		mDoneButton.setEnabled(status.isAllRead());
	}
	
	private static class ImageTickOnOff {
		private ImageView unticked;
		private ImageView ticked;
	}

    /** Override Doc & Page header buttons to show reading plans and days lists.
     *  If any of the other buttons are pressed then finish and allow switch back to standard WebView
     * 
     * @param buttonType
     */
    protected void handleHeaderButtonPress(HeaderButton buttonType) {
    	switch (buttonType) {
    	case DOCUMENT:
        	Intent docHandlerIntent = new Intent(this, ReadingPlanSelectorList.class);
        	startActivityForResult(docHandlerIntent, 1);
        	finish();
    		break;
    	case PAGE:
        	Intent pageHandlerIntent = new Intent(this, DailyReadingList.class);
        	startActivityForResult(pageHandlerIntent, 1);
        	finish();
    		break;
    	default:
    		super.handleHeaderButtonPress(buttonType);
    		finish();
    	}
    }

    /** need to switch to current doc instead of next doc if the type is currently shown in WebView
     //TODO Flagging all doc types as not shown would be more elegant.
     */
    @Override
    protected Book getSuggestedDocument(HeaderButton buttonType) {
    	Book suggestedDoc = null;

    	switch (buttonType) {
    	case BIBLE:
    		suggestedDoc = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getCurrentDocument();
    		break;
    	case COMMENTARY:
    		suggestedDoc = ControlFactory.getInstance().getCurrentPageControl().getCurrentCommentary().getCurrentDocument();
    		break;
    	case DICTIONARY:
    		suggestedDoc = ControlFactory.getInstance().getCurrentPageControl().getCurrentDictionary().getCurrentDocument();
    		break;
    	case GEN_BOOK:
    		suggestedDoc = ControlFactory.getInstance().getCurrentPageControl().getCurrentGeneralBook().getCurrentDocument();
    		break;
    	}
    	return suggestedDoc;
    }

    //TODO prevent Strongs button being shown
	protected void updatePageTitle() {
		// shorten plan code and show it in doc button
    	setDocumentTitle(StringUtils.left(mReadings.getReadingPlanInfo().getCode(), 8));
    	// show day in page/key button
    	setPageTitle(mReadings.getDayDesc());
    	
    	// make sure docs show something
    	updateSuggestedDocuments();
	}
	
	@Override
	protected void preferenceSettingsChanged() {
		// TODO Auto-generated method stub
	}
}
