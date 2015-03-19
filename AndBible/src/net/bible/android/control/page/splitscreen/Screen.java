package net.bible.android.control.page.splitscreen;

import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.Logger;

import org.json.JSONException;
import org.json.JSONObject;

public class Screen {

	private WindowLayout windowLayout;
	
	// 1 based screen no
	private int screenNo;
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	private static final String TAG = "Screen";
	
	public Screen(int screenNo, WindowState windowState) {
		this.screenNo = screenNo;
		this.windowLayout = new WindowLayout( windowState );
	}
	public Screen() {
		this.windowLayout = new WindowLayout(WindowState.SPLIT);
	}

	public int getScreenNo() {
		return screenNo;
	}

	public boolean isVisible() {
		return getWindowLayout().getState()!=WindowState.MINIMISED;
	}
	
	public JSONObject getStateJson() throws JSONException {
		JSONObject object = new JSONObject();
		object.put("screenNo", screenNo)
			 .put("windowLayout", windowLayout.getStateJson());
		return object;
	}

	public void restoreState(JSONObject jsonObject) throws JSONException {
		try {
			this.screenNo = jsonObject.getInt("screenNo");
			this.windowLayout.restoreState(jsonObject.getJSONObject("windowLayout"));
		} catch (Exception e) {
			logger.warn("Screen state restore error");
		}
	}

	public WindowLayout getWindowLayout() {
		return windowLayout;
	}
	@Override
	public String toString() {
		return "Screen [screenNo=" + screenNo + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + screenNo;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Screen other = (Screen) obj;
		if (screenNo != other.screenNo)
			return false;
		return true;
	}
}
