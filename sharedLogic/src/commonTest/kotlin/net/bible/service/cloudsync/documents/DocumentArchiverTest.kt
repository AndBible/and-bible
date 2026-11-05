package net.bible.service.cloudsync.documents

import net.bible.sharedlogic.addons.FakeAddonBook
import net.bible.sharedlogic.addons.FakeAddonMetaData
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentArchiverTest {
    private fun book(properties: String) = SwordBook(
        SwordBookMetaData(
            """
            [ArchiveTest]
            Description=Archive test
            ModDrv=RawText
            Lang=en
            $properties
            """.trimIndent().encodeToByteArray(),
            "ArchiveTest",
        ),
        NullBackend(),
    )

    @Test
    fun manualInstallationMetadataDeterminesCloudDocumentType() {
        // The markers describe installation format, not the book's category or ModDrv.
        val cases = listOf(
            "" to DocumentType.SWORD,
            "AndBibleMyBibleModule=1" to DocumentType.MYBIBLE,
            "AndBibleMySwordModule=1" to DocumentType.MYSWORD,
            "AndBibleESwordModule=1" to DocumentType.ESWORD,
            "AndBibleEpubModule=1" to DocumentType.EPUB,
        )
        for ((properties, expected) in cases) {
            assertEquals(expected, DocumentArchiver.documentTypeOf(book(properties)), properties)
        }
    }

    @Test
    fun cloudVersionPreservesMetadataAndDefaultsOnlyWhenAbsent() {
        for ((version, expected) in listOf(null to "0.0", "2.7.1" to "2.7.1", "2026-11-beta" to "2026-11-beta")) {
            // SWORD metadata supplies its own 1.0 default; use metadata that genuinely lacks Version.
            val properties = version?.let { mapOf("Version" to it) }.orEmpty()
            val book = FakeAddonBook(FakeAddonMetaData("ArchiveTest", null, emptyMap(), properties))
            assertEquals(expected, DocumentArchiver.documentVersion(book), version)
        }
    }
}
