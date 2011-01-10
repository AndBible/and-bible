package net.bible.android.view.util.buttongrid;

import java.util.List;

import net.bible.android.view.util.buttongrid.LayoutDesigner.RowColLayout;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import example.tablekeyboard.R;

public class ButtonGrid extends TableLayout {

	public static class ButtonInfo {
		public int id;
		public String name;

		// used internally 
		private Button button;
		private int top;
		private int bottom;
		private int left;
		private int right;
	}

	private OnButtonGridActionListener onButtonGridActionListener;
	
	private List<ButtonInfo> buttonInfoList;
	
	private static final String TAG = "ButtonGrid";
	
	private RowColLayout mRowColLayout;
	private ButtonInfo mPressed;
	private Context mContext;
	private boolean isInitialised = false;

	public ButtonGrid(Context context) {
        this(context, null);
	}
    public ButtonGrid(Context context, AttributeSet attrs) {
        this(context, attrs, R.style.ButtonGrid);
    }

    public ButtonGrid(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        this.mContext = context;
        // use generic ViewGroup LayoutParams for Table because we don't know what the parent is
		setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		setStretchAllColumns(true);
    }
    
    public void addButtons(List<ButtonInfo> buttonInfoList) {
    	this.buttonInfoList = buttonInfoList;
    	int numButtons = buttonInfoList.size();
    	
    	mRowColLayout = new LayoutDesigner().calculateLayout(buttonInfoList); 
		
    	TableLayout.LayoutParams rowInTableLp = new TableLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1.0f);
		TableRow.LayoutParams cellInRowLp = new TableRow.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1.0f);

		int iCellNo = 0;
		for (int iRow=0; iRow<mRowColLayout.rows; iRow++) {
			TableRow row = new TableRow(mContext);
			addView(row, rowInTableLp);

			for (int iCol=0; iCol<mRowColLayout.cols; iCol++) {
				if (iCellNo<numButtons) {
					ButtonInfo buttonInfo = buttonInfoList.get(iCellNo);
					Button but = new Button(mContext);
					but.setText(buttonInfo.name);
					but.setBackgroundResource(R.drawable.buttongrid_button_background);
					but.setTextColor(Color.WHITE);
					buttonInfo.button = but;
					row.addView(but, cellInRowLp);
				} else {
					TextView spacer = new TextView(mContext);
					row.addView(spacer, cellInRowLp);
				}
				iCellNo++;
			}
		}
	}

    
	/* (non-Javadoc)
	 * @see android.view.ViewGroup#onInterceptTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		
		// wait until the columns have been layed out and adjusted before recording button positions
		if (!isInitialised) {
			synchronized (buttonInfoList) {
				if (!isInitialised) {
					recordButtonPositions();
					isInitialised = true;
				}
			}
		}
		
		/*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */
		return true;
	}
	
    @Override
    public boolean onTouchEvent(MotionEvent event) {
		Log.d(TAG, "ME act:"+event.getAction()+" x:"+event.getX()+" y:"+event.getY());

    	switch (event.getAction()) {
    	case MotionEvent.ACTION_DOWN :
    	case MotionEvent.ACTION_MOVE :
    		ButtonInfo but = findButton((int)event.getX(), (int)event.getY());
			if (but!=null) {
				// show the button being pressed
				if (!but.equals(mPressed)) {
					if (mPressed!=null) {
						mPressed.button.setPressed(false);
					}
					but.button.setPressed(true);
					mPressed = but;
				}
			}
			break;
    	case MotionEvent.ACTION_UP :
    		if (mPressed!=null) {
    			buttonSelected(mPressed);
    		}
    		break;
    	}
    	
		return true; //super.onInterceptTouchEvent(ev);
	}

    private ButtonInfo findButton(int x, int y) {
		for (ButtonInfo but : buttonInfoList) {
			if (isInside(but, x, y)) {
				Log.d(TAG, "IsInside "+but.name);
				return but;
			}
		}
		return null;
    }
    
    private void buttonSelected(ButtonInfo selectedButton) {
    	Log.i(TAG, "Selected:"+selectedButton.name);
    	if (onButtonGridActionListener!=null) {
    		onButtonGridActionListener.buttonPressed(selectedButton);
    	}
    	int bookNo = selectedButton.id;
    	
    }
	
	private boolean isInside(ButtonInfo but, float x, float y) {
		return (but.top<y && but.bottom>y &&
			but.left<x && but.right>x);
	}
	
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    	super.onLayout(changed, l, t, r, b);

    }

    /** calculate button position relative to this table because MotionEvents are relative to this table
     */
    private void recordButtonPositions() {
		for (ButtonInfo buttonInfo : buttonInfoList) {
			Log.d(TAG, "button:"+buttonInfo.name);
			
			// get position of button within row
			Button button = buttonInfo.button;
			TableRow tableRow = (TableRow)button.getParent();
			
			buttonInfo.left += button.getLeft()+tableRow.getLeft();
			buttonInfo.top += button.getTop()+tableRow.getTop();
			buttonInfo.right += button.getRight()+tableRow.getLeft();
			buttonInfo.bottom += button.getBottom()+tableRow.getTop();
		}
    }
	/**
	 * @param onButtonGridActionListener the onButtonGridActionListener to set
	 */
	public void setOnButtonGridActionListener(
			OnButtonGridActionListener onButtonGridActionListener) {
		this.onButtonGridActionListener = onButtonGridActionListener;
	}
}
