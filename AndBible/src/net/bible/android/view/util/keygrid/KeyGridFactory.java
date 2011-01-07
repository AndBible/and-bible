package net.bible.android.view.util.keygrid;

import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.view.util.keygrid.KeyGridView.KeyInfo;
import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ScrollView;

public class KeyGridFactory {

	// this is so a grid containing all the books in the bible looks nice
	private static final int MAX_KEYS_IN_SINGLE_SCREEN = 66;
	
	private static KeyGridFactory singleton = new KeyGridFactory(); 
	
	public static KeyGridFactory getInstance() {
		return singleton;
	}
	
	public KeyGridViewHolder createKeyGrid(Context context, List<KeyInfo> keys, KeyGridListener keyGridListener) {
		LayoutParams lop = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		
		KeyGridViewHolder keyGridViewHolder = new KeyGridViewHolder();
		KeyGridView keyGridView = new KeyGridView(context);
		keyGridViewHolder.keyGridView = keyGridView;

		if (keys!=null) {
			// if there are a lot of keys then wrap in a scrollView
			if (keys.size()>MAX_KEYS_IN_SINGLE_SCREEN) {
				ScrollView scrollView = new ScrollView(context, null);
				scrollView.setLayoutParams(lop);
				scrollView.addView(keyGridView);
				keyGridViewHolder.topView = scrollView;
			} else {
				keyGridViewHolder.topView = keyGridView;
			}
		}
		
        // we are only interested in the key pressed and not neighbouring keys
        keyGridView.setProximityCorrectionEnabled(false);
        
        int cols = context.getResources().getInteger(R.integer.key_grid_cols);
        Keyboard keyboard = new Keyboard(context, R.xml.key_grid_layout, getKeyCharString(keys.size()), cols, 0);
        setKeyValues(keyboard.getKeys(), keys);
        keyGridView.setKeyboard(keyboard);

        keyGridView.setOnKeyboardActionListener(new KeyHandler(keyGridListener, keys));
		
		return keyGridViewHolder;
	}
	
    /** these characters are never seen but act as ids for each key
     * 
     * @param numKeys
     * @return
     */
    private String getKeyCharString(int numKeys) {
    	StringBuilder charString = new StringBuilder(numKeys);
    	
    	for (int i=1; i<=numKeys; i++) {
    		charString.append((char)i);
    	}
    	return charString.toString();
    }

    private void setKeyValues(List<Key> keyList, List<KeyInfo> keyInfoList) {
    	assert keyList.size()==keyInfoList.size();
    	
    	for (int i=0; i<keyList.size(); i++) {
    		Key key = keyList.get(i);
    		KeyInfo keyInfo = keyInfoList.get(i);
			// this is used for preview
    		key.label = keyInfo.name;
    		// this is selected
    		key.text = Integer.toString(i);
    		
    		// if you don't do this then the key selected is about 3 below the one you click on
    		key.edgeFlags = 0;
    		
    		System.out.println("Cell width="+key.width);
    		System.out.println("Default height="+key.height);
    		System.out.println("Default x="+key.x);
    		System.out.println("Default y="+key.y);
    		System.out.println("Default gap="+key.gap);
    	}
    }

	
	public static class KeyGridViewHolder {
		public KeyGridView keyGridView;
		// large grids are wrapped in a ScrollView - but not all grids because teh ScrollView causes poorer interaction
		public View topView;
	}
}
