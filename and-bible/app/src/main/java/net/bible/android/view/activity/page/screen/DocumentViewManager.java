package net.bible.android.view.activity.page.screen;

import android.app.Activity;
import android.widget.LinearLayout;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.mynote.MyNoteViewBuilder;
import net.bible.android.view.activity.page.MainBibleActivity;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
/**
 * Create Views for displaying documents
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class DocumentViewManager {

	private DocumentWebViewBuilder documentWebViewBuilder;
	private MyNoteViewBuilder myNoteViewBuilder;
	private Activity mainBibleActivity;
	private LinearLayout parent;
	
	private WindowControl windowControl;

	@Inject
	public DocumentViewManager(MainBibleActivity mainBibleActivity, DocumentWebViewBuilder documentWebViewBuilder, MyNoteControl myNoteControl) {
		this.mainBibleActivity = mainBibleActivity;
		this.documentWebViewBuilder = documentWebViewBuilder;
		myNoteViewBuilder = new MyNoteViewBuilder(this.mainBibleActivity, myNoteControl);
		this.parent = (LinearLayout)mainBibleActivity.findViewById(R.id.mainBibleView);
		windowControl = ControlFactory.getInstance().getWindowControl();

		EventBus.getDefault().register(this);
	}
	
	public void onEvent(NumberOfWindowsChangedEvent event) {
		buildView();
	}

	public synchronized void buildView() {
    	if (myNoteViewBuilder.isMyNoteViewType()) {
    		documentWebViewBuilder.removeWebView(parent);
    		myNoteViewBuilder.addMyNoteView(parent);
    	} else {
    		myNoteViewBuilder.removeMyNoteView(parent);
    		documentWebViewBuilder.addWebView(parent);
    	}
	}

	public DocumentView getDocumentView() {
		return getDocumentView(windowControl.getActiveWindow());
	}
	public DocumentView getDocumentView(Window window) {
		if (myNoteViewBuilder.isMyNoteViewType()) {
			return myNoteViewBuilder.getView();
		} else {
			// a specific screen is specified to prevent content going to wrong screen if active screen is changed fast
			return documentWebViewBuilder.getView(window);
		}
	}
}
