package net.bible.service.common;

public class Utils {

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

	public static boolean isAndroid() {
		return isAndroid;
	}
}
