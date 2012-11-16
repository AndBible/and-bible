package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.service.common.CommonUtils;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

//TODO do not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class QuickCommentaryButton extends Button implements ToolbarButton {

	public QuickCommentaryButton(Context context) {
		this(context, null);
	}


	public QuickCommentaryButton(Context context, AttributeSet attrs) {
		super(context, attrs);
//		this(context, attrs, R.style.TitleBarButton);
//	}
//	
//	public QuickCommentaryButton(Context context, AttributeSet attrs, int defStyle) {
//		super(context, attrs, defStyle);
		
//		int widthPix = context.getResources().getDimensionPixelSize(R.dimen.size_of_quick_buttons);
//		
////		setBackgroundResource(R.drawable.btn_tiny_toggle); // strongs only
//		setBackgroundResource(R.drawable.btn_tiny);
//		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(widthPix, LayoutParams.FILL_PARENT);
//		layoutParams.setMargins(0, 0, 0, CommonUtils.convertDipsToPx(1));
//
//		//skip centreinparent style
////		layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, 1);
//		setLayoutParams( layoutParams );
//		setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//		setSingleLine(true);
//		setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
//		setTextColor(Color.WHITE); //0xFFFFF0);
////		setPadding(CommonUtils.convertDipsToPx(7), CommonUtils.convertDipsToPx(7), 0, 0);
//
//		//extras
//		setWidth(widthPix);
//		setText(ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary().getInitials());
//
//		setPadding(0, 0, 0, CommonUtils.convertDipsToPx(1));
	}



	@Override
	public int getPriority() {
		return 1;
	}

	@Override
	public int getOrder() {
		return 1;
	}

}
