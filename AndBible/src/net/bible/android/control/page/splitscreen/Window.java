package net.bible.android.control.page.splitscreen;

import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.Logger;

import org.json.JSONException;
import org.json.JSONObject;

public class Window {

	private WindowLayout windowLayout;
	
	// 1 based screen no
	private int screenNo;
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	private static final String TAG = "Window";
	
	public Window(int screenNo, WindowState windowState) {
		this.screenNo = screenNo;
		this.windowLayout = new WindowLayout( windowState );
	}
	public Window() {
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
			logger.warn("Window state restore error");
		}
	}

	public WindowLayout getWindowLayout() {
		return windowLayout;
	}
	@Override
	public String toString() {
		return "Window [screenNo=" + screenNo + "]";
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
		Window other = (Window) obj;
		if (screenNo != other.screenNo)
			return false;
		return true;
	}
}
