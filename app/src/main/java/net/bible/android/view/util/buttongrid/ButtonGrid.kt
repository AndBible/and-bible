/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.util.buttongrid

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.view.util.buttongrid.LayoutDesigner.RowColLayout

class ButtonInfo (
    var id: Int = 0,
    var name: String? = null,
    var textColor: Int = Color.WHITE,
    var highlight: Boolean = false,

    var top: Int = 0,
    var bottom: Int = 0,
    var left: Int = 0,
    var right: Int = 0,
    var rowNo: Int = 0,
    var colNo: Int = 0
) {
    lateinit var button: Button
}

/** Show a grid of buttons to allow selection for navigation
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ButtonGrid constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : TableLayout(context, attrs) {
    private var currentPreview: ButtonInfo? = null
    private var previewText: TextView? = null
    private var previewPopup: PopupWindow? = null
    private var previewOffset = 0
    private var previewHeight = 0
    private var onButtonGridActionListener: OnButtonGridActionListener? = null
    private var buttonInfoList: List<ButtonInfo>? = null
    private var rowColLayout: RowColLayout? = null
    private var pressed: ButtonInfo? = null
    private var isInitialised = false

    fun clear() {
        removeAllViews()
        buttonInfoList = null
        rowColLayout = null
        pressed = null
        isInitialised = false
    }

    /** Called during initialisation to add the list of buttons to be laid out on the screen
     *
     * @param buttonInfoList
     */
    fun addButtons(buttonInfoList: List<ButtonInfo>) {
        this.buttonInfoList = buttonInfoList
        val numButtons = buttonInfoList.size
        val textSize = resources.getInteger(R.integer.grid_cell_text_size_sp)

        // calculate the number of rows and columns so that the grid looks nice
        val rowColLayout = LayoutDesigner(this).calculateLayout(buttonInfoList)
        this.rowColLayout = rowColLayout
        val rowInTableLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f)
        val cellInRowLp = TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f)
        for (iRow in 0 until rowColLayout.rows) {
            val row = TableRow(context)
            addView(row, rowInTableLp)
            for (iCol in 0 until rowColLayout.cols) {
                val buttonInfoIndex = getButtonInfoIndex(iRow, iCol)
                if (buttonInfoIndex < numButtons) {
                    // create a graphical Button View object to show on the screen and link it to the ButtonInfo object
                    val buttonInfo = buttonInfoList[buttonInfoIndex]
                    val button = Button(context)
                    button.text = buttonInfo.name
                    button.setBackgroundResource(R.drawable.buttongrid_button_background)
                    button.setTextColor(buttonInfo.textColor)
                    if (buttonInfo.highlight) {
                        button.setTypeface(Typeface.DEFAULT_BOLD)
                        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize + 1.toFloat())
                        button.paintFlags = button.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    } else {
                        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                    }
                    // set pad to 0 prevents text being pushed off the bottom of buttons on small screens
                    button.setPadding(0, 0, 0, 0)
                    button.isAllCaps = false
                    buttonInfo.button = button
                    buttonInfo.rowNo = iRow
                    buttonInfo.colNo = iCol
                    row.addView(button, cellInRowLp)
                } else {
                    val spacer = TextView(context)
                    row.addView(spacer, cellInRowLp)
                }
            }
        }
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        previewText = inflater.inflate(R.layout.buttongrid_button_preview, null) as TextView
        val previewPopup = PopupWindow(previewText)
        this.previewPopup = previewPopup
        previewPopup.contentView = previewText
        previewPopup.setBackgroundDrawable(null)
        previewPopup.isTouchable = false
        previewText!!.setCompoundDrawables(null, null, null, null)
        val scale = context.resources.displayMetrics.density
        previewHeight = (PREVIEW_HEIGHT_DIP * scale).toInt()
    }

    /** Ensure longer runs by populating in longest direction ie columns if portrait and rows if landscape
     *
     * @param row
     * @param col
     * @return
     */
    private fun getButtonInfoIndex(row: Int, col: Int): Int {
        return if (rowColLayout!!.columnOrder) {
            col * rowColLayout!!.rows + row
        } else {
            row * rowColLayout!!.cols + col
        }
    }

    /* (non-Javadoc)
	 * @see android.view.ViewGroup#onInterceptTouchEvent(android.view.MotionEvent)
	 */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {

        // wait until the columns have been layed out and adjusted before recording button positions
        if (!isInitialised) {
            synchronized(buttonInfoList!!) {
                if (!isInitialised) {
                    recordButtonPositions()
                    isInitialised = true
                }
            }
        }

        /*
		 * This method JUST determines whether we want to intercept the motion.
		 * If we return true, onMotionEvent will be called and we do the actual
		 * scrolling there.
		 */return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val but = findButton(event.x.toInt(), event.y.toInt())
                if (but != null) {
                    // show the button being pressed
                    if (but != pressed) {
                        if (pressed != null) {
                            pressed!!.button.isPressed = false
                        }
                        but.button.isPressed = true
                        pressed = but
                        showPreview(but)
                    }
                }
            }
            MotionEvent.ACTION_UP -> if (pressed != null) {
                buttonSelected(pressed!!)
            }
        }
        return true //super.onInterceptTouchEvent(ev);
    }

    private fun findButton(x: Int, y: Int): ButtonInfo? {
        for (but in buttonInfoList!!) {
            if (isInside(but, x.toFloat(), y.toFloat())) {
                return but
            }
        }
        return null
    }

    private fun buttonSelected(selectedButton: ButtonInfo) {
        Log.i(TAG, "Selected:" + selectedButton.name)
        if (onButtonGridActionListener != null) {
            onButtonGridActionListener!!.buttonPressed(selectedButton)
        }
        close()
    }

    private fun isInside(but: ButtonInfo, x: Float, y: Float): Boolean {
        return but.top < y && but.bottom > y && but.left < x && but.right > x
    }

    private fun showPreview(buttonInfo: ButtonInfo) {
        try {
            val button = buttonInfo.button
            if (buttonInfo != currentPreview) {
                Log.d(TAG, "Previewing " + buttonInfo.name)
                currentPreview = buttonInfo
                previewText!!.text = buttonInfo.name
                val popupHeight = previewHeight
                previewText!!.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val popupWidth = Math.max(previewText!!.measuredWidth, buttonInfo.button!!.width + previewText!!.paddingLeft + previewText!!.paddingRight)
                val lp = previewText!!.layoutParams
                if (lp != null) {
                    lp.width = popupWidth
                    lp.height = popupHeight
                }

                // where to place the popup
                val popupPreviewX: Int
                val popupPreviewY: Int
                if (buttonInfo.rowNo < 2) {
                    val horizontalOffset = 2 * buttonInfo.button.width
                    // if in top 2 rows then show off to right/left to avoid popup going off the screen
                    popupPreviewX = if (buttonInfo.colNo < rowColLayout!!.cols / 2.0) {
                        // key is on left so show to right of key
                        buttonInfo.left - previewText!!.paddingLeft + horizontalOffset
                    } else {
                        // key is on right so show to right of key
                        buttonInfo.left - previewText!!.paddingLeft - horizontalOffset
                    }
                    popupPreviewY = buttonInfo.bottom
                } else {
                    // show above the key above the one currently pressed
                    popupPreviewX = buttonInfo.left - previewText!!.paddingLeft
                    popupPreviewY = buttonInfo.top /*- popupHeight*/ + previewOffset
                }
                if (previewPopup!!.isShowing) {
                    previewPopup!!.update(popupPreviewX, popupPreviewY, popupWidth, popupHeight)
                } else {
                    previewPopup!!.width = popupWidth
                    previewPopup!!.height = popupHeight
                    previewPopup!!.showAtLocation(this, Gravity.NO_GRAVITY, popupPreviewX, popupPreviewY)
                }
                previewText!!.visibility = View.VISIBLE
            } else {
                // could be returning to this view via Back or Finish and the user represses same button
                if (previewText!!.visibility != View.VISIBLE) {
                    previewText!!.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            // avoid very occasional NPE deep in Android code by catching and ignoring because showing preview is optional
            Log.w(TAG, "Error showing button grid preview", e)
        }
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }

    private fun close() {
        // avoid errors on Lenovo tablet in dismiss
        try {
            if (previewPopup!!.isShowing) {
                previewPopup!!.dismiss()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ButtonGrid preview")
        }
    }

    /** calculate button position relative to this table because MotionEvents are relative to this table
     */
    private fun recordButtonPositions() {
        for (buttonInfo in buttonInfoList!!) {

            // get position of button within row
            val button = buttonInfo.button
            val tableRow = button.parent as TableRow
            buttonInfo.left += button.left + tableRow.left
            buttonInfo.top += button.top + tableRow.top
            buttonInfo.right += button.right + tableRow.left
            buttonInfo.bottom += button.bottom + tableRow.top
        }

        // calculate offset of 2 button heights so users can see the buttons surrounding the current button pressed
        if (buttonInfoList!!.size > 0) {
            val but1 = buttonInfoList!![0]
            previewOffset = but1.top - but1.bottom
        }
    }

    /**
     * @param onButtonGridActionListener the onButtonGridActionListener to set
     */
    fun setOnButtonGridActionListener(
        onButtonGridActionListener: OnButtonGridActionListener?) {
        this.onButtonGridActionListener = onButtonGridActionListener
    }

    companion object {
        private const val PREVIEW_HEIGHT_DIP = 70
        private const val TAG = "ButtonGrid"
    }

    init {

        // use generic ViewGroup LayoutParams for Table because we don't know what the parent is
        layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isStretchAllColumns = true
    }
}
