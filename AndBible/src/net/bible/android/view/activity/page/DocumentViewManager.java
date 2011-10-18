package net.bible.android.view.activity.page;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.mynote.MyNoteViewBuilder;
import android.app.Activity;
import android.view.ViewGroup;
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
	private ViewGroup parent;

	public DocumentViewManager(Activity mainActivity) {
		this.mainActivity = mainActivity;
		documentWebViewBuilder = new DocumentWebViewBuilder(this.mainActivity);
		myNoteViewBuilder = new MyNoteViewBuilder(this.mainActivity);
		this.parent = (ViewGroup)mainActivity.findViewById(R.id.mainBibleView);
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
