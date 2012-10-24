package net.bible.android.view.activity.speak;

import net.bible.android.activity.R;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;

public class AudioControls {

	private MediaController mTTSMediaController;
	
	private MediaPlayerControl ttsPlayerControl = new TTSPlayerControl();
	
	private static final String TAG="AudioControls"; 
	
	
	public void addTo(Activity activity, View attachTo) {
		Log.d(TAG, "addTo");
		if (mTTSMediaController == null) {
			Log.d(TAG, "creating new MediaController");
			mTTSMediaController = new TTSControllerWidget(activity);
		}

        mTTSMediaController.setMediaPlayer(ttsPlayerControl);
        View anchorView = attachTo.getParent() instanceof View ? (View)attachTo.getParent() : attachTo;
        mTTSMediaController.setAnchorView(anchorView);
        mTTSMediaController.setEnabled(true); //mIsPrepared);
        mTTSMediaController.setBackgroundColor(0xFF848284);
        mTTSMediaController.show(0);
	}
}
