/**
 * 
 */
package net.bible.android.view.util.keygrid;

import java.util.List;

import net.bible.android.view.util.keygrid.KeyGridView.KeyInfo;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.util.Log;

/**
 * @author denha1m
 *
 */
public class KeyHandler implements OnKeyboardActionListener {

	private KeyGridListener keyGridListener;
	private List<KeyInfo> keyInfoList;
	
	private static final String TAG = "KeyHandler";
	

	public KeyHandler(KeyGridListener keyGridListener, List<KeyInfo> keyInfoList) {
		super();
		this.keyGridListener = keyGridListener;
		this.keyInfoList = keyInfoList;
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#onKey(int, int[])
	 */
	@Override
	public void onKey(int primaryCode, int[] keyCodes) {
		Log.d(TAG, "onKey:"+primaryCode);
		
		//this is never called
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#onPress(int)
	 */
	@Override
	public void onPress(int primaryCode) {
		Log.d(TAG, "onPress:"+primaryCode);
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#onRelease(int)
	 */
	@Override
	public void onRelease(int primaryCode) {
		Log.d(TAG, "onRelease:"+primaryCode);
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#onText(java.lang.CharSequence)
	 */
	@Override
	public void onText(CharSequence text) {
		Log.d(TAG, "onText:"+text);
		int selectedIndex = Integer.parseInt(text.toString());
		KeyInfo selectedKeyInfo = keyInfoList.get(selectedIndex);
		keyGridListener.keyPressed(selectedKeyInfo);
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#swipeDown()
	 */
	@Override
	public void swipeDown() {
		Log.d(TAG, "swipeDown");
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#swipeLeft()
	 */
	@Override
	public void swipeLeft() {
		Log.d(TAG, "swipeLeft");
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#swipeRight()
	 */
	@Override
	public void swipeRight() {
		Log.d(TAG, "swipeRight");
	}

	/* (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#swipeUp()
	 */
	@Override
	public void swipeUp() {
		Log.d(TAG, "swipeUp");
	}

}
