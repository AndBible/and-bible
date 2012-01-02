package net.bible.android.view.activity.page;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.bookmark.Bookmarks;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.help.Help;
import net.bible.android.view.activity.mynote.MyNotes;
import net.bible.android.view.activity.navigation.ChooseDocument;
import net.bible.android.view.activity.navigation.History;
import net.bible.android.view.activity.readingplan.DailyReadingList;
import net.bible.android.view.activity.settings.SettingsActivity;
import net.bible.android.view.activity.speak.Speak;
import net.bible.service.common.CommonUtils;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Handle requests from the main menu
 * 
 * @author denha1m
 *
 */
public class MainMenuCommandHandler {

	private static final String TAG = "MainMenuCommandHandler";
	
	public static class IntentHolder {
		Intent intent;
		int requestCode;
	}
	
	private Activity callingActivity;
	
	// request codes passed to and returned from sub-activities
	static final int REFRESH_DISPLAY_ON_FINISH = 2;
	static final int UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH = 3;

	private String mPrevLocalePref = "";
	
	public MainMenuCommandHandler(Activity activity) {
		super();
		this.callingActivity = activity;
	}

	/** 
     * on Click handlers
     */
    public boolean handleMenuRequest(int menuItemId) {
        boolean isHandled = false;
        int requestCode = ActivityBase.STD_REQUEST_CODE;
        
    	// Activities
    	{
    		Intent handlerIntent = null;
	        // Handle item selection
	        switch (menuItemId) {
	        case R.id.chooseBookButton:
	        	handlerIntent = new Intent(callingActivity, ChooseDocument.class);
	        	break;
	        case R.id.selectPassageButton:
	        	handlerIntent = new Intent(callingActivity, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
	        	break;
	        case R.id.searchButton:
	        	handlerIntent = ControlFactory.getInstance().getSearchControl().getSearchIntent(CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument());
	        	break;
	        case R.id.settingsButton:
	        	handlerIntent = new Intent(callingActivity, SettingsActivity.class);
	        	// force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
	        	mPrevLocalePref = CommonUtils.getLocalePref();
	        	requestCode = REFRESH_DISPLAY_ON_FINISH;
	        	break;
	        case R.id.historyButton:
	        	handlerIntent = new Intent(callingActivity, History.class);
	        	break;
	        case R.id.bookmarksButton:
	        	handlerIntent = new Intent(callingActivity, Bookmarks.class);
	        	break;
	        case R.id.mynotesButton:
	        	handlerIntent = new Intent(callingActivity, MyNotes.class);
	        	break;
			case R.id.speakButton:
	        	handlerIntent = new Intent(callingActivity, Speak.class);
	        	break;
	        case R.id.dailyReadingPlanButton:
	        	handlerIntent = new Intent(callingActivity, DailyReadingList.class);
	        	break;
	        case R.id.downloadButton:
	        	if (CommonUtils.getSDCardMegsFree()<SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
	            	Dialogs.getInstance().showErrorMsg(R.string.storage_space_warning);
	        	} else if (!CommonUtils.isInternetAvailable()) {
	            	Dialogs.getInstance().showErrorMsg(R.string.no_internet_connection);
	        	} else {
	        		handlerIntent = new Intent(callingActivity, Download.class);
	        		requestCode = UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH;
	        	}
	        	break;
	        case R.id.helpButton:
	        	handlerIntent = new Intent(callingActivity, Help.class);
	        	break;
	        }
	        
	        if (handlerIntent!=null) {
	        	callingActivity.startActivityForResult(handlerIntent, requestCode);
	        	isHandled = true;
	        } 
    	}

        return isHandled;
    }
    
    public boolean restartIfRequiredOnReturn(int requestCode) {
    	if (requestCode == MainMenuCommandHandler.REFRESH_DISPLAY_ON_FINISH) {
    		Log.i(TAG, "Refresh on finish");
    		if (!CommonUtils.getLocalePref().equals(mPrevLocalePref)) {
    			// must restart to change locale
    			PendingIntent intent = PendingIntent.getActivity(callingActivity.getBaseContext(), 0, new Intent(callingActivity.getIntent()), callingActivity.getIntent().getFlags());
    			AlarmManager mgr = (AlarmManager)callingActivity.getSystemService(Context.ALARM_SERVICE);
    			mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, intent);
    			System.exit(2);
    			return true;
    		}
    	}
    	return false;
    }

    public boolean isDisplayRefreshRequired(int requestCode) { 
    	return requestCode == MainMenuCommandHandler.REFRESH_DISPLAY_ON_FINISH;
	}
    
    public boolean isDocumentChanged(int requestCode) { 
    	return requestCode == MainMenuCommandHandler.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH;
    }

}
