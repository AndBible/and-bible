package net.bible.android.control;

import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.history.HistoryManager;
import android.util.Log;

/** when a bible passage is changed there are lots o things to update and they should be done in a helpful order
 * This helps to control screen updates after a passage change
 * @author denha1m
 *
 */
public class PassageChangeMediator {

	private MainBibleActivity mMainBibleActivity;
	private BibleContentManager mBibleContentManager;
	
	private static final String TAG = "PassageChangeMediator";
	
	private static final PassageChangeMediator singleton = new PassageChangeMediator();
	
	public static final PassageChangeMediator getInstance() {
		return singleton;
	}

	public void onBeforeCurrentPageChanged() {
		HistoryManager.getInstance().beforePageChange();
	}
	public void onCurrentPageChanged() {
		if (mBibleContentManager!=null) {
			mBibleContentManager.updateText();
		} else {
			Log.w(TAG, "BibleContentManager not yet registered");
		}
	}
	public void onCurrentPageDetailChanged() {
		doVerseChanged();
	}

	public void contentChangeStarted() {
		if (mMainBibleActivity!=null) {
			mMainBibleActivity.onPassageChangeStarted();
		} else {
			Log.w(TAG, "Bible activity not yet registered");
		}
	}
	public void contentChangeFinished() {
		if (mMainBibleActivity!=null) {
			mMainBibleActivity.onPassageChanged();
		} else {
			Log.w(TAG, "Bible activity not yet registered");
		}
	}
	
	private void doVerseChanged() {
		if (mMainBibleActivity!=null) {
			mMainBibleActivity.onVerseChanged();
		} else {
			Log.w(TAG, "Bible activity not yet registered");
		}
	}

	public void setBibleContentManager(BibleContentManager bibleContentManager) {
		this.mBibleContentManager = bibleContentManager;
	}

	public void setMainBibleActivity(MainBibleActivity mainBibleActivity) {
		this.mMainBibleActivity = mainBibleActivity;
	}
}
