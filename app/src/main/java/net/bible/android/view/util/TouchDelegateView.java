package net.bible.android.view.util;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

/** TouchDelegate was not working with the split WebViews so created this simple replacement.  
 * Partially overlay another view with this to redirect touch events to a delegate View
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TouchDelegateView extends View {
    private View delegate;
    
    public TouchDelegateView(Context context, View delegate) {
        super(context);
        this.delegate = delegate;
        setBackgroundColor(Color.TRANSPARENT);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return delegate.onTouchEvent(event);
    }
}

