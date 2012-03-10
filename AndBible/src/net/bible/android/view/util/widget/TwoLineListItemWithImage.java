package net.bible.android.view.util.widget;

import net.bible.android.activity.R;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TwoLineListItem;

/** Add an image to the normal 2 line list item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TwoLineListItemWithImage extends TwoLineListItem {

	private ImageView mIcon;
	
//	private static final String TAG = "TwoLineListItemWithImage";
	
	public TwoLineListItemWithImage(Context context) {
		super(context);
	}

	public TwoLineListItemWithImage(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TwoLineListItemWithImage(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIcon = (ImageView) findViewById(R.id.icon);
    }

	public ImageView getIcon() {
		return mIcon;
	}

	public void setIcon(ImageView icon) {
		mIcon = icon;
	}

}
