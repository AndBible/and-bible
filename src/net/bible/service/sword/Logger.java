package net.bible.service.sword;

import net.bible.service.common.Utils;
import android.util.Log;

/** Not sure whether to use Log or jdk logger or log4j.
 * Log requires the android classes and is used for front end classes but these classes really belong in the back end
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Logger {

	private String name;
	
	private static final boolean isAndroid = Utils.isAndroid();
	
	
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
