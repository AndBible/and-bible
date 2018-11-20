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
