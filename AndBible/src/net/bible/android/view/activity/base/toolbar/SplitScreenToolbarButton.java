package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.page.splitscreen.SplitScreenControl;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class SplitScreenToolbarButton extends ToolbarButtonBase implements ToolbarButton {

	private Button mButton;
	
	private SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
	
	public SplitScreenToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.quickSplitScreen);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	private void onButtonPress() {
    	splitScreenControl.setSplit(!splitScreenControl.isSplit());
		// redisplay the current page
		PassageChangeMediator.getInstance().forcePageUpdate();
	}

	public void update() {
		mButton.setVisibility(canShow()? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	public boolean canShow() {
		return true;
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
