package net.bible.android.view.activity.page.actionmode;

import android.app.Activity;
import android.content.Intent;

import net.bible.android.activity.R;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.comparetranslations.CompareTranslations;
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity;

import org.crosswire.jsword.passage.VerseRange;

/** Handle requests from the selected verse action menu
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseMenuCommandHandler {

	private final Activity mainActivity;

	private final PageControl pageControl;

	private final BookmarkControl bookmarkControl;

	private final MyNoteControl myNoteControl;

	private final IntentHelper intentHelper = new IntentHelper();

	private static final String TAG = "VerseMenuCommandHandler";

	public VerseMenuCommandHandler(Activity mainActivity, PageControl pageControl, BookmarkControl bookmarkControl, MyNoteControl myNoteControl) {
		super();
		this.mainActivity = mainActivity;
		this.pageControl = pageControl;
		this.bookmarkControl = bookmarkControl;
		this.myNoteControl = myNoteControl;
	}
	
	/**
	 * on Click handler for Selected verse menu
	 */
	public boolean handleMenuRequest(int menuItemId, VerseRange verseRange) {
		boolean isHandled = false;

		{
			Intent handlerIntent = null;
			int requestCode = ActivityBase.STD_REQUEST_CODE;

			// Handle item selection
			switch (menuItemId) {
				case R.id.compareTranslations:
					handlerIntent = new Intent(mainActivity, CompareTranslations.class);
					isHandled = true;
					break;
				case R.id.notes:
					handlerIntent = new Intent(mainActivity, FootnoteAndRefActivity.class);
					isHandled = true;
					break;
				case R.id.add_bookmark:
					bookmarkControl.addBookmarkForVerseRange(verseRange);
					// refresh view to show new bookmark icon
					PassageChangeMediator.getInstance().forcePageUpdate();
					isHandled = true;
					break;
				case R.id.delete_bookmark:
					bookmarkControl.deleteBookmarkForVerseRange(verseRange);
					// refresh view to show new bookmark icon
					PassageChangeMediator.getInstance().forcePageUpdate();
					isHandled = true;
					break;
				case R.id.edit_bookmark_labels:
					bookmarkControl.editBookmarkLabelsForVerseRange(verseRange);
					isHandled = true;
					break;
				case R.id.myNoteAddEdit:
					myNoteControl.showMyNote(verseRange);
					isHandled = true;
					break;
				case R.id.copy:
					pageControl.copyToClipboard(verseRange);
					isHandled = true;
					break;
				case R.id.shareVerse:
					pageControl.shareVerse(verseRange);
					isHandled = true;
					break;
			}

			if (handlerIntent!=null) {
				intentHelper.updateIntentWithVerseRange(handlerIntent, verseRange);
				mainActivity.startActivityForResult(handlerIntent, requestCode);
			}
		}

		return isHandled;
	}
}
