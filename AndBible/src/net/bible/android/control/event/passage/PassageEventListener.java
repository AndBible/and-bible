package net.bible.android.control.event.passage;

import java.util.EventListener;

public interface PassageEventListener extends EventListener {
	
	void pageDetailChange(PassageEvent event);

}
