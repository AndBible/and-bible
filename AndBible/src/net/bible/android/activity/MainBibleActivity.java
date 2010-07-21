package net.bible.android.activity;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;
import net.bible.android.util.Hourglass;
import net.bible.android.view.BibleContentManager;
import net.bible.android.view.BibleSwipeListener;
import net.bible.service.format.Note;
import net.bible.service.sword.SwordApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.SimpleAdapter;

/** The main activity screen showing Bible text
 * 
 * @author denha1m
 */
public class MainBibleActivity extends Activity {

	private BibleContentManager bibleContentManager;
	
	private Hourglass hourglass = new Hourglass();
	
	private static final int DIALOG_NOTES = 1;
	
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
        boolean isHandled = false;
        
    	// Activities
    	{
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
	        case R.id.settingsButton:
	        	handlerClass = SettingsActivity.class;
	        	break;
	        }
	
	        if (handlerClass!=null) {
	        	Intent handlerIntent = new Intent(this, handlerClass);
	        	startActivityForResult(handlerIntent, 1);
	        	isHandled = true;
	        } 
    	}

    	//Dialogs
    	{
    		if (!isHandled) {
                switch (item.getItemId()) {
                case R.id.notesButton:
                	showDialog(DIALOG_NOTES);
                	isHandled = true;
                	break;
                }
    		}
    	}

    	if (!isHandled) {
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
    	WebView bibleWebView = (WebView)findViewById(R.id.main_text);
    	bibleContentManager = new BibleContentManager(bibleWebView, this);
    	
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
            case DIALOG_NOTES:
            	final List<Note> notesList = bibleContentManager.getNotesList(CurrentPassage.getInstance().getCurrentVerse());
                SimpleAdapter adapter = new SimpleAdapter(this, notesList, 
                        R.layout.two_line_list_item_copy, 
                        new String[] {Note.SUMMARY, Note.DETAIL}, 
                        new int[] {android.R.id.text1, android.R.id.text2});
            	
           	
                return new AlertDialog.Builder(MainBibleActivity.this)
                .setTitle(R.string.notes)
                .setCancelable(true)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int selected) {
                    	Note selectedNote = notesList.get(selected);
                    	if (selectedNote.isNavigable()) {
                    		selectedNote.navigateTo();
                    	}
                    }
                })
                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int selected) {
                    	// do nothing but allow return to current page
                    }
                })
                .create();
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