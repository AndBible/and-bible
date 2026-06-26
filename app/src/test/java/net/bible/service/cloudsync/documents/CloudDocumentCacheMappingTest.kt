package net.bible.service.cloudsync.documents

import net.bible.android.database.toMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentCacheMappingTest {
    private val meta = DocumentSyncMeta(
        initials = "KJV", name = "King James Version", documentType = DocumentType.SWORD,
        version = "2.6", size = 16384000, language = "en", category = "BIBLE",
        sourceDevice = "dev1", timestamp = 1740000000000, cipherKey = "abc", deleted = true,
    )

    @Test fun roundTripsThroughCacheEntity() {
        assertEquals(meta, meta.toCacheEntity().toMeta())
    }

    @Test fun defaultsSurvive() {
        val minimal = DocumentSyncMeta(
            initials = "X", name = "X", documentType = DocumentType.EPUB, version = "1.0",
            size = 0, language = "", sourceDevice = "d", timestamp = 0,
        )
        val back = minimal.toCacheEntity().toMeta()
        assertEquals("", back.category)
        assertEquals(null, back.cipherKey)
        assertEquals(false, back.deleted)
    }
}
