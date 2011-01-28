package net.bible.android.view.util.keygrid;

import android.content.Context;
import android.inputmethodservice.KeyboardView;

public class KeyGridView extends KeyboardView {

	public KeyGridView(Context context) {
		super(context, null);
	}

	public static class KeyInfo {
		public int id;
		public String name;
	}
}
