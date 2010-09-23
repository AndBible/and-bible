package net.bible.android.view;

import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;
import net.bible.android.activity.MainBibleActivity;

public class PassageChangeMediator {

	private MainBibleActivity mMainBibleActivity;
	private BibleContentManager mBibleContentManager;
	
	private static final PassageChangeMediator singleton = new PassageChangeMediator();
	
	public static final PassageChangeMediator getInstance() {
		return singleton;
	}
	
	private PassageChangeMediator() {
    	CurrentPassage.getInstance().addObserver(new Observer() {

			@Override
			public void update(Observable observable, Object data) {
				mBibleContentManager.updateText();
			}
    	});
    	CurrentPassage.getInstance().addVerseObserver(new Observer() {
			@Override
			public void update(Observable observable, Object data) {
				doVerseChanged();
			}
    	});

	}
	
	public void contentChangeStarted() {
		mMainBibleActivity.setProgressBarIndeterminateVisibility(true);
	}
	public void contentChangeFinished() {
		mMainBibleActivity.setProgressBarIndeterminateVisibility(false);
		mMainBibleActivity.onPassageChanged();
	}
	
	private void doVerseChanged() {
		mMainBibleActivity.onPassageChanged();
	}

	public void setBibleContentManager(BibleContentManager bibleContentManager) {
		this.mBibleContentManager = bibleContentManager;
	}

	public void setMainBibleActivity(MainBibleActivity mainBibleActivity) {
		this.mMainBibleActivity = mainBibleActivity;
	}
	
}
