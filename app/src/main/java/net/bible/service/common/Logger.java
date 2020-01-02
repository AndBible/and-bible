/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.service.common;

import android.util.Log;

/** Not sure whether to use Log or jdk logger or log4j.
 * Log requires the android classes and is used for front end classes but these classes really belong in the back end
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Logger {

	private String name;
	
	private static final boolean isAndroid = TestUtils.isAndroid();
	
	public Logger(String name) {
		this.name = name;
	}
	public void debug(String s) {
		if (isAndroid) {
			Log.d(name, s);
		} else {
			System.out.println(name+":"+s);
		}
	}
	public void info(String s) {
		if (isAndroid) {
			Log.i(name, s);
		} else {
			System.out.println(name+":"+s);
		}
	}
	public void warn(String s) {
		if (isAndroid) {
			Log.w(name, s);
		} else {
			System.out.println(name+":"+s);
		}
	}
	public void warn(String s, Exception e) {
		if (isAndroid) {
			Log.e(name, s, e);
		} else {
			System.out.println(name+":"+s);
			e.printStackTrace();
		}
	}
	public void error(String s) {
		if (isAndroid) {
			Log.e(name, s);
		} else {
			System.out.println(name+":"+s);
		}
	}

	public void error(String s, Exception e) {
		if (isAndroid) {
			Log.e(name, s, e);
		} else {
			System.out.println(name+":"+s);
			e.printStackTrace();
		}
	}
}
