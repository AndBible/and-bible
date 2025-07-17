package net.bible.android.control.bookmark

import kotlinx.coroutines.runBlocking
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.*

class BookmarkCsvColumnSelectionTest {

    @Test
    fun testAvailableColumns() {
        val columns = BookmarkCsvUtils.availableColumns
        assertTrue("Should have at least 10 columns", columns.size >= 10)
        
        // Check if all columns have non-empty keys and display names
        columns.forEach { column ->
            assertFalse("Column key should not be empty", column.key.isEmpty())
            assertFalse("Column display name should not be empty", column.displayName.isEmpty())
            assertFalse("Column header should not be empty", column.header.isEmpty())
        }
        
        // Check that essential columns are present
        val columnKeys = columns.map { it.key }
        assertTrue("Should have OSIS reference column", columnKeys.contains("osisRef"))
        assertTrue("Should have Bible reference column", columnKeys.contains("bibleRef"))
        assertTrue("Should have notes column", columnKeys.contains("notes"))
        assertTrue("Should have labels column", columnKeys.contains("labels"))
    }

    @Test
    fun testCsvColumnDataClass() {
        val column = BookmarkCsvUtils.CsvColumn("test_key", "test_header", "Test Display Name", false)
        assertEquals("test_key", column.key)
        assertEquals("test_header", column.header)
        assertEquals("Test Display Name", column.displayName)
        assertFalse(column.defaultSelected)
    }

    @Test
    fun testDefaultColumnSelection() {
        val columns = BookmarkCsvUtils.availableColumns
        val defaultSelectedColumns = columns.filter { it.defaultSelected }
        
        // All columns should be selected by default
        assertEquals("All columns should be selected by default", columns.size, defaultSelectedColumns.size)
    }
}
