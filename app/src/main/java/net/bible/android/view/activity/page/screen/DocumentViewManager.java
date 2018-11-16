package net.bible.android.view.activity.page.screen;

import android.view.View;
import android.widget.LinearLayout;

import net.bible.android.activity.R;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.mynote.MyNoteViewBuilder;
import net.bible.android.view.activity.page.MainBibleActivity;

import javax.inject.Inject;

import java.util.List;

/**
 * Create Views for displaying documents
 * 
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class DocumentViewManager {

	private DocumentWebViewBuilder documentWebViewBuilder;
	private MyNoteViewBuilder myNoteViewBuilder;
	private LinearLayout parent;
	
	private WindowControl windowControl;
	private MainBibleActivity mainBibleActivity;

	@Inject
	public DocumentViewManager(MainBibleActivity mainBibleActivity, DocumentWebViewBuilder documentWebViewBuilder, MyNoteViewBuilder myNoteViewBuilder, WindowControl windowControl) {
		this.mainBibleActivity = mainBibleActivity;
		this.documentWebViewBuilder = documentWebViewBuilder;
		this.myNoteViewBuilder = myNoteViewBuilder;
		this.parent = (LinearLayout)mainBibleActivity.findViewById(R.id.mainBibleView);
		this.windowControl = windowControl;

		ABEventBus.getDefault().register(this);
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
		List<Window> windows = windowControl.getWindowRepository().getVisibleWindows();
		for(Window window: windows) {
			mainBibleActivity.registerForContextMenu((View) getDocumentView(window));
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
