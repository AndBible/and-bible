package net.bible.service.device.speak.event;

import java.util.EventListener;

public interface SpeakEventListener extends EventListener {
	void speakStateChange(SpeakEvent e);
}
