package net.bible.android.view.activity.page;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;
import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
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
import net.bible.android.view.activity.speak.GeneralSpeakActivity;
import net.bible.android.view.activity.speak.BibleSpeakActivity;
import net.bible.service.common.CommonUtils;
import org.crosswire.jsword.book.BookCategory;

import javax.inject.Inject;
import java.util.Objects;

import static net.bible.android.view.activity.page.MainBibleActivity.BACKUP_RESTORE_REQUEST;
import static net.bible.android.view.activity.page.MainBibleActivity.BACKUP_SAVE_REQUEST;

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
		        	requestCode = IntentHelper.RETURN_FROM_SETTINGS_ACTIVITY;
		        	break;
		        case R.id.historyButton:
		        	handlerIntent = new Intent(callingActivity, History.class);
		        	break;
		        case R.id.bookmarksButton:
		        	handlerIntent = new Intent(callingActivity, Bookmarks.class);
		        	break;
				case (R.id.manageLabels):
					handlerIntent = new Intent(callingActivity, ManageLabels.class);
					requestCode = IntentHelper.RETURN_FROM_SETTINGS_ACTIVITY;
					break;
		        case R.id.mynotesButton:
		        	handlerIntent = new Intent(callingActivity, MyNotes.class);
		        	break;
				case R.id.speakButton:
					boolean isBible = windowControl.getActiveWindowPageManager().getCurrentPage()
							.getBookCategory().equals(BookCategory.BIBLE);
					handlerIntent = new Intent(callingActivity, isBible ? BibleSpeakActivity.class : GeneralSpeakActivity.class);
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
					if(ContextCompat.checkSelfPermission(callingActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
						ActivityCompat.requestPermissions(callingActivity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, BACKUP_SAVE_REQUEST);
						return false;
					}
					else {
						backupControl.backupDatabase();
					}
					isHandled = true;
		        	break;
		        case R.id.restore:
					if(ContextCompat.checkSelfPermission(callingActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
						ActivityCompat.requestPermissions(callingActivity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BACKUP_RESTORE_REQUEST);
						return false;
					}
					else {
						backupControl.restoreDatabase();
					}
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
    	if (requestCode == IntentHelper.RETURN_FROM_SETTINGS_ACTIVITY) {
    		Log.i(TAG, "Refresh on finish");
    		if (!Objects.equals(CommonUtils.getLocalePref(), BibleApplication.getApplication().getLocaleOverrideAtStartUp())) {
				// must restart to change locale
    			CommonUtils.restartApp(callingActivity);
    		}
    	}
    	return false;
    }

    public boolean isDisplayRefreshRequired(int requestCode) { 
    	return requestCode == IntentHelper.RETURN_FROM_SETTINGS_ACTIVITY;
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