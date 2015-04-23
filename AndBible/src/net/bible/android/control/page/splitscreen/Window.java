package net.bible.android.control.page.splitscreen;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.CurrentPageManagerFactory;
import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;
import net.bible.service.common.Logger;

import org.json.JSONException;
import org.json.JSONObject;

public class Window {
	
	public enum WindowOperation {
		MAXIMISE, MINIMISE, RESTORE, DELETE 
	}

	private WindowOperation defaultOperation;
	
	private boolean isSynchronised = true;
	
	private WindowLayout windowLayout;
	
	private CurrentPageManager currentPageManager;
	
	private static CurrentPageManagerFactory currentPageManagerFactory = new CurrentPageManagerFactory();
	
	// 1 based screen no
	private int screenNo;
	
	private final Logger logger = new Logger(this.getClass().getName());
	
	public Window(int screenNo, WindowState windowState) {
		this.screenNo = screenNo;
		this.windowLayout = new WindowLayout( windowState );
		
		this.defaultOperation = WindowOperation.MINIMISE;
	}

	/**
	 * Used when restoring state
	 */
	public Window() {
		this.windowLayout = new WindowLayout(WindowState.SPLIT);
		this.defaultOperation = WindowOperation.MINIMISE;
	}

	public CurrentPageManager getPageManager() {
		//TODO use a factory
		// for now lazily create to prevent NPE on start up due to circular dependency
		if (currentPageManager==null) {
			this.currentPageManager = currentPageManagerFactory.createCurrentPageManager(this);
		}
		return currentPageManager;
	}

	public int getScreenNo() {
		return screenNo;
	}

	public boolean isSynchronised() {
		return isSynchronised;
	}
	
	public void setSynchronised(boolean isSynchronised) {
		this.isSynchronised = isSynchronised;
	}

	public boolean isVisible() {
		return 	getWindowLayout().getState()!=WindowState.MINIMISED &&
				getWindowLayout().getState()!=WindowState.REMOVED;
	}

	
	public WindowOperation getDefaultOperation() {
		return defaultOperation;
	}
	public void setDefaultOperation(WindowOperation defaultOperation) {
		this.defaultOperation = defaultOperation;
	}

	public JSONObject getStateJson() throws JSONException {
		JSONObject object = new JSONObject();
		object.put("screenNo", screenNo)
			.put("isSynchronised", isSynchronised)
			.put("windowLayout", windowLayout.getStateJson());
		return object;
	}

	public void restoreState(JSONObject jsonObject) throws JSONException {
		try {
			this.screenNo = jsonObject.getInt("screenNo");
			this.isSynchronised = jsonObject.getBoolean("isSynchronised");
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
