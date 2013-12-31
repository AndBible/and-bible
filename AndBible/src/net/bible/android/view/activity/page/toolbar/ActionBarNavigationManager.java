package net.bible.android.view.activity.page.toolbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.navigation.ChooseDocument;
import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.widget.ArrayAdapter;

public class ActionBarNavigationManager implements ActionBar.OnNavigationListener {

	private PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	public void  initialiseActionBarNavigation(ActionBar actionBar) {
		actionBar.setDisplayShowHomeEnabled(false);
		actionBar.setDisplayShowTitleEnabled(false);
		
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

	    final String[] dropdownValues = new String[] {getTitle(), "Passage", "Chapter", "Verse", "Document"};

	    // Specify a SpinnerAdapter to populate the dropdown list.
	    ArrayAdapter<String> adapter = new ArrayAdapter<String>(actionBar.getThemedContext(),
	        android.R.layout.simple_spinner_item, android.R.id.text1,
	        dropdownValues);

	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

	    // Set up the dropdown list navigation in the action bar.
	    actionBar.setListNavigationCallbacks(adapter, this);

	}

	@Override
	public boolean onNavigationItemSelected(int position, long id) {

		Intent handlerIntent = null;
		int requestCode = ActivityBase.STD_REQUEST_CODE;
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		
		switch (position) {
	        case 1:
	        case 2:
	        case 3:
	        	handlerIntent = new Intent(currentActivity, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
	        	break;
	        case 4:
	        	handlerIntent = new Intent(currentActivity, ChooseDocument.class);
	        	break;
		}
        if (handlerIntent!=null) {
        	currentActivity.startActivityForResult(handlerIntent, requestCode);
        }
		return true;
	}
	
	private String getTitle() {
		return pageControl.getCurrentDocumentTitle()+" "+pageControl.getCurrentPageTitle();
	}
}
