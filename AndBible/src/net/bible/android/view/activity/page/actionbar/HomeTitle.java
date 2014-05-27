package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.passage.PassageEvent;
import net.bible.android.control.event.passage.PassageEventListener;
import net.bible.android.control.event.passage.PassageEventManager;
import net.bible.android.control.page.CurrentPageManager;
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
        PassageEventManager.getInstance().addPassageEventListener(new PassageEventListener() {
			@Override
			public void pageDetailChange(PassageEvent event) {
				update(false);
			}
		});
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
		Intent intent = new Intent(getActivity(), CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
		getActivity().startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE);
	}
}
