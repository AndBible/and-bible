package net.bible.android.view.util;

import android.os.Handler;
import android.os.Looper;

/**
 * Simple Android thread utilities
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class Threadutils {

	public static void runOnUiThread(Runnable runnable) {
		new Handler(Looper.getMainLooper()).post(runnable);
	}
}
