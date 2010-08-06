package net.bible.android.util;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

public class ActivityBase extends Activity {
	private static final String TAG = "ActivityBase";
	
	private Hourglass hourglass = new Hourglass();
	
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

}
