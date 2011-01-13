package net.bible.android.view.util.buttongrid;

import java.util.List;

import net.bible.android.view.util.buttongrid.LayoutDesigner.RowColLayout;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import example.tablekeyboard.R;

public class ButtonGrid extends TableLayout {

	public static class ButtonInfo {
		public int id;
		public String name;
		public int textColor = Color.WHITE;

		// used internally 
		private Button button;
		private int top;
		private int bottom;
		private int left;
		private int right;
		
		private int rowNo;
		private int colNo;
	}

	private ButtonInfo mCurrentPreview;
    private TextView mPreviewText;
    private PopupWindow mPreviewPopup;
//    private int mPreviewTextSizeLarge;
    private int mPreviewOffset;
    private int mPreviewHeight;
	private static final int PREVIEW_HEIGHT_DIP = 70;
	
	private OnButtonGridActionListener onButtonGridActionListener;

	private List<ButtonInfo> buttonInfoList;
	private RowColLayout mRowColLayout;
	
	private ButtonInfo mPressed;
	private Context mContext;
	private boolean isInitialised = false;


	private static final String TAG = "ButtonGrid";

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
					// create a graphical Button View object to show on the screen and link it to the ButtonInfo object
					ButtonInfo buttonInfo = buttonInfoList.get(iCellNo);
					Button button = new Button(mContext);
					button.setText(buttonInfo.name);
					button.setBackgroundResource(R.drawable.buttongrid_button_background);
					button.setTextColor(buttonInfo.textColor);
					// set pad to 0 prevents text being pushed off the bottom of buttons on small screens
					button.setPadding(0, 0, 0, 0);
					
					buttonInfo.button = button;
					buttonInfo.rowNo = iRow;
					buttonInfo.colNo = iCol;
					
					row.addView(button, cellInRowLp);
				} else {
					TextView spacer = new TextView(mContext);
					row.addView(spacer, cellInRowLp);
				}
				iCellNo++;
			}
		}

		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		mPreviewText = (TextView)inflater.inflate(R.layout.buttongrid_button_preview, null);
		mPreviewPopup = new PopupWindow(mPreviewText);
		mPreviewPopup.setContentView(mPreviewText);
		mPreviewPopup.setBackgroundDrawable(null);
		mPreviewPopup.setTouchable(false);
        mPreviewText.setCompoundDrawables(null, null, null, null);
        
        float scale = mContext.getResources().getDisplayMetrics().density;
        mPreviewHeight = (int)(PREVIEW_HEIGHT_DIP*scale);
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
					showPreview(but);
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
    	
    	close();    	
    }
	
	private boolean isInside(ButtonInfo but, float x, float y) {
		return (but.top<y && but.bottom>y &&
			but.left<x && but.right>x);
	}

	private void showPreview(ButtonInfo buttonInfo) {
		if (!buttonInfo.equals(mCurrentPreview)) {
			Log.d(TAG, "Previewing "+buttonInfo.name);
			mCurrentPreview = buttonInfo;
			mPreviewText.setText(buttonInfo.name);

            int popupHeight = mPreviewHeight;
            mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), buttonInfo.button.getWidth() + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());

            ViewGroup.LayoutParams lp = mPreviewText.getLayoutParams();
            if (lp != null) {
            	Log.d(TAG, "*** setting popup layout params width and height");
                lp.width = popupWidth;
                lp.height = popupHeight;
            }

            // where to place the popup
            int popupPreviewX;
            int popupPreviewY;
            if (buttonInfo.rowNo<2) {
        		int horizontalOffset = (2*buttonInfo.button.getWidth());
    			// if in top 2 rows then show off to right/left to avoid popup going off the screen
            	if (buttonInfo.colNo<mRowColLayout.cols/2.0) {
            		// key is on left so show to right of key
        			popupPreviewX = buttonInfo.left - mPreviewText.getPaddingLeft() + horizontalOffset;
            	} else {
            		// key is on right so show to right of key
        			popupPreviewX = buttonInfo.left - mPreviewText.getPaddingLeft() - horizontalOffset;
            	}
                popupPreviewY = buttonInfo.bottom;
            } else {
            	// show above the key above the one currently pressed 
    			popupPreviewX = buttonInfo.left - mPreviewText.getPaddingLeft();
                popupPreviewY = buttonInfo.top /*- popupHeight*/+ mPreviewOffset;
            }
			Log.d(TAG, "Y offset: "+mPreviewOffset);
			
            if (mPreviewPopup.isShowing()) {
    			Log.d(TAG, "Is showing "+buttonInfo.name+" "+mPreviewPopup.toString());
                mPreviewPopup.update(popupPreviewX, popupPreviewY, popupWidth, popupHeight);
            } else {
    			Log.d(TAG, "Is NOT showing "+buttonInfo.name+" "+mPreviewPopup.toString());
            	mPreviewPopup.setWidth(popupWidth);
                mPreviewPopup.setHeight(popupHeight);
                mPreviewPopup.showAtLocation(this, Gravity.NO_GRAVITY, popupPreviewX, popupPreviewY);
            }
			Log.d(TAG, "PP size "+mPreviewPopup.getWidth()+" "+mPreviewPopup.getHeight()+" "+popupPreviewX+":"+popupPreviewY);
            mPreviewText.setVisibility(VISIBLE);
		} else {
			// could be returning to this view via Back or Finish and the user represses same button 
			if (mPreviewText.getVisibility()!=VISIBLE) { 
				mPreviewText.setVisibility(VISIBLE);
			}
		}
	}
	
	
	
    @Override
	protected void onDetachedFromWindow() {
    	close();
    	
		// TODO Auto-generated method stub
		super.onDetachedFromWindow();
	}
    
    private void close() {
    	if (mPreviewPopup.isShowing()) {
    		mPreviewPopup.dismiss();
    	}
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
		
		// calculate offset of 2 button heights so users can see the buttons surrounding the current button pressed
		ButtonInfo but1 = buttonInfoList.get(0);
		mPreviewOffset = but1.top - but1.bottom;
    }
	/**
	 * @param onButtonGridActionListener the onButtonGridActionListener to set
	 */
	public void setOnButtonGridActionListener(
			OnButtonGridActionListener onButtonGridActionListener) {
		this.onButtonGridActionListener = onButtonGridActionListener;
	}
}
