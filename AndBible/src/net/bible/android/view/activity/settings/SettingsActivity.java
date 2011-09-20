package net.bible.android.view.activity.settings;

import net.bible.android.activity.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

/** show settings
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SettingsActivity extends PreferenceActivity {

	private static final String TAG = "SettingsActivity";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			Log.d(TAG, "pref before");
			addPreferencesFromResource(R.xml.settings);
			Log.d(TAG, "pref after");
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
