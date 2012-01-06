package net.bible.android.view.activity.readingplan;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.readingplan.ReadingStatus;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.readingplan.OneDaysReadingsDto;

import org.crosswire.jsword.passage.Key;

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
public class DailyReading extends ActivityBase {
	
	private static final String TAG = "DailyReading";
	
	private TextView mDescriptionView;
	private TextView mDayView;
	private TextView mStatusMsgView;
	private List<ImageTickOnOff> mImageTickOnOffList;
	
	private int mDay;
	public static final String DAY = "day";
	
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
				mDay = extras.getInt(DAY, mDay);
			}
	
	        mReadings = mReadingPlanControl.getDaysReading(mDay);
	        
	        mDescriptionView =  (TextView)findViewById(R.id.description);
	        mDescriptionView.setText(mReadings.getReadingPlanInfo().getDescription());
	
	        mDayView =  (TextView)findViewById(R.id.day);
	        mDayView.setText(mReadings.getDayDesc());
	
	        mStatusMsgView =  (TextView)findViewById(R.id.status_message);
	        mStatusMsgView.setText("Status goes here");
	        
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
	
	            Button speakBtn = (Button)child.findViewById(R.id.speakButton);
	            speakBtn.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						onSpeak(readingNo);
					}
				});
	            
	            layout.addView(child, readingNo);
	        }
	
	        updateTicks();
	        
	        // All
	        View child = getLayoutInflater().inflate(R.layout.reading_plan_one_reading, null);
	
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
	        
//	        layout.getRootView().requestLayout();
	        
	        Log.d(TAG, "Finished displaying Reading view");
        } catch (Exception e) {
        	Log.e(TAG, "Error showing daily readings", e);
        	Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
        }
    }

    public void onSpeak(int readingNo) {
    	Log.i(TAG, "CLICKED "+readingNo);
    	Key readingKey = mReadings.getReadingKey(readingNo);
    	mReadingPlanControl.speak(mDay, readingNo, readingKey);
    	
    	updateTicks();
    }
    public void onSpeakAll(View view) {
    	Log.i(TAG, "CLICKED All");
    	mReadingPlanControl.speak(mDay, mReadings.getReadingKeys());
    	
    	updateTicks();
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
    	//TODO mark readings complete
    	mReadingPlanControl.completed(mDay);
    	finish();
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
        int requestCode = ActivityBase.STD_REQUEST_CODE;
        
    	// Activities
    	{
    		Intent handlerIntent = null;
	        // Handle item selection
	        switch (item.getItemId()) {
	        case R.id.dailyReadingPlanSelectionButton:
        		handlerIntent = new Intent(this, ReadingPlanSelectorList.class);
	        	break;
	        }
	        
	        if (handlerIntent!=null) {
	        	startActivityForResult(handlerIntent, requestCode);
	        	finish();
	        	isHandled = true;
	        } 
    	}

     	if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
     	return isHandled;
    }

    /** I don't think this is used because of hte finish() in onSearch()
     */
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToPreviousScreen();
    	}
    }

	private void updateTicks() {
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
	}
	
	private static class ImageTickOnOff {
		private ImageView unticked;
		private ImageView ticked;
	}
}
