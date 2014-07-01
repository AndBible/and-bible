package net.bible.android.view.activity.page.screen;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.splitscreen.NumberOfScreensChangedEvent;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.mynote.MyNoteViewBuilder;
import android.app.Activity;
import android.widget.LinearLayout;
import de.greenrobot.event.EventBus;
/**
 * Create Views for displaying documents
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DocumentViewManager {

	private DocumentWebViewBuilder documentWebViewBuilder;
	private MyNoteViewBuilder myNoteViewBuilder;
	private Activity mainActivity;
	private LinearLayout parent;
	
	private SplitScreenControl splitScreenControl;
	
	public DocumentViewManager(Activity mainActivity) {
		this.mainActivity = mainActivity;
		documentWebViewBuilder = new DocumentWebViewBuilder(this.mainActivity);
		myNoteViewBuilder = new MyNoteViewBuilder(this.mainActivity);
		this.parent = (LinearLayout)mainActivity.findViewById(R.id.mainBibleView);
		splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

		EventBus.getDefault().register(this);
	}
	
	public void onEvent(NumberOfScreensChangedEvent event) {
		buildView();
	}

	public void buildView() {
    	if (myNoteViewBuilder.isMyNoteViewType()) {
    		documentWebViewBuilder.removeWebView(parent);
    		myNoteViewBuilder.addMyNoteView(parent);
    	} else {
    		myNoteViewBuilder.removeMyNoteView(parent);
    		documentWebViewBuilder.addWebView(parent);
    	}
	}

	public DocumentView getDocumentView() {
		return getDocumentView(splitScreenControl.getCurrentActiveScreen());
	}
	public DocumentView getDocumentView(Screen screen) {
		if (myNoteViewBuilder.isMyNoteViewType()) {
			return myNoteViewBuilder.getView();
		} else {
			// a specific screen is specified to prevent content going to wrong screen if active screen is changed fast
			return documentWebViewBuilder.getView(screen);
		}
	}
}
