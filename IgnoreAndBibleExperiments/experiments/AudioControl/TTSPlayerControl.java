package net.bible.android.view.activity.speak;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.speak.SpeakControl;
import android.util.Log;
import android.widget.MediaController.MediaPlayerControl;

public class TTSPlayerControl implements MediaPlayerControl {

	private SpeakControl mSpeakControl = ControlFactory.getInstance().getSpeakControl();
	
	private static final String TAG = "TTSPlayerControl";
	
	@Override
	public void start() {
		Log.d(TAG, "start");
		mSpeakControl.continueAfterPause();
	}

	@Override
	public void pause() {
		Log.d(TAG, "pause");
		mSpeakControl.pause();
	}

	@Override
	public int getDuration() {
		Log.d(TAG, "getDuration");

		return 0;
	}

	@Override
	public int getCurrentPosition() {
		// TODO Auto-generated method stub
		Log.d(TAG, "getCurrentPosition");
		return 0;
	}

	@Override
	public void seekTo(int pos) {
		Log.d(TAG, "seekTo:"+pos);
	}

	@Override
	public boolean isPlaying() {
		boolean isPlaying = mSpeakControl.isSpeaking();
		Log.d(TAG, "isPlaying: "+isPlaying);
		return true;
	}

	@Override
	public int getBufferPercentage() {
		Log.d(TAG, "getBufferPercentage");
		return 0;
	}

	@Override
	public boolean canPause() {
		Log.d(TAG, "canPause");
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}
}
