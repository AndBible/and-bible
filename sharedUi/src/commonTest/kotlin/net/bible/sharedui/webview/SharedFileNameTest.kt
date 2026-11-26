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

    @Test fun boundsTheStemByCodePointWithoutSplittingSurrogatePairs() {
        assertEquals("x".repeat(59) + "😀", sharedFileName("x".repeat(59) + "😀suffix"))
        assertEquals("😀".repeat(60), sharedFileName("😀".repeat(61)))
        assertEquals("x".repeat(60), sharedFileName("x".repeat(100)))
        assertEquals("My Study Pad", sharedFileName("  My Study Pad...  "))
        assertEquals("x".repeat(59), sharedFileName("x".repeat(59) + " .suffix"))
    }

    @Test fun avoidsWindowsReservedDeviceBasenamesCaseInsensitively() {
        assertEquals("cOn_.txt", sharedFileName("cOn.txt"))
        assertEquals("PrN_", sharedFileName("PrN"))
        assertEquals("AuX_.html", sharedFileName("AuX.html"))
        assertEquals("nUl_.doc", sharedFileName("nUl.doc"))
        for (number in 1..9) {
            assertEquals("COM${number}_.txt", sharedFileName("COM${number}.txt"))
            assertEquals("LPT${number}_.txt", sharedFileName("LPT${number}.txt"))
        }
        assertEquals("lPt9_.html", sharedFileName("lPt9.html"))
        assertEquals("CON_." + "x".repeat(55), sharedFileName("CON." + "x".repeat(56)))
    }

    @Test fun truncationCannotCreateAWindowsReservedDeviceName() {
        assertEquals("CON_", sharedFileName("CON" + " ".repeat(57) + "x"))
    }

    @Test fun allowsNamesThatOnlyResembleWindowsReservedDeviceBasenames() {
        assertEquals("COM10", sharedFileName("COM10"))
        assertEquals("CONtext.txt", sharedFileName("CONtext.txt"))
    }
}
