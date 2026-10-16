package net.bible.android.database

import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.intToColorArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArgbColorTest {
    @Test fun argbPacksChannels() {
        assertEquals(0xff444444.toInt(), argb(255, 68, 68, 68))
        assertEquals(0x80123456.toInt(), argb(0x80, 0x12, 0x34, 0x56))
        assertEquals(0, argb(0, 0, 0, 0))
        assertEquals(-1, argb(255, 255, 255, 255))
    }

    @Test fun channelExtractionTreatsHighBitAsUnsigned() {
        val c = 0x80123456.toInt()
        assertEquals(0x80, c.alphaOf())
        assertEquals(0x12, c.redOf())
        assertEquals(0x34, c.greenOf())
        assertEquals(0x56, c.blueOf())
        assertEquals(255, (-1).alphaOf())
        assertEquals(255, (-1).blueOf())
    }

    @Test fun parseArgbHexAcceptsSixAndEightDigitsCaseInsensitive() {
        assertEquals(0xff444444.toInt(), parseArgbHex("#ff444444"))
        assertEquals(0x7f0000ff, parseArgbHex("#7f0000FF"))
        assertEquals(0xff112233.toInt(), parseArgbHex("#112233"))
        assertEquals(0xffabcdef.toInt(), parseArgbHex("#ABCDEF"))
    }

    @Test fun parseArgbHexRejectsMalformedInput() {
        for (bad in listOf("444444", "", "#", "#12345", "#1234567", "#123456789", "#gg0000", "#+12345", "#-12345", "ff444444")) {
            assertThrows("input '$bad'", IllegalArgumentException::class.java) { parseArgbHex(bad) }
        }
    }

    @Test fun bookmarkStyleColoursKeepTheirStoredIntValues() {
        // Literal ints as produced by android.graphics.Color.argb for the original BookmarkEntities literals.
        assertEquals(0xffffff00.toInt(), BookmarkStyle.YELLOW_STAR.backgroundColor)
        assertEquals(0xffd50000.toInt(), BookmarkStyle.RED_HIGHLIGHT.backgroundColor)
        assertEquals(0xffffff00.toInt(), BookmarkStyle.YELLOW_HIGHLIGHT.backgroundColor)
        assertEquals(0xff00ff00.toInt(), BookmarkStyle.GREEN_HIGHLIGHT.backgroundColor)
        assertEquals(0xff91a7ff.toInt(), BookmarkStyle.BLUE_HIGHLIGHT.backgroundColor)
        assertEquals(0xffffa500.toInt(), BookmarkStyle.ORANGE_HIGHLIGHT.backgroundColor)
        assertEquals(0xff800080.toInt(), BookmarkStyle.PURPLE_HIGHLIGHT.backgroundColor)
        assertEquals(0xff806380.toInt(), BookmarkStyle.UNDERLINE.backgroundColor)
        assertEquals(0xffff0000.toInt(), BookmarkStyle.SPEAK.backgroundColor)
    }

    @Test fun colourArrayKeepsRgbaOrder() {
        assertEquals(listOf(0x91, 0xa7, 0xff, 0xff), BookmarkStyle.BLUE_HIGHLIGHT.colorArray)
        assertEquals(listOf(0x12, 0x34, 0x56, 0x80), intToColorArray(0x80123456.toInt()))
    }

    @Test fun defaultWorkspaceColourKeepsStoredValue() {
        assertEquals(0xff444444.toInt(), defaultWorkspaceColor)
    }
}
