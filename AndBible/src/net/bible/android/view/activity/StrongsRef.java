package net.bible.android.view.activity;

import javax.xml.parsers.ParserConfigurationException;

import net.bible.android.activity.R;
import net.bible.android.activity.R.id;
import net.bible.android.activity.R.layout;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.Constants;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.passage.Key;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class StrongsRef extends ActivityBase {
	private static final String TAG = "StrongsRef";

	private WebView webView;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Strongs ref");
        setContentView(R.layout.strongs_ref);
        
        webView = (WebView)findViewById(R.id.webView);
        
        String uri = getIntent().getStringExtra("URI");
        Log.d(TAG, "URI:"+uri);

        Book book = null;
        if (uri.startsWith(Constants.GREEK_DEF_PROTOCOL)) {
        	book = Defaults.getGreekDefinitions();
        } else if (uri.startsWith(Constants.HEBREW_DEF_PROTOCOL)) {
        	book = Defaults.getHebrewDefinitions();
        } else {
        	showErrorMsg(R.string.error_occurred);
        	finish();
        	return;
        }
        
        if (book==null) {
        	showErrorMsg(R.string.strongs_not_installed);
        	finish();
        	return;
        }
        
        try {
	        //TODO multiple refs
	        String ref = uri.split(":")[1];
	        Log.d(TAG, "Ref:"+ref);
	        Key key = book.getKey(ref);
	        Log.d(TAG, "Key:"+key);
        
        	String html = SwordApi.getInstance().readHtmlText(book, key, 100).getHtmlPassage();
        	webView.loadDataWithBaseURL("http://baseUrl", html, "text/html", "UTF-8", "http://historyUrl");
        	
        } catch (Exception e) {
        	Dialogs.getInstance().showErrorMsg(e.getMessage());
        	
        }
    }
}
