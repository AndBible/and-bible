package net.bible.android.view.activity.comparetranslations;

 import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.comparetranslations.TranslationDto;
import net.bible.android.view.activity.base.ListActivityBase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CompareTranslations extends ListActivityBase {
	private static final String TAG = "CompareTranslations";
	
    private List<TranslationDto> mTranslations;
    private ArrayAdapter<TranslationDto> mKeyArrayAdapter;
    
    private CompareTranslationsControl compareTranslationsControl = ControlFactory.getInstance().getCompareTranslationsControl();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Compare Translations view");
        setContentView(R.layout.list);
//TODO fetch from verse from intent if set
//TODO implement getHistoryIntent to allow correct verse to be shown if history nav occurs
        
        setTitle(compareTranslationsControl.getTitle());
        
        mTranslations = compareTranslationsControl.getAllTranslations();
        
    	mKeyArrayAdapter = new ItemAdapter(this, LIST_ITEM_TYPE, mTranslations);
        setListAdapter(mKeyArrayAdapter);

        Log.d(TAG, "Finished displaying Compare Translations view");
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		// no need to call HistoryManager.beforePageChange() here because PassageChangeMediator will tell HistoryManager a change is about to occur 
    		
	    	translationSelected(mTranslations.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
    
    private void translationSelected(TranslationDto translationDto) {
    	if (translationDto!=null) {
        	Log.i(TAG, "chose:"+translationDto.getBook());
        	
        	compareTranslationsControl.showTranslation(translationDto);
    		
    		// this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
    		returnToPreviousScreen();
    	}
    }
}
