package net.bible.android.activity;

import javax.xml.parsers.ParserConfigurationException;

import net.bible.android.activity.base.ActivityBase;
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
        	showErrorMsg("Error");
        	finish();
        	return;
        }
        
        if (book==null) {
        	showErrorMsg("Install Strong's Hebrew and Greek Bible Dictionaries");
        	finish();
        	return;
        }
        
        try {
	        //TODO multiple refs
	        String refs = uri.split(":")[1];
	        Log.d(TAG, "Ref:"+refs);
	        refs = refs.replace("+", " ");
	        String[] refList = refs.split(" ");
	        Key key = book.createEmptyKeyList();
	        for (String ref : refList) {
	        	key.addAll(book.getKey(ref));
	        }
	        Log.d(TAG, "Key:"+key);
        
        	String html = SwordApi.getInstance().readHtmlText(book, key, 100).getHtmlPassage();
        	webView.loadDataWithBaseURL("http://baseUrl", html, "text/html", "UTF-8", "http://historyUrl");
        	
        } catch (Exception e) {
        	showErrorMsg(e.getMessage());
        	
        }
    }
}
