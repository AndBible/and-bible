package org.crosswire.jsword;

import java.util.Locale;

import junit.framework.TestCase;

import org.crosswire.jsword.versification.system.Versifications;

public class BookNameTest extends TestCase {

    public void testgetBookWithDifferentLocale() {
        Locale.setDefault(Locale.GERMAN);
        assertNotNull(Versifications.instance().getVersification("KJV").getBook("Mr"));
    }
}
