package net.bible.android.view.activity.speak;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.device.TextToSpeechController;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

/** Allow user to enter search criteria
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakBible extends ActivityBase {
	
	public static final String SEARCH_TEXT = "SearchText";
	
	private static final String TAG = "Search";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.speak_bible);
    
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
        int checked = chaptersRadioGroup.getCheckedRadioButtonId();
        switch (checked) {
        case R.id.oneChapter:
        	return 1;
        case R.id.twoChapter:
        	return 2;
        case R.id.fiveChapter:
        	return 5;
        case R.id.tenChapter:
        	return 10;
    	default:
    		return 1;
        }
    }

    private boolean isQueue() {
    	CheckBox queue = (CheckBox)findViewById(R.id.queue);
    	return queue.isChecked();
    }
    
    private boolean isRepeat() {
    	CheckBox repeat = (CheckBox)findViewById(R.id.repeat);
    	return repeat.isChecked();
    }

    private void returnToMainScreen() {
    	// just pass control back to teh main screen
    	Intent resultIntent = new Intent(this, MainBibleActivity.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();
    }
}
