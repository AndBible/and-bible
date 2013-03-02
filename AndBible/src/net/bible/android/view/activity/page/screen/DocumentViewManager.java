package net.bible.android.view.activity.page.screen;

import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.mynote.MyNoteViewBuilder;

import android.app.Activity;
import android.widget.LinearLayout;
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
		
		splitScreenControl.addSplitScreenEventListener(new SplitScreenEventListener() {
			
			@Override
			public void numberOfScreensChanged(Map<Screen, Integer> screenVerseMap) {
				buildView();
			}
			
			@Override
			public void updateSecondaryScreen(Screen updateScreen, String html,	int verseNo) {
				// Noop				
			}
			
			@Override
			public void scrollSecondaryScreen(Screen screen, int verseNo) {
				// Noop
			}
			
			@Override
			public void currentSplitScreenChanged(Screen activeScreen) {
				// Noop
			}

			@Override
			public void splitScreenSizeChange(boolean isMoveFinished, Map<Screen, Integer> screenVerseMap) {
				// Noop
			}
		});		
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
		if (myNoteViewBuilder.isMyNoteViewType()) {
			return myNoteViewBuilder.getView();
		} else {
			return documentWebViewBuilder.getView();
		}
	}
}
