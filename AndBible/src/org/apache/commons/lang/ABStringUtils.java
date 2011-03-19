package org.apache.commons.lang;

public class ABStringUtils extends StringUtils {

	/** it doesn't make sense to say a string is not all uppercase just becasue it contains characters like numbers that can't be uppercase
	 * 
	 * @param cs
	 * @return
	 */
	public static boolean isAllUpperCaseWherePossible(CharSequence cs) {
        if (cs == null || isEmpty(cs)) {
            return false;
        }
        int sz = cs.length();
        for (int i = 0; i < sz; i++) {
        	char ch = cs.charAt(i);
            if (Character.isLowerCase(ch)) {
                return false;
            }
        }
        return true;
    }
}
