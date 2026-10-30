package net.bible.android.control.backup

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the POSIX spelling of the library-root URI of extracted SWORD books (Windows is covered in jsword). */
class DirectoryKUriTest {
    @Test fun posixDirectoryGetsTrailingSlash() {
        assertEquals("file:/home/u/.sword/", directoryKUri("/home/u/.sword".toPath()).toString())
        assertEquals("/home/u/.sword/", directoryKUri("/home/u/.sword".toPath()).getPath())
    }

    @Test fun posixDirectoryWithSpaceIsPercentEncoded() {
        assertEquals("file:/home/u/my%20sword/", directoryKUri("/home/u/my sword".toPath()).toString())
    }

    @Test fun rootKeepsSingleSlash() {
        assertEquals("file:/", directoryKUri("/".toPath()).toString())
    }
}
