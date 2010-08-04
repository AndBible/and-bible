package net.bible.android.activity;

 import net.bible.android.CurrentPassage;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;
import org.crosswire.jsword.book.Book;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SearchIndex extends Activity {
	private static final String TAG = "SearchIndex";
	
	private TextView mStatusTextView;
	private ProgressBar mProgressBar;
	
	private WorkListener workListener;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying SearchIndex view");
        setContentView(R.layout.search_index);
    
        mStatusTextView =  (TextView)findViewById(R.id.statusText);
        mProgressBar =  (ProgressBar)findViewById(R.id.progressBar);
        
        initialiseView();
        Log.d(TAG, "Finished displaying Search Index view");
    }

    private void initialiseView() {
        
    }

    // Indexing is too slow and fails aftr 1 hour - the experimental method below does not improve things enough to make indexing succeed 
    public void onIndex(View v) {
    	Log.i(TAG, "CLICKED");
    	showMsg("Starting index");
    	try {
	        Book book = CurrentPassage.getInstance().getCurrentDocument();
	        
	        // this starts a new thread to do the indexing and returns immediately
	        // if index creation is already in progress then nothing will happen
	        SwordApi.getInstance().ensureIndexCreation(book);
			
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}

		workListener = new WorkListener() {

			@Override
			public void workProgressed(WorkEvent ev) {
				//int total = ev.getJob().getTotalWork();
				final int done = ev.getJob().getWork();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mProgressBar.setProgress(done);
					}
				});
			}

			@Override
			public void workStateChanged(WorkEvent ev) {
				String section = ev.getJob().getSectionName();
				String msg = "Current book: "+section;
				final String status = msg;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						showMsg(status);
					}
				});
			}
			
		};
		JobManager.addWorkListener(workListener);
    }
    
    @Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
    	JobManager.removeWorkListener(workListener);
	}

    private void showMsg(String msg) {
    	mStatusTextView.setText(msg);
    }
    
}
