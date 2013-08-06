package net.bible.android.view.activity.base.toolbar;

import android.view.View;
import android.view.View.OnClickListener;

/**
 * Base class for buttons in the top toolbar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
//does not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
abstract public class ToolbarButtonBase<ButtonType extends View> implements ToolbarButton {

	private ButtonType mButton;
	
	private boolean isEnoughRoomInToolbar = false;
	private boolean isNarrow = true;
	
	abstract protected void onButtonPress();
	
	@SuppressWarnings("unchecked")
	protected ToolbarButtonBase(View parent, int buttonId) {
        mButton = (ButtonType)parent.findViewById(buttonId);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	public boolean isEnoughRoomInToolbar() {
		return isEnoughRoomInToolbar;
	}
	@Override
	public void setEnoughRoomInToolbar(boolean isRoom) {
		isEnoughRoomInToolbar = isRoom;
	}
	public boolean isNarrow() {
		return isNarrow;
	}
	@Override
	public void setNarrow(boolean isNarrow) {
		this.isNarrow = isNarrow;
	}
	
	public void update() {
		// run on ui thread
		getButton().post(new Runnable() {
			@Override
			public void run() {
				//hide/show speak button dependant on lang and speak support of lang && space available
		       	getButton().setVisibility(canShow() ? View.VISIBLE : View.GONE);
			}
		});
	}
	
	public ButtonType getButton() {
		return mButton;
	}
}
