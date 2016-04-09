package net.bible.android.view.activity.page;

import android.content.Intent;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.touch.ShowContextMenuEvent;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.comparetranslations.CompareTranslations;
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity;

import de.greenrobot.event.EventBus;

/**
 * Control the verse selection action mode
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseActionModeMediator {

	private MainBibleActivity mainBibleActivity;

    boolean isVerseActionMode;

    private static final String TAG = "VerseActionModeMediator";

	public VerseActionModeMediator() {
		ControlFactory.getInstance().inject(this);
	}

	public void setMainBibleActivity(MainBibleActivity mainBibleActivity) {
		this.mainBibleActivity = mainBibleActivity;
	}

	public void verseLongPress(int verse) {
        Log.d(TAG, "Verse selected event:"+verse);
        startVerseActionMode();
    }

    private void startVerseActionMode() {
        showActionModeMenu();
    }

    private void showActionModeMenu() {

		mainBibleActivity.showVerseActionModeMenu(
			new ActionMode.Callback() {
				@Override
				public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
					// Inflate our menu from a resource file
					actionMode.getMenuInflater().inflate(R.menu.document_viewer_context_menu, menu);

					// Return true so that the action mode is shown
					return true;
				}

				@Override
				public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
					// As we do not need to modify the menu before displayed, we return false.
					return false;
				}

				@Override
				public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
					// Similar to menu handling in Activity.onOptionsItemSelected()
					Intent handlerIntent = null;
					boolean isHandled = false;
					int requestCode = ActivityBase.STD_REQUEST_CODE;

					// Handle item selection
					switch (menuItem.getItemId()) {
						case R.id.compareTranslations:
							handlerIntent = new Intent(mainBibleActivity, CompareTranslations.class);
							isHandled = true;
							break;
						case R.id.notes:
							handlerIntent = new Intent(mainBibleActivity, FootnoteAndRefActivity.class);
							isHandled = true;
							break;
						case R.id.add_bookmark:
							ControlFactory.getInstance().getBookmarkControl().bookmarkCurrentVerse();
							// refresh view to show new bookmark icon
							PassageChangeMediator.getInstance().forcePageUpdate();
							isHandled = true;
							break;
						case R.id.myNoteAddEdit:
							ControlFactory.getInstance().getCurrentPageControl().showMyNote();
							isHandled = true;
							break;
						case R.id.copy:
							ControlFactory.getInstance().getPageControl().copyToClipboard();
							isHandled = true;
							break;
						case R.id.shareVerse:
							ControlFactory.getInstance().getPageControl().shareVerse();
							isHandled = true;
							break;
					}

					if (handlerIntent!=null) {
						mainBibleActivity.startActivityForResult(handlerIntent, requestCode);
						isHandled = true;
					}

					// handle all
					return true;
				}

				@Override
				public void onDestroyActionMode(ActionMode actionMode) {
					// Allows you to be notified when the action mode is dismissed
				}
			});
    }

	public interface ActionModeMenuDisplay {
		void showVerseActionModeMenu(ActionMode.Callback actionModeCallbackHandler);
	}
}
