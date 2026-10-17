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
        // Sorted-set semantics without the JVM-only toSortedSet: distinct ids stay strictly ordered.
        val sorted = ids.sorted()
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.compareTo(b) < 0 })
        for (id in ids) {
            // An independently constructed equal id must compare as 0 and be ==.
            val twin = IdType(id.toString())
            assertTrue(twin !== id)
            assertEquals(id, twin)
            assertEquals(0, id.compareTo(twin), "id=$id")
            assertEquals(0, twin.compareTo(id), "id=$id")
        }
        for (i in 0 until 1000) {
            val a = ids[(i * 7) % ids.size]
            val b = ids[(i * 13 + 1) % ids.size]
            if (a !== b) assertTrue(a.compareTo(b) != 0, "a=$a b=$b")
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
