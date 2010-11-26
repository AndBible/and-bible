package net.bible.android.view.activity;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.activity.R.layout;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.service.common.CommonUtils;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

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
        
        TextView versionTextView = (TextView)findViewById(R.id.versionText);
        String versionMsg = BibleApplication.getApplication().getString(R.string.version_text, CommonUtils.getApplicationVersionName());
        versionTextView.setText(versionMsg);
    }
}
