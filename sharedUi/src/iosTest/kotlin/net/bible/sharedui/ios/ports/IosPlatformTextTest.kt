package net.bible.sharedui.ios.ports

import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.assembly.iosStringTable
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.strings.lookupPlural
import net.bible.sharedui.strings.keyed.formatTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IosPlatformTextTest {
    @Test
    fun pluralUsesTheTable() {
        val holder = IosStringTableHolder(iosStringTable("en"))
        val text = IosPlatformText { holder.current }
        for (language in listOf("en", "fi")) {
            holder.switchTo(language)
            for (count in listOf(0, 1, 2)) {
                assertEquals(
                    formatTemplate(lookupPlural(language, "cloud_doc_sync_now_count_size", count), arrayOf(count, "5 MB")),
                    text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT_SIZE, count, count, "5 MB"),
                )
                assertEquals(holder.current.plural("cloud_doc_bulk_remove_all_confirm", count),
                    text.plural(PluralKey.CLOUD_DOC_BULK_REMOVE_CONFIRM, count, count))
            }
        }
    }

    @Test
    fun fileSizeFormat() {
        val text = IosPlatformText { iosStringTable("en") }
        val small = text.formatFileSize(1024)
        val large = text.formatFileSize(5_000_000)
        assertTrue(small.isNotBlank())
        assertTrue(large.any { it.isLetter() }, large)
        assertNotEquals(small, large)
        assertNotEquals("5000000", large)
    }
}
