package net.bible.android.util;

import static net.bible.android.util.ActivityBase.INTERNET_NOT_AVAILABLE_DIALOG;
import net.bible.android.activity.Download;
import net.bible.android.activity.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

/** Base class for activities
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ActivityBase extends Activity {
	private static final String TAG = "ActivityBase";
	
	private Hourglass hourglass = new Hourglass();

	protected static final int INTERNET_NOT_AVAILABLE_DIALOG = 20;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }
    
    /** for some reason Android insists Dialogs are created in the onCreateDialog method
     * 
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case Hourglass.HOURGLASS_KEY:
                hourglass.show(this);
                return hourglass.getHourglass();
            case INTERNET_NOT_AVAILABLE_DIALOG:
            	return new AlertDialog.Builder(this)
            		   .setMessage(getText(R.string.no_internet_connection))
            	       .setCancelable(false)
            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int buttonId) {
            	        	   dialogOnClick(INTERNET_NOT_AVAILABLE_DIALOG, buttonId);
            	           }
            	       }).create();
//            case DIALOG_NOTES:
//            	final List<Note> notesList = bibleContentManager.getNotesList(CurrentPassage.getInstance().getCurrentVerse());
//                SimpleAdapter adapter = new SimpleAdapter(this, notesList, 
//                        R.layout.two_line_list_item_copy, 
//                        new String[] {Note.SUMMARY, Note.DETAIL}, 
//                        new int[] {android.R.id.text1, android.R.id.text2});
//            	
//           	
//                return new AlertDialog.Builder(MainBibleActivity.this)
//                .setTitle(R.string.notes)
//                .setCancelable(true)
//                .setAdapter(adapter, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int selected) {
//                    	Note selectedNote = notesList.get(selected);
//                    	if (selectedNote.isNavigable()) {
//                    		selectedNote.navigateTo();
//                    	}
//                    }
//                })
//                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int selected) {
//                    	// do nothing but allow return to current page
//                    }
//                })
//                .create();
        }
        return null;
    }
    
    protected void dismissHourglass() {
    	hourglass.dismiss();
    }

    /** to retry e.g. if internet conn down override this method
     */
    protected void dialogOnClick(int dialogId, int buttonId) {
    }
    
    protected void returnErrorToPreviousScreen() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(Activity.RESULT_CANCELED, resultIntent);
    	finish();    
    }
    protected void returnToPreviousScreen() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

}
