/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.util;

import net.bible.android.activity.R;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class SeekBarPreference extends DialogPreference implements
        SeekBar.OnSeekBarChangeListener {
    private static final String androidns = "http://schemas.android.com/apk/res/android";

    private SeekBar mSeekBar;

    private TextView mDialogMessageView;

    private TextView mValueText;

    private String mSuffix;

    private int mMax, mMin, mValue = 0;
    
    @SuppressWarnings("unused")
	private static final String TAG = "SeekBarPreference";

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(true);
        
        mSuffix = attrs.getAttributeValue(androidns, "text");
        mMin = attrs.getAttributeIntValue(androidns, "min", 0);
        mMax = attrs.getAttributeIntValue(androidns, "max", 100);
        
        setDialogLayoutResource(R.layout.preference_seekbar);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        mDialogMessageView = (TextView) v.findViewById(R.id.dialogMessage);
        mDialogMessageView.setText(getDialogMessage());

        mValueText = (TextView) v.findViewById(R.id.actualValue);

        mSeekBar = (SeekBar) v.findViewById(R.id.myBar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax(mMax-mMin);
        mSeekBar.setProgress(mValue-mMin);

        String t = String.valueOf(mValue);
        mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        mValue = getPersistedInt(defaultValue == null ? 0 : (Integer) defaultValue);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            int value = mSeekBar.getProgress()+mMin;
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    public void setValue(int value) {
        if (value > mMax) {
            value = mMax;
        } else if (value < 0) {
            value = 0;
        }
        mValue = value;
        persistInt(value);
    }

    public void setMax(int max) {
        mMax = max;
        if (mValue > mMax) {
            setValue(mMax);
        }
    }

    public void setMin(int min) {
        if (min < mMax) {
            mMin = min;
        }
    }

    /** update text displays reflecting new value
     *  called as a result of changing progresBar
     * @param value
     */
    protected void updateScreenValue(int value) {
        String t = String.valueOf(value);
        mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    }

    public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
    	int newValue = value + mMin;
       
        updateScreenValue(newValue);
    }

    public void onStartTrackingTouch(SeekBar seek) {
    }

    public void onStopTrackingTouch(SeekBar seek) {
    }

	public TextView getDialogMessageView() {
		return mDialogMessageView;
	}
}
