package net.bible.android.control.page.window;

import org.json.JSONException;
import org.json.JSONObject;

public class WindowLayout {

	public enum WindowState {SPLIT, MINIMISED, MAXIMISED, CLOSED} 
	
	private WindowState state = WindowState.SPLIT;
	
	private float weight = 1.0f;
	

	public WindowLayout(WindowState windowState) {
		this.state = windowState;
	}
	
	public WindowState getState() {
		return state;
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
		object.put("state", state.toString())
			 .put("weight", weight);
		return object;
	}

	public void restoreState(JSONObject jsonObject) throws JSONException {
		this.state = WindowState.valueOf(jsonObject.getString("state"));
		this.weight = (float)jsonObject.getDouble("weight");
	}
}
