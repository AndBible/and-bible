package net.bible.android.database

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the stored MyUUID/IdType text and byte formats as literals (written against the pre-refactor code). */
class IdTypeFormatTest {
    private val mixed = MyUUID(0x0123456789abcdefL, -0x0123456789abcdf0L) // lsb = 0xfedcba9876543210
    private val allOnes = MyUUID(-1L, -1L)
    private val highBitOnly = MyUUID(Long.MIN_VALUE, 1L)

    @Test fun toStringIsFixedWidthLowercaseHex() {
        assertEquals("01234567-89ab-cdef-fedc-ba9876543210", mixed.toString())
        assertEquals("ffffffff-ffff-ffff-ffff-ffffffffffff", allOnes.toString())
        assertEquals("80000000-0000-0000-0000-000000000001", highBitOnly.toString())
        assertEquals("00000000-0000-0000-0000-000000000000", MyUUID(0, 0).toString())
    }

    @Test fun fromStringReadsBackTheSameBits() {
        for ((s, m, l) in listOf(
            Triple("01234567-89ab-cdef-fedc-ba9876543210", 0x0123456789abcdefL, -0x0123456789abcdf0L),
            Triple("ffffffff-ffff-ffff-ffff-ffffffffffff", -1L, -1L),
            Triple("80000000-0000-0000-0000-000000000001", Long.MIN_VALUE, 1L),
        )) {
            val id = MyUUID.fromString(s)
            assertEquals(s, m, id.mostSignificantBits)
            assertEquals(s, l, id.leastSignificantBits)
        }
    }

    @Test fun fromStringIsCaseInsensitiveAndAcceptsShortComponents() {
        assertEquals(mixed, MyUUID.fromString("01234567-89AB-CDEF-FEDC-BA9876543210"))
        val short = MyUUID.fromString("1-2-3-4-5")
        assertEquals(0x0000000100020003L, short.mostSignificantBits)
        assertEquals(0x0004000000000005L, short.leastSignificantBits)
    }

    @Test fun fromStringRejectsMalformedText() {
        for (bad in listOf("", "1-2-3-4", "1-2-3-4-5-6", "zz-1-2-3-4", "--1-2-3", "+1-2-3-4-5", "-1-2-3-4-5",
            "0x1-2-3-4-5", "1-2-3-4-ffffffffffffffff", "１-2-3-4-5")) {
            assertThrows("input '$bad'", IllegalArgumentException::class.java) { MyUUID.fromString(bad) }
        }
    }

    @Test fun idTypeFromBadStringIsEmptyAndEmptyStringRoundTrips() {
        assertTrue(IdType("zz-1-2-3-4").isEmpty)
        assertTrue(IdType(null as String?).isEmpty)
        assertEquals("", IdType.empty().toString())
        assertTrue(IdType.fromString("").isEmpty)
        assertEquals("01234567-89ab-cdef-fedc-ba9876543210", IdType.fromString("01234567-89ab-cdef-fedc-ba9876543210").toString())
    }

    @Test fun toByteArrayIsBigEndianMostSignificantFirst() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xab.toByte(), 0xcd.toByte(), 0xef.toByte(),
                0xfe.toByte(), 0xdc.toByte(), 0xba.toByte(), 0x98.toByte(), 0x76, 0x54, 0x32, 0x10),
            mixed.toByteArray())
        assertArrayEquals(ByteArray(16) { -1 }, allOnes.toByteArray())
        assertArrayEquals(ByteArray(16).also { it[0] = 0x80.toByte(); it[15] = 1 }, highBitOnly.toByteArray())
    }

    @Test fun fromByteArrayRoundTripsAndRejectsWrongSize() {
        assertEquals(mixed, MyUUID.fromByteArray(mixed.toByteArray()))
        assertEquals(allOnes, MyUUID.fromByteArray(ByteArray(16) { -1 }))
        assertEquals(highBitOnly, MyUUID.fromByteArray(highBitOnly.toByteArray()))
        assertThrows(RuntimeException::class.java) { MyUUID.fromByteArray(ByteArray(15)) }
    }

    @Test fun idTypeToHexIsLowercaseTwoDigitsPerByte() {
        assertEquals("0123456789abcdeffedcba9876543210", IdType(mixed).toHex())
        assertEquals("ffffffffffffffffffffffffffffffff", IdType(allOnes).toHex())
        assertEquals("80000000000000000000000000000001", IdType(highBitOnly).toHex())
    }

    @Test fun idTypeFromByteArrayTreatsZeroOrOneByteAsEmpty() {
        assertTrue(IdType.fromByteArray(ByteArray(0))!!.isEmpty)
        assertTrue(IdType.fromByteArray(ByteArray(1))!!.isEmpty)
        assertEquals(IdType(mixed), IdType.fromByteArray(mixed.toByteArray()))
    }

    @Test fun equalityAndHashUseBitsOnly() {
        assertEquals(MyUUID(1, 2), MyUUID(1, 2))
        assertNotEquals(MyUUID(1, 2), MyUUID(1, 3))
        assertNotEquals(MyUUID(1, 2) as Any, "x" as Any)
        assertEquals(MyUUID(1, 2).hashCode(), MyUUID(1, 2).hashCode())
        assertEquals(((1L xor 2L) shr 32).toInt() xor (1L xor 2L).toInt(), MyUUID(1, 2).hashCode())
    }

    @Test fun randomUuidsDiffer() {
        assertNotEquals(MyUUID.randomUUID(), MyUUID.randomUUID())
    }
}
