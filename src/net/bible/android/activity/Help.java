package net.bible.android.activity;

 import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.base.ActivityBase;
import net.bible.service.history.HistoryItem;
import net.bible.service.history.HistoryManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Help extends ActivityBase {
	private static final String TAG = "Help";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying History view");
        setContentView(R.layout.help);
    }
    
    public void onOkay(View v) {
    	finish();
    }
}
