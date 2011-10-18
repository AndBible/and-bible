package net.bible.android.view.activity.mynote;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.view.activity.base.DocumentView;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;

public class MyNoteEditTextView extends EditText implements DocumentView {

	private MyNote myNoteControl = ControlFactory.getInstance().getMyNoteControl();

	public MyNoteEditTextView(Context context) {
		super(context);
		setSingleLine(false);
		setGravity(Gravity.TOP);
		setBackgroundColor(0);
	}

	@Override
	public void save() {
		myNoteControl.saveMyNoteText(getText().toString());		
	}

	@Override
	public void selectAndCopyText() {
	}

	@Override
	public void show(String html, int jumpToVerse, float jumpToYOffsetRatio) {
		setText(html);
	}

	@Override
	public void applyPreferenceSettings() {
	}

	@Override
	public boolean pageDown(boolean toBottom) {
		return false;
	}

	@Override
	public float getCurrentPosition() {
		return 0;
	}

	@Override
	public View asView() {
		return this;
	}
}
