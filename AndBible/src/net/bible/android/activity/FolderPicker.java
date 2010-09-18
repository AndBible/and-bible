package net.bible.android.activity;

import java.io.File;
import java.util.ArrayList;

import net.bible.android.SharedConstants;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * This isn't used in the app
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class FolderPicker extends ListActivity {

    private final String TAG = "File Chooser";

    protected ArrayList<File> mFileList;
    protected File mRoot;
    private boolean mRadio;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Log.i(TAG, "called onCreate");

        setContentView(R.layout.folderpicker);
        
        initialize("Folder picker title", SharedConstants.MODULE_PATH, false);
    }


    public void initialize(String title, String path, Boolean radio) {
        setTitle(getString(R.string.app_name) + " > " + title);
        mRadio = radio;
        mFileList = new ArrayList<File>();
        if (getDirectory(path)) {
            getFolders(mRoot);
            displayFiles();
        }
    }


    public void refreshRoot() {
        getFolders(mRoot);
        displayFiles();
    }


    private boolean getDirectory(String path) {

        TextView tv = (TextView) findViewById(R.id.folderpicker_message);

        // if storage directory does not exist, create it.
        boolean made = true;
        mRoot = new File(path);
        if (!mRoot.exists()) {
            made = mRoot.mkdirs();
        }

        if (!made) {
            tv.setText(getString(R.string.directory_error, path));
            return false;
        } else {
            return true;
        }
    }



    private void getFolders(File f) {
    	mFileList.clear();
        mFileList.add(new DotDot(f.getParentFile()));
        if (f.isDirectory()) {

            File[] childs = f.listFiles();
            for (File child : childs) {
                if (child.isDirectory()) {
                	mFileList.add(child);
                }
            }
            return;
        }
    }

    /**
     * Opens the directory, puts valid files in array adapter for display
     */
    private void displayFiles() {

        ArrayAdapter<File> fileAdapter;
//        Collections.sort(mFileList, String.CASE_INSENSITIVE_ORDER);

        if (mRadio) {
            getListView().setItemsCanFocus(false);
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            fileAdapter =
                    new ArrayAdapter<File>(this, android.R.layout.simple_list_item_single_choice,
                            mFileList);
        } else {
            fileAdapter =
                    new ArrayAdapter<File>(this, android.R.layout.simple_list_item_1, mFileList);
        }

        setListAdapter(fileAdapter);
    }


    /**
     * Stores the path of clicked file in the intent and exits.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        File f = mFileList.get(position);
    	try {
	        getFolders(f);
	        displayFiles();
	        this.mRoot = f;
    	} catch (Exception e) {
            TextView tv = (TextView) findViewById(R.id.folderpicker_message);
            tv.setText(getString(R.string.directory_error, f.getName()));
            Log.e(TAG, "Error accessing dir", e);
    	}
    }

    public void onClickUseFolder(View v) {
       	Log.i(TAG, "Displaying select module dir");
       	try {
	        Intent i = new Intent();
	        i.putExtra(SharedConstants.FOLDERPATH_KEY, mRoot.getAbsolutePath());
	        setResult(RESULT_OK, i);
	
	        finish();
		} catch (Exception e) {
	        TextView tv = (TextView) findViewById(R.id.folderpicker_message);
	        tv.setText(getString(R.string.directory_error, mRoot.getName()));
	        Log.e(TAG, "Error selecting dir", e);
		}
    }

}

class DotDot extends File {
	
	public DotDot(File dir) {
		super(dir.getAbsolutePath());
	}

	@Override
	public String toString() {
		return "..";
	}
}

