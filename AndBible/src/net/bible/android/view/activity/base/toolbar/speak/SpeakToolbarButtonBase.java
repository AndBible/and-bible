package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;
import net.bible.service.device.speak.event.SpeakEvent;
import net.bible.service.device.speak.event.SpeakEventListener;
import net.bible.service.device.speak.event.SpeakEventManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public abstract class SpeakToolbarButtonBase implements ToolbarButton, SpeakEventListener, OnClickListener {

	private ImageButton mButton;
	
	private SpeakControl speakControl = ControlFactory.getInstance().getSpeakControl();
	
	protected static final int SPEAK_START_PRIORITY = 10;

	private static final String TAG = "Speak";

	public SpeakToolbarButtonBase(View parent, int resourceId) {
        mButton = (ImageButton)parent.findViewById(resourceId);
        
        if (mButton!=null) {
	        mButton.setOnClickListener(this);
	
			// the manager will also instantly fire a catch-up event to ensure state is current
	        SpeakEventManager.getInstance().addSpeakEventListener(this);
        }
	}

	@Override
	public void speakStateChange(final SpeakEvent e) {
		update();
	}

	/**  return true if Speak button can be shown */
	public boolean canSpeak() {
		return //TODO COUNT BUTTONS SHOWN AND USE PRIORITY TO LIMIT BUTTON NUMBER- (numButtonsToShow()>2 || !isStrongsRelevant()) &&
				speakControl.isCurrentDocSpeakAvailable();
	}
	
	public boolean canShowFFRew() {
		//TODO remove numButtonsShown and use priority
		return canSpeak() && ToolbarButtonHelper.numButtonsToShow()>=5 && (speakControl.isSpeaking() || speakControl.isPaused());
	}

	protected ImageButton getButton() {
		return mButton;
	}
	
	protected SpeakControl getSpeakControl() {
		return speakControl;
	}
}
