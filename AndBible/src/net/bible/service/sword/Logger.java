package net.bible.service.sword;

/** Not sure whether to use Log or jdk logger or log4j.
 * Log requires the android classes and is used for front end classes but these classes really belong in the back end
 * 
 * @author denha1m
 *
 */
public class Logger {
	private String name;
	public Logger(String name) {
		this.name = name;
	}
	public void debug(String s) {
		System.out.println(name+":"+s);
	}
	public void error(String s) {
		System.out.println(name+":"+s);
	}
	public void error(String s, Exception e) {
		System.out.println(name+":"+s);
		e.printStackTrace();
	}
}
