package net.bible.android.view.activity.page;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.MenuItem;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.activity.StartupActivity;
import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.bookmark.Bookmarks;
import net.bible.android.view.activity.bookmark.ManageLabels;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.help.Help;
import net.bible.android.view.activity.installzip.InstallZip;
import net.bible.android.view.activity.mynote.MyNotes;
import net.bible.android.view.activity.navigation.History;
import net.bible.android.view.activity.page.screen.WindowMenuCommandHandler;
import net.bible.android.view.activity.readingplan.DailyReading;
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList;
import net.bible.android.view.activity.settings.SettingsActivity;
import net.bible.android.view.activity.speak.Speak;
import net.bible.android.view.activity.speak.SpeakBible;
import net.bible.service.common.CommonUtils;
import org.crosswire.jsword.book.BookCategory;

import java.util.Objects;

import javax.inject.Inject;

/** Handle requests from the main menu
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@MainBibleActivityScope
public class MenuCommandHandler {

	private DownloadControl downloadControl;

	private BackupControl backupControl;

	private MainBibleActivity callingActivity;

	private final ReadingPlanControl readingPlanControl;

	private final SearchControl searchControl;

	private final WindowMenuCommandHandler windowMenuCommandHandler;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private final WindowControl windowControl;

	private static final String TAG = "MainMenuCommandHandler";

	@Inject
	public MenuCommandHandler(MainBibleActivity activity, ReadingPlanControl readingPlanControl,
							  SearchControl searchControl, WindowMenuCommandHandler windowMenuCommandHandler,
							  ActiveWindowPageManagerProvider activeWindowPageManagerProvider,
							  WindowControl windowControl
							  ) {
		super();
		this.callingActivity = activity;
		this.readingPlanControl = readingPlanControl;
		this.searchControl = searchControl;
		this.windowMenuCommandHandler = windowMenuCommandHandler;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
		this.windowControl = windowControl;
	}
	
	/**
     * on Click handlers
     */
    public boolean handleMenuRequest(MenuItem menuItem) {
        boolean isHandled = false;

    	// Activities
    	{
    		Intent handlerIntent = null;
			int requestCode = ActivityBase.STD_REQUEST_CODE;
	        // Handle item selection
	        switch (menuItem.getItemId()) {
		        case R.id.searchButton:
		        	handlerIntent = searchControl.getSearchIntent(activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getCurrentDocument());
		        	break;
		        case R.id.settingsButton:
		        	handlerIntent = new Intent(callingActivity, SettingsActivity.class);
		        	// force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
		        	requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH;
		        	break;
		        case R.id.historyButton:
		        	handlerIntent = new Intent(callingActivity, History.class);
		        	break;
		        case R.id.bookmarksButton:
		        	handlerIntent = new Intent(callingActivity, Bookmarks.class);
		        	break;
				case (R.id.manageLabels):
					handlerIntent = new Intent(callingActivity, ManageLabels.class);
					requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH;
					break;
		        case R.id.mynotesButton:
		        	handlerIntent = new Intent(callingActivity, MyNotes.class);
		        	break;
				case R.id.speakButton:
					boolean isBible = windowControl.getActiveWindowPageManager().getCurrentPage()
							.getBookCategory().equals(BookCategory.BIBLE);
		        	handlerIntent = new Intent(callingActivity, isBible ? SpeakBible.class : Speak.class);
		        	break;
		        case R.id.dailyReadingPlanButton:
		        	// show todays plan or allow plan selection
		        	if (readingPlanControl.isReadingPlanSelected()) {
		        		handlerIntent = new Intent(callingActivity, DailyReading.class);
		        	} else {
		        		handlerIntent = new Intent(callingActivity, ReadingPlanSelectorList.class);
		        	}
		        	break;
		        case R.id.downloadButton:
		        	if (downloadControl.checkDownloadOkay()) {
		        		handlerIntent = new Intent(callingActivity, Download.class);
		        		requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH;
		        	}
		        	break;
		        case R.id.installZipButton:
		        	handlerIntent = new Intent(callingActivity, InstallZip.class);
		 
		        	break;		        	
		        case R.id.helpButton:
		        	handlerIntent = new Intent(callingActivity, Help.class);
		        	break;
		        case R.id.backup:
					backupControl.backupDatabase();
					isHandled = true;
		        	break;
		        case R.id.restore:
					backupControl.restoreDatabase();
					isHandled = true;
		        	break;
	        }
	        
	        if (!isHandled) {
	        	isHandled = windowMenuCommandHandler.handleMenuRequest(menuItem);
	        }
	        
	        if (handlerIntent!=null) {
	        	callingActivity.startActivityForResult(handlerIntent, requestCode);
	        	isHandled = true;
	        } 
    	}

        return isHandled;
    }
    
    public boolean restartIfRequiredOnReturn(int requestCode) {
    	if (requestCode == IntentHelper.REFRESH_DISPLAY_ON_FINISH) {
    		Log.i(TAG, "Refresh on finish");
    		if (!Objects.equals(CommonUtils.getLocalePref(), BibleApplication.getApplication().getLocaleOverrideAtStartUp())) {
    			// must restart to change locale
    			PendingIntent pendingIntent;
    			if (CommonUtils.isIceCreamSandwichPlus()) {
        			// works on 4.x but not on 2.1
        			Intent startupIntent = new  Intent("net.bible.android.activity.StartupActivity.class");
        			pendingIntent = PendingIntent.getActivity(callingActivity.getBaseContext(), 0, startupIntent, 0);
    			} else {
	    			//works on 2.1 but scroll errors on 4.x
	    			Intent startupIntent = new  Intent(callingActivity.getBaseContext(), StartupActivity.class);
					//noinspection ResourceType
					pendingIntent = PendingIntent.getActivity(callingActivity.getBaseContext(), 0, startupIntent, callingActivity.getIntent().getFlags());
    			}
    			AlarmManager mgr = (AlarmManager)callingActivity.getSystemService(Context.ALARM_SERVICE);
    			mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);
    			System.exit(2);
    			return true;
    		}
    	}
    	return false;
    }

    public boolean isDisplayRefreshRequired(int requestCode) { 
    	return requestCode == IntentHelper.REFRESH_DISPLAY_ON_FINISH;
	}
    
    public boolean isDocumentChanged(int requestCode) { 
    	return requestCode == IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH;
    }

	@Inject
	void setBackupControl(BackupControl backupControl) {
		this.backupControl = backupControl;
	}

	@Inject
	void setDownloadControl(DownloadControl downloadControl) {
		this.downloadControl = downloadControl;
	}
}