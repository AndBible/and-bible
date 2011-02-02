package net.bible.service.common;

/** support junit tests
 * 
 * @author denha1m
 *
 */
public class TestUtils {
	
	private static boolean isAndroid;
	private static boolean isAndroidCheckDone;
	
	/** return true id running in an Android vm
	 * 
	 * @return
	 */
	public static boolean isAndroid() {
		if (!isAndroidCheckDone) {
			try {
				Class.forName("android.util.Log");
				isAndroid = true;
			} catch (ClassNotFoundException cnfe) {
				isAndroid = false;
			}
			isAndroidCheckDone = true;
		}
		return isAndroid;
	}
}
