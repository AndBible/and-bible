package net.bible.android.control.event.passage;

import java.util.EventListener;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface PassageEventListener extends EventListener {
	
	void pageDetailChange(PassageEvent event);

}
