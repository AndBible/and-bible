package net.bible.shareddata

import net.bible.android.database.IdType
import net.bible.android.database.MyUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `MyUUID`/`IdType` ordering must be consistent with `equals` and with SQLite BLOB (`memcmp`) order. */
class IdTypeOrderingTest {
    private fun randomIds(n: Int = 200) = List(n) { IdType() }

    @Test
    fun distinctIdsAreNotEqualUnderCompareTo() {
        val ids = randomIds()
        // Sorted-set semantics without the JVM-only toSortedSet: all 200 stay distinct under compareTo.
        val sorted = ids.sorted()
        assertEquals(200, sorted.size)
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.compareTo(b) < 0 })
        ids.forEach { assertEquals(0, it.compareTo(it)) }
        for (i in 0 until 1000) {
            val a = ids[(i * 7) % ids.size]
            val b = ids[(i * 13 + 1) % ids.size]
            assertEquals(a == b, a.compareTo(b) == 0, "a=$a b=$b")
        }
    }

    @Test
    fun mostSignificantBitsDominate() {
        assertTrue(MyUUID(1, 0) > MyUUID(0, -1L))
        assertTrue(MyUUID(0, 1) < MyUUID(0, 2))
    }

    @Test
    fun highBitSetSortsAfterHighBitClear() {
        assertTrue(MyUUID(Long.MIN_VALUE, 0) > MyUUID(Long.MAX_VALUE, 0))
        assertTrue(MyUUID(0, Long.MIN_VALUE) > MyUUID(0, Long.MAX_VALUE))
        val max = MyUUID(-1L, -1L)
        val list = listOf(MyUUID(0, 0), max, MyUUID(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(max, list.max())
    }

    @Test
    fun sortOrderMatchesHexStringOrder() {
        val ids = randomIds()
        assertEquals(ids.map { it.toString() }.sorted(), ids.sorted().map { it.toString() })
    }

    @Test
    fun sortOrderMatchesUnsignedByteOrder() {
        val ids = randomIds()
        val byteOrder = Comparator<IdType> { a, b ->
            val x = a.toByteArray()!!
            val y = b.toByteArray()!!
            for (i in x.indices) {
                val c = x[i].toUByte().compareTo(y[i].toUByte())
                if (c != 0) return@Comparator c
            }
            0
        }
        assertEquals(ids.sortedWith(byteOrder), ids.sorted())
    }

    @Test
    fun emptySortsFirstAndEqualsEmpty() {
        assertTrue(IdType.empty() < IdType())
        assertEquals(0, IdType.empty().compareTo(IdType.empty()))
        assertTrue(listOf(IdType(), IdType.empty()).sorted().first().isEmpty)
    }
}
