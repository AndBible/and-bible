package net.bible.sharedui.webview

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedFileNameTest {
    @Test fun preservesDocumentNamesAndUnicode() {
        assertEquals("Compare", sharedFileName("Compare"))
        assertEquals("Multiple references", sharedFileName("Multiple references"))
        assertEquals("My Study Pad", sharedFileName("My Study Pad"))
        assertEquals("תורה", sharedFileName("תורה"))
    }

    @Test fun replacesPathAndFilenameSeparatorsWithSpaces() {
        assertEquals("a b c d e f g h i j", sharedFileName("a/b\\c:d*e?f\"g<h>i|j"))
        assertEquals("Study Pad  John 1", sharedFileName("../Study Pad: John 1"))
    }

    @Test fun controlCharactersCannotReachTheFilename() {
        assertEquals("a b c d", sharedFileName("a\nb\u0000c\u007fd"))
    }

    @Test fun emptyOrDotOnlyTitlesFallBackToShared() {
        for (title in listOf("", "  /// ", "..", " . . ")) {
            assertEquals("shared", sharedFileName(title), title)
        }
    }

    @Test fun boundsTheStemAndRemovesTrailingDotsAndWhitespace() {
        assertEquals("x".repeat(60), sharedFileName("x".repeat(100)))
        assertEquals("My Study Pad", sharedFileName("  My Study Pad...  "))
        assertEquals("x".repeat(59), sharedFileName("x".repeat(59) + " .suffix"))
    }
}
