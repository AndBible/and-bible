package net.bible.service.device.speak.event;

public class SpeakEvent {

	public enum SpeakState {SPEAKING, PAUSED, SILENT};
	
	private SpeakState speakState;
	
	public SpeakEvent(SpeakState newSpeakState) {
		this.speakState = newSpeakState;
	}

	public SpeakState getSpeakState() {
		return speakState;
	}
	
	public boolean isSpeaking() {
		return speakState == SpeakState.SPEAKING;
	}

	public boolean isPaused() {
		return speakState == SpeakState.PAUSED;
	}
}
