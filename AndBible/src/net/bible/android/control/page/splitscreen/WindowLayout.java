package net.bible.android.control.page.splitscreen;

import org.json.JSONException;
import org.json.JSONObject;

public class WindowLayout {

	public enum WindowState {MINIMISED, SPLIT} 
	
	private WindowState state = WindowState.SPLIT;
	
	private boolean isSynchronised = true;
	
	private float weight = 1.0f;
	

	public WindowLayout(WindowState windowState) {
		this.state = windowState;
	}
	
	public WindowState getState() {
		return state;
	}
	
	public boolean isSynchronised() {
		return isSynchronised;
	}
	
	public void setSynchronised(boolean isSynchronised) {
		this.isSynchronised = isSynchronised;
	}

	public void setState(WindowState state) {
		this.state = state;
	}
	
	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	
	public JSONObject getStateJson() throws JSONException {
		JSONObject object = new JSONObject();
		object.put("state", state)
			 .put("isSynchronised", isSynchronised)
			 .put("weight", weight);
		return object;
	}

	public void restoreState(JSONObject jsonObject) throws JSONException {
		this.state = WindowState.valueOf(jsonObject.getString("state"));
		this.isSynchronised = jsonObject.getBoolean("isSynchronised");
		this.weight = (float)jsonObject.getDouble("weight");
	}
}
