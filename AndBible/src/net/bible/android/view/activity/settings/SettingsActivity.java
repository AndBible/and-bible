package net.bible.android.view.activity.settings;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.util.UiUtils;
import net.bible.service.device.ScreenSettings;
import android.os.Bundle;
import android.preference.Preference;
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
		// change theme according to light sensor
		UiUtils.applyTheme(this);

		super.onCreate(savedInstanceState);

		// allow partial integration with And Bible framework - without this TTS stops
		// putting this before the below ensures any error dialog will be displayed in front of the settings screen and not the previous screen
		CurrentActivityHolder.getInstance().setCurrentActivity(this);
		
		try {
			addPreferencesFromResource(R.xml.settings);
			
		    //If no light sensor exists switch to old boolean check box
			// see here for method: http://stackoverflow.com/questions/4081533/how-to-remove-android-preferences-from-the-screen
			Preference unusedNightModePreference = getPreferenceScreen().findPreference(ScreenSettings.getUnusedNightModePreferenceKey());
			getPreferenceScreen().removePreference(unusedNightModePreference);
			
			// if no tilt sensor then remove tilt-to-scroll setting
			if (!ControlFactory.getInstance().getPageTiltScrollControl().isTiltSensingPossible()) {
				Preference tiltToScrollPreferenceKey = getPreferenceScreen().findPreference(PageTiltScrollControl.TILT_TO_SCROLL_PREFERENCE_KEY);
				getPreferenceScreen().removePreference(tiltToScrollPreferenceKey);
			}
	    } catch (Exception e) {
			Log.e(TAG, "Error preparing preference screen", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
		}
	}
}
