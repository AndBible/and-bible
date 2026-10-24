package net.bible.service.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Characterization of [ABStringUtils.isAllUpperCaseWherePossible]. */
class ABStringUtilsTest {
    @Test fun digitsDoNotCountAsLowerCase() {
        assertTrue(ABStringUtils.isAllUpperCaseWherePossible("ABC1"))
    }

    @Test fun emptyStringIsNotAllUpperCase() {
        assertFalse(ABStringUtils.isAllUpperCaseWherePossible(""))
    }

    @Test fun anyLowerCaseLetterMakesItFalse() {
        assertFalse(ABStringUtils.isAllUpperCaseWherePossible("AbC"))
    }
}
