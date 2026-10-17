package net.bible.service.legacymigration.plist

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BinaryPlistTest {
    private fun hex(s: String) = s.filter { !it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Wraps one hand-written object into a single-object plist with 1-byte offsets. */
    private fun single(objectHex: String): ByteArray {
        val obj = hex(objectHex)
        val trailer = ByteArray(32)
        trailer[6] = 1; trailer[7] = 1; trailer[15] = 1
        trailer[31] = (8 + obj.size).toByte()
        return "bplist00".encodeToByteArray() + obj + byteArrayOf(8) + trailer
    }

    @Test fun intsOfEveryWidth() {
        assertEquals(PlistInt(7), BinaryPlist.parse(single("10 07")))
        assertEquals(PlistInt(0x0102), BinaryPlist.parse(single("11 01 02")))
        assertEquals(PlistInt(0x01020304), BinaryPlist.parse(single("12 01 02 03 04")))
        assertEquals(PlistInt(-2), BinaryPlist.parse(single("13 FF FF FF FF FF FF FF FE")))
    }

    @Test fun simpleScalars() {
        assertEquals(PlistNull, BinaryPlist.parse(single("00")))
        assertEquals(PlistBool(false), BinaryPlist.parse(single("08")))
        assertEquals(PlistBool(true), BinaryPlist.parse(single("09")))
        assertEquals(PlistReal(1.5), BinaryPlist.parse(single("23 3F F8 00 00 00 00 00 00")))
        assertEquals(PlistReal(1.5), BinaryPlist.parse(single("22 3F C0 00 00")))
        assertEquals(PlistDate(1.5), BinaryPlist.parse(single("33 3F F8 00 00 00 00 00 00")))
        assertEquals(PlistUid(5), BinaryPlist.parse(single("80 05")))
        assertContentEquals(byteArrayOf(1, 2, 3), assertIs<PlistData>(BinaryPlist.parse(single("43 01 02 03"))).bytes)
    }

    @Test fun asciiAndUtf16Strings() {
        assertEquals(PlistString("abc"), BinaryPlist.parse(single("53 61 62 63")))
        assertEquals(PlistString("aä€"), BinaryPlist.parse(single("63 00 61 00 E4 20 AC")))
    }

    @Test fun longStringUsesExtendedLength() {
        val text = "x".repeat(20)
        val bytes = BplistBuilder.build(PlistString(text))
        assertEquals(PlistString(text), BinaryPlist.parse(bytes))
    }

    @Test fun nestedStructureWithTwoByteOffsets() {
        val tree = PlistDict(mapOf(
            "list" to PlistArray(listOf(PlistInt(1), PlistString("two"), PlistBool(true))),
            "inner" to PlistDict(mapOf("k" to PlistUid(3))),
        ))
        for (size in listOf(1, 2)) {
            assertEquals(tree, BinaryPlist.parse(BplistBuilder.build(tree, minOffsetSize = size)))
        }
    }

    @Test fun rejectsOtherVersion() {
        val bytes = single("10 07").also { it[7] = '1'.code.toByte() }
        assertFailsWith<LegacyFormatException> { BinaryPlist.parse(bytes) }
    }

    @Test fun rejectsTruncatedTrailer() {
        assertFailsWith<LegacyFormatException> { BinaryPlist.parse(single("10 07").copyOf(20)) }
        assertFailsWith<LegacyFormatException> { BinaryPlist.parse(ByteArray(0)) }
    }

    @Test fun rejectsOffsetBeyondEnd() {
        val bytes = single("10 07")
        bytes[bytes.size - 32 - 1] = 0x7F // the single offset-table entry
        assertFailsWith<LegacyFormatException> { BinaryPlist.parse(bytes) }
    }
}
