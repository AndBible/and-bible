package net.bible.android.activity;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

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
