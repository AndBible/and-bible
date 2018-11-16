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
                Class.forName("net.bible.test.TestEnvironmentFlag");
                isAndroid = false;
                System.out.println("Running as test");
            } catch (ClassNotFoundException cnfe) {
                isAndroid = true;
                System.out.println("Running on Android");
            }
            isAndroidCheckDone = true;
        }
        return isAndroid;
    }
    
    public static void setTestMode() {
        isAndroid = false;
        isAndroidCheckDone = true;
    }
}
