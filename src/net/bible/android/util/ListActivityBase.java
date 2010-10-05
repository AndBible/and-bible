package net.bible.android.util;

import net.bible.android.activity.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Base class for activities
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ListActivityBase extends ListActivity {
	private static final String TAG = "ListActivityBase";
	
	private Hourglass hourglass = new Hourglass();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(getLocalClassName(), "onCreate");

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
        }
        return null;
    }
    
    protected void dismissHourglass() {
    	hourglass.dismiss();
    }

//	@Override
//	protected void onRestart() {
//		// TODO Auto-generated method stub
//		super.onRestart();
//        Log.i(getLocalClassName(), "onRestart");
//	}
//
//	@Override
//	protected void onResume() {
//		// TODO Auto-generated method stub
//		super.onResume();
//        Log.i(getLocalClassName(), "onResume");
//	}
//
//	@Override
//	protected void onStart() {
//		// TODO Auto-generated method stub
//		super.onStart();
//        Log.i(getLocalClassName(), "onStart");
//	}
//
//	@Override
//	protected void onPause() {
//		// TODO Auto-generated method stub
//		super.onPause();
//        Log.i(getLocalClassName(), "onPause");
//	}
//
//	@Override
//	protected void onStop() {
//		// TODO Auto-generated method stub
//		super.onStop();
//        Log.i(getLocalClassName(), "onStop");
//	}
}
