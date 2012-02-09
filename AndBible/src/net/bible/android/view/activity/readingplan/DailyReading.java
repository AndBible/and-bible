package net.bible.android.view.activity.readingplan;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.readingplan.ReadingStatus;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.readingplan.OneDaysReadingsDto;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.versification.BookName;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
	private List<ImageView> mImageTickList;
	private Button mDoneButton;
	
	private int mDay;
	
	public static final String PLAN = "net.bible.android.view.activity.readingplan.Plan";
	public static final String DAY = "net.bible.android.view.activity.readingplan.Day";
	
	private OneDaysReadingsDto mReadings;

	private ReadingPlanControl mReadingPlanControl = ControlFactory.getInstance().getReadingPlanControl();
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying one day reading plan");
        setContentView(R.layout.reading_plan_one_day);
        
        try {
			// may not be for current day if user presses forward or backward
	        mDay = mReadingPlanControl.getCurrentPlanDay();
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				if (extras.containsKey(PLAN)) {
					mReadingPlanControl.setReadingPlan(extras.getString(PLAN));
				}
				if (extras.containsKey(DAY)) {
					mDay = extras.getInt(DAY, mDay);
				}
			}

			// get readings for chosen day
	        mReadings = mReadingPlanControl.getDaysReading(mDay);
	        
	        // Populate view
	        
	        mDescriptionView =  (TextView)findViewById(R.id.description);
	        mDescriptionView.setText(mReadings.getReadingPlanInfo().getDescription());
	
	        // date display
	        mDayView =  (TextView)findViewById(R.id.day);
	        mDayView.setText(mReadings.getDayDesc());
	        mDateView =  (TextView)findViewById(R.id.date);
	        mDateView.setText(mReadings.getReadingDateString());
	
	        mDoneButton = (Button)findViewById(R.id.doneButton);
//	        mStatusMsgView =  (TextView)findViewById(R.id.status_message);
	        
	        mImageTickList = new ArrayList<ImageView>();

	        // show short book name to save space if Portrait
			boolean fullBookNameSave = BookName.isFullBookName();
			BookName.setFullBookName(!CommonUtils.isPortrait());
	        
	        TableLayout layout = (TableLayout)findViewById(R.id.reading_container);
	        for (int i=0; i<mReadings.getNumReadings(); i++) {
	        	final int readingNo = i;
	            View child = getLayoutInflater().inflate(R.layout.reading_plan_one_reading, null);
	
	            // Ticks
	            mImageTickList.add((ImageView)child.findViewById(R.id.tick));
	            
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
	
			// restore full book name setting
			BookName.setFullBookName(fullBookNameSave);

	        updateTicksAndDone();
	        
	        // Speak All
	        if (mReadings.getNumReadings()>1) {
		        View child = getLayoutInflater().inflate(R.layout.reading_plan_one_reading, null);
		
		        // hide the tick
		        ImageView tick = (ImageView)child.findViewById(R.id.tick);
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
	        }
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

    // the button that called this has been removed
    public void onNext(View view) {
    	Log.i(TAG, "Next");
    	if (mDay<mReadings.getReadingPlanInfo().getNumberOfPlanDays()) {
	    	Intent handlerIntent = new Intent(this, DailyReading.class);
	    	handlerIntent.putExtra(DAY, mDay+1);
	    	startActivity(handlerIntent);
	    	finish();
    	}
    }

    // the button that called this has been removed
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
    	try {
	    	// do not add to History list because it will just redisplay same page
	    	setIntegrateWithHistoryManager(false);
	    	
	    	// all readings must be ticked for this to be enabled
	    	mReadingPlanControl.done(mReadings.getReadingPlanInfo(), mDay);
	    	
	    	//if user is behind then go to next days readings
	    	if (mReadingPlanControl.isDueToBeRead(mReadings.getReadingPlanInfo(), mDay+1)) {
	    		onNext(null);
	    	} else {
	    		// else exit
	        	finish();
	    	}
	
	    	// if we move away then add to history list
	    	setIntegrateWithHistoryManager(true);
        } catch (Exception e) {
        	Log.e(TAG, "Error when Done daily reading", e);
        	Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
        }
    }
    
    /** allow activity to enhance intent to correctly restore state */
	public Intent getIntentForHistoryList() {
		Intent intent = getIntent();
		
		intent.putExtra(DailyReading.PLAN, mReadings.getReadingPlanInfo().getCode());
		intent.putExtra(DailyReading.DAY, mReadings.getDay());

		return intent;
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
		
		for (int i=0; i<mImageTickList.size(); i++) {
			ImageView imageTick = mImageTickList.get(i);
			if (status.isRead(i)) {
				imageTick.setImageResource(R.drawable.btn_check_buttonless_on);
			} else {
				imageTick.setImageResource(R.drawable.btn_check_buttonless_off);
			}
		}
		
		mDoneButton.setEnabled(status.isAllRead());
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

    //TODO move the below up to more general parent class
    
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.reading_plan, menu);
        return true;
    }

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = false;
        
        switch (item.getItemId()) {
		case (R.id.reset):
			mReadingPlanControl.reset(mReadings.getReadingPlanInfo());
			finish();
			isHandled = true;
			break;
		case (R.id.setStartToJan1):
			mReadingPlanControl.setStartToJan1(mReadings.getReadingPlanInfo());
		
			// refetch readings for chosen day
	        mReadings = mReadingPlanControl.getDaysReading(mDay);
	        
	        // update date and day no
	        mDateView.setText(mReadings.getReadingDateString());
			mDayView.setText(mReadings.getDayDesc());
			
			isHandled = true;
			break;
        }
        
		if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
     	return isHandled;
    }
}
