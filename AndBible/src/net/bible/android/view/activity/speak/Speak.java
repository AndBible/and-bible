package net.bible.android.view.activity.speak;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.speak.NumPagesToSpeakDefinition;
import net.bible.android.control.speak.SpeakControl;
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
public class Speak extends ActivityBase {
	
    private NumPagesToSpeakDefinition[] numPagesToSpeakDefinitions;
    
    private CheckBox mQueueCheckBox;
    private CheckBox mRepeatCheckBox;
    
    private SpeakControl speakControl;
    
	private static final String TAG = "Speak";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.speak);
        
        speakControl = ControlFactory.getInstance().getSpeakControl();
        
        // set title of chapter/verse/page selection
        numPagesToSpeakDefinitions = speakControl.getNumPagesToSpeakDefinitions();
        
        // set a suitable prompt for the different numbers of chapters
        for (NumPagesToSpeakDefinition defn : numPagesToSpeakDefinitions) {
        	RadioButton numChaptersCheckBox = (RadioButton)findViewById(defn.getRadioButtonId());

        	numChaptersCheckBox.setText(defn.getPrompt());
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
    	speakControl.speak(getSelectedNumPagesToSpeak(), isQueue(), isRepeat());
    	
    	returnToMainScreen();
    }
    
    public void onStop(View v) {
    	Log.i(TAG, "CLICKED");
    	speakControl.stop();
		returnToMainScreen();
    }

    private NumPagesToSpeakDefinition getSelectedNumPagesToSpeak() {
        RadioGroup chaptersRadioGroup = (RadioGroup)findViewById(R.id.numChapters);
        int selectedId = chaptersRadioGroup.getCheckedRadioButtonId();
        
        for (NumPagesToSpeakDefinition defn : numPagesToSpeakDefinitions) {
        	if (selectedId == defn.getRadioButtonId()) {
        		return defn;
        	}
        }
        // error - should not get here
   		return numPagesToSpeakDefinitions[0];
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
