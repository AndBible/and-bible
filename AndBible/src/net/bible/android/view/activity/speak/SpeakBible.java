package net.bible.android.view.activity.speak;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** Allow user to enter search criteria
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakBible extends ActivityBase {
	
    private static final int[] CHECK_BOX_IDS = new int[] {R.id.numChapters1, R.id.numChapters2, R.id.numChapters3, R.id.numChapters4}; 
    private static final int[] NUM_CHAPTERS = new int[] {1, 2, 3, 10};

    private CheckBox mQueueCheckBox;
    private CheckBox mRepeatCheckBox;
    
	private static final String TAG = "SpeakBible";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.speak_bible);
        
        // set a suitable prompt for the different numbers of chapters 
        for (int i=0; i<CHECK_BOX_IDS.length; i++) {
        	RadioButton numChaptersCheckBox = (RadioButton)findViewById(CHECK_BOX_IDS[i]);
        	String label = getResources().getQuantityString(R.plurals.num_chapters, NUM_CHAPTERS[i], NUM_CHAPTERS[i]);

        	numChaptersCheckBox.setText(label);
        }
        
        // set defaults for Queue and Repeat
        mQueueCheckBox = (CheckBox)findViewById(R.id.queue);
        mRepeatCheckBox = (CheckBox)findViewById(R.id.repeat);
        mQueueCheckBox.setChecked(true);
        mRepeatCheckBox.setChecked(false);
    
        Log.d(TAG, "Finished displaying Speak view");
    }

    public void onSpeak(View v) {
    	Log.i(TAG, "CLICKED");
    	ControlFactory.getInstance().getSpeakControl().speak(getNumChapters(), isQueue(), isRepeat());
    	
    	returnToMainScreen();
    }
    
    public void onStop(View v) {
    	Log.i(TAG, "CLICKED");
    	ControlFactory.getInstance().getSpeakControl().stop();
		returnToMainScreen();
    }

    private int getNumChapters() {
        RadioGroup chaptersRadioGroup = (RadioGroup)findViewById(R.id.numChapters);
        int selectedId = chaptersRadioGroup.getCheckedRadioButtonId();
        
        for (int i=0; i<CHECK_BOX_IDS.length; i++) {
        	if (selectedId == CHECK_BOX_IDS[i]) {
        		return NUM_CHAPTERS[i];
        	}
        }
        // error - should not get here
   		return 1;
    }

    private boolean isQueue() {
    	return mQueueCheckBox.isChecked();
    }
    
    private boolean isRepeat() {
    	return mRepeatCheckBox.isChecked();
    }

    private void returnToMainScreen() {
    	// just pass control back to teh main screen
    	Intent resultIntent = new Intent(this, MainBibleActivity.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();
    }
}
