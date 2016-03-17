package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.passage.CurrentVerseChangedEvent;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.actionbar.Title;
import net.bible.android.view.activity.navigation.ChooseDocument;
import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBar;

/** 
 * Show current verse/key and document on left of actionBar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class HomeTitle extends Title {

	private PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	public void addToBar(ActionBar actionBar, final Activity activity) {
		super.addToBar(actionBar, activity);

		// listen for verse change events
		ABEventBus.getDefault().safelyRegister(this);
	}
	
	/**
	 * Receive verse change events
	 */
	public void onEvent(CurrentVerseChangedEvent passageEvent) {
		update(false);
	}
	
	@Override
	protected String[] getDocumentTitleParts() {
		return pageControl.getCurrentDocumentTitleParts();
	}

	@Override
	protected String[] getPageTitleParts() {
		return pageControl.getCurrentPageTitleParts();
	}

	@Override
	protected void onDocumentTitleClick() {
		Intent intent = new Intent(getActivity(), ChooseDocument.class);
		getActivity().startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE);
	}

	@Override
	protected void onPageTitleClick() {
		Intent intent = new Intent(getActivity(), ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getKeyChooserActivity());
		getActivity().startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE);
	}
}
