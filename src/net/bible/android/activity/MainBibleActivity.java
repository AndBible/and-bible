package net.bible.android.activity;

import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;
import net.bible.android.util.Hourglass;
import net.bible.android.view.BibleSwipeListener;
import net.bible.service.sword.SwordApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

/** The main activity screen showing Bible text
 * 
 * @author denha1m
 */
public class MainBibleActivity extends Activity {

	private Hourglass hourglass = new Hourglass();
	
	private static final String TAG = "MainBibleActivity";

	// detect swipe left/right
	private GestureDetector gestureDetector;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_bible_view);

        gestureDetector = new GestureDetector( new BibleSwipeListener(this) );
        
        initialiseSword();
        restoreState();
        initialiseView();
    }
    
    /** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Class<? extends Activity> handlerClass = null;
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.chooseBookButton:
        	handlerClass = ChooseDocument.class;
        	break;
        case R.id.selectPassageButton:
        	handlerClass = ChoosePassageBook.class;
        	break;
        case R.id.searchButton:
        	handlerClass = Search.class;
        	break;
        }

        boolean isHandled = false;
        if (handlerClass!=null) {
        	Intent handlerIntent = new Intent(this, handlerClass);
        	startActivityForResult(handlerIntent, 1);
        	isHandled = true;
        } else {
            isHandled = super.onOptionsItemSelected(item);
        }
        
        return isHandled;
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {     
      super.onActivityResult(requestCode, resultCode, data);
    }
    
    private void initialiseSword() {
    	try {
    		SwordApi.getInstance();
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising Sword", e);
    	}
    }

    private void initialiseView() {
    	//todo call CurrentPassage.update???
    	CurrentPassage.getInstance().addObserver(new Observer() {

			@Override
			public void update(Observable observable, Object data) {
				onPassageChanged();
			}
    	});
    	CurrentPassage.getInstance().addVerseObserver(new Observer() {
			@Override
			public void update(Observable observable, Object data) {
				onPassageChanged();
			}
    	});
    	// initialise title etc
    	onPassageChanged();
    }

    private void onPassageChanged() {
    	String passageDesc = CurrentPassage.getInstance().toString();
    	setTitle(passageDesc);
    }
  
    
    @Override
	protected void onPause() {
    	Log.i(TAG, "Saving instance state");
		super.onPause();
    	SharedPreferences settings = getSharedPreferences(TAG, 0);
		CurrentPassage.getInstance().saveState(settings);
	}

    private void restoreState() {
    	try {
        	Log.i(TAG, "Restore instance state");
        	SharedPreferences settings = getSharedPreferences(TAG, 0);
    		CurrentPassage.getInstance().restoreState(settings);
    	} catch (Exception e) {
    		Log.e(TAG, "Restore error", e);
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
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

    // handle swipe left and right
    // http://android-journey.blogspot.com/2010_01_01_archive.html
    //http://android-journey.blogspot.com/2010/01/android-gestures.html
    // above dropped in favour of simpler method below
    //http://developer.motorola.com/docstools/library/The_Widget_Pack_Part_3_Swipe/
	@Override
	public boolean dispatchTouchEvent(MotionEvent motionEvent) {
		this.gestureDetector.onTouchEvent(motionEvent);
		return super.dispatchTouchEvent(motionEvent);
	}
	
 }