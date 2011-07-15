package net.bible.android.control;

import net.bible.android.control.page.CurrentPageManager;
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
	private boolean isPageChanging = false;
	
	private static final String TAG = "PassageChangeMediator";
	
	private static final PassageChangeMediator singleton = new PassageChangeMediator();
	
	public static final PassageChangeMediator getInstance() {
		return singleton;
	}

	/** first time we know a page or doc will imminently change
	 */
	public void onBeforeCurrentPageChanged() {
		isPageChanging = true;
		
		// save screen offset for current doc before change occurs
		if (mMainBibleActivity!=null && CurrentPageManager.getInstance().getCurrentPage()!=null) {
			float screenPosn = mMainBibleActivity.getCurrentPosition();
			CurrentPageManager.getInstance().getCurrentPage().setCurrentYOffsetRatio(screenPosn);
		}

		HistoryManager.getInstance().beforePageChange();
	}
	/** the document has changed so ask the view to refresh itself
	 */
	public void onCurrentPageChanged() {
		if (mBibleContentManager!=null) {
			mBibleContentManager.updateText();
		} else {
			Log.w(TAG, "BibleContentManager not yet registered");
		}
	}

	/** this is triggered on scroll
	 */
	public void onCurrentPageDetailChanged() {
		doVerseChanged();
	}

	/** The thread which fetches the new page html has started
	 */
	public void contentChangeStarted() {
		isPageChanging = true;
		
		if (mMainBibleActivity!=null) {
			mMainBibleActivity.onPassageChangeStarted();
		} else {
			Log.w(TAG, "Bible activity not yet registered");
		}
	}
	/** finished fetching html so should hide hourglass
	 */
	public void contentChangeFinished() {
		if (mMainBibleActivity!=null) {
			mMainBibleActivity.onPassageChanged();
		} else {
			Log.w(TAG, "Bible activity not yet registered");
		}

		isPageChanging = false;
	}
	
	private void doVerseChanged() {
		if (mMainBibleActivity!=null) {
			mMainBibleActivity.onVerseChanged();
		} else {
			Log.w(TAG, "Bible activity not yet registered");
		}
	}

	public boolean isPageChanging() {
		return isPageChanging;
	}

	public void setBibleContentManager(BibleContentManager bibleContentManager) {
		this.mBibleContentManager = bibleContentManager;
	}

	public void setMainBibleActivity(MainBibleActivity mainBibleActivity) {
		this.mMainBibleActivity = mainBibleActivity;
	}
}
