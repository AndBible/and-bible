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

import org.apache.commons.lang3.StringUtils;

public class ABStringUtils extends StringUtils {

	/** It doesn't make sense to say a string is not all uppercase just becasue it contains characters like numbers that can't be uppercase
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
