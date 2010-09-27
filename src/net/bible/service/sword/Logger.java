package net.bible.service.sword;

/** Not sure whether to use Log or jdk logger or log4j.
 * Log requires the android classes and is used for front end classes but these classes really belong in the back end
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Logger {

	private String name;
	
	static private boolean isAndroid;
	
	//todo have to finish implementing switchable logging here
	static {
		try {
			Class.forName("android.util.Log");
			isAndroid = true;
		} catch (ClassNotFoundException cnfe) {
			isAndroid = false;
		}
		System.out.println("isAndroid:"+isAndroid);
	}
	
	public Logger(String name) {
		this.name = name;
	}
	public void debug(String s) {
		System.out.println(name+":"+s);
	}
	public void info(String s) {
		System.out.println(name+":"+s);
	}
	public void warn(String s) {
		System.out.println(name+":"+s);
	}
	public void warn(String s, Exception e) {
		System.out.println(name+":"+s);
		e.printStackTrace();
	}
	public void error(String s) {
		System.out.println(name+":"+s);
	}
	public void error(String s, Exception e) {
		System.out.println(name+":"+s);
		e.printStackTrace();
	}
}
