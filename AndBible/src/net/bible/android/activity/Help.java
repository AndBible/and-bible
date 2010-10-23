package net.bible.android.activity;

import net.bible.android.activity.base.ActivityBase;
import android.os.Bundle;
import android.util.Log;

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
}
