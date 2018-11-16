package net.bible.android.view.util.locale;

import android.content.Context;

/**
 * Version specific locale overrider
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public interface LocaleChanger {
	Context changeLocale(Context context, String language);
}
