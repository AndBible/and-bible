package org.apache.commons.lang;

import junit.framework.TestCase;

public class ABStringUtilsTest extends TestCase {

	public void testIsAllUpperCaseWherePossible() {
		assertTrue(ABStringUtils.isAllUpperCaseWherePossible("CHAPTER 1"));
		assertFalse(StringUtils.isAllUpperCase("CHAPTER 1"));
	}
}
