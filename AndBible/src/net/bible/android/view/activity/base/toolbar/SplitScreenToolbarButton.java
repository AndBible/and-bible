package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.page.splitscreen.SplitScreenControl;

import android.view.View;
import android.widget.Button;

public class SplitScreenToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
	
	public SplitScreenToolbarButton(View parent) {
        super(parent, R.id.quickSplitScreen);
	}

	@Override
	protected void onButtonPress() {
    	splitScreenControl.setSplit(!splitScreenControl.isSplit());
		// redisplay the current page
		PassageChangeMediator.getInstance().forcePageUpdate();
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
