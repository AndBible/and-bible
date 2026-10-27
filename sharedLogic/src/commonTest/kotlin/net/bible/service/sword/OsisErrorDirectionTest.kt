package net.bible.service.sword

import kotlin.test.Test
import kotlin.test.assertEquals

class OsisErrorDirectionTest {
    @Test
    fun inlineErrorDeterminesItsOwnDirectionInsteadOfInheritingTheModuleDirection() {
        val error = DocumentNotFound("Key Luke 9:61 not found in document SP.")

        assertEquals("auto", error.xml.getAttributeValue("dir"))
        assertEquals(error.stringMsg, error.xml.text)
    }
}
