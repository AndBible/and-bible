package example.tablekeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.TableLayout;

public class StartActivity1 extends Activity implements OnTouchListener {
	
	private static final String TAG = "StartActivity1";
	
	Button but1;
	Button but2;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        
        but1 = (Button)findViewById(R.id.but1);
        but1.setOnTouchListener(this);
        but2 = (Button)findViewById(R.id.but2);
        but2.setOnTouchListener(this);
        
        TableLayout tl = (TableLayout)findViewById(R.id.TableLayout01);
        tl.setOnTouchListener(this);
    }

    
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		Log.d(TAG, "ME:"+v.getId()+" act:"+event.getAction()+" x:"+event.getX()+" y:"+event.getY());
		Log.d(TAG, "But1:"+but1.getTop()+" "+but1.getLeft());
		Log.d(TAG, "But2:"+but2.getTop()+" "+but2.getLeft());
		if (isInside(but1, event.getRawX(), event.getRawY())) {
			Log.d(TAG, "BUT1");
		}
		if (isInside(but2, event.getRawX(), event.getRawY())) {
			Log.d(TAG, "BUT2");
		}
		return true;
	}
    
    private boolean isInside(Button but, float x, float y) {
    	return (but.getTop()<y && but.getBottom()>y &&
    		but.getLeft()<x && but.getRight()>x);
    }
}