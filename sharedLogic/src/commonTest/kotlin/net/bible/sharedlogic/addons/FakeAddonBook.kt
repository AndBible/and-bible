package net.bible.sharedlogic.addons

import fi.sykero.kdom.Content
import org.crosswire.common.util.KUri
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.basic.AbstractBook
import org.crosswire.jsword.book.basic.DefaultBookMetaData
import org.crosswire.jsword.book.sword.processing.RawTextToXmlProcessor
import org.crosswire.jsword.passage.Key

/** Metadata that can hold several values for one key (as a SWORD conf with repeated entries does). */
class FakeAddonMetaData(initials: String, locationPath: String?, multi: Map<String, List<String>>, single: Map<String, String>) :
    DefaultBookMetaData(null, initials, BookCategory.AND_BIBLE) {
    private val multiValues = multi

    init {
        setInitials(initials)
        location = locationPath?.let { KUri("file://$it") }
        single.forEach { (k, v) -> setProperty(k, v) }
    }

    override fun getValues(key: String?): Collection<String>? = multiValues[key] ?: super.getValues(key)
}

/** A [Book] that only carries metadata; every content method is unused by the add-on scanner. */
class FakeAddonBook(meta: FakeAddonMetaData) : AbstractBook(meta, null) {
    override fun getOsis(key: Key?, noOpRawTextProcessor: RawTextToXmlProcessor?): List<Content> = TODO()
    override fun getGlobalKeyList(): Key = TODO()
    override fun getValidKey(name: String?): Key = TODO()
    override fun getKey(name: String?): Key = TODO()
    override fun createEmptyKeyList(): Key = TODO()
    override fun getOsisIterator(key: Key?, allowEmpty: Boolean, allowGenTitles: Boolean): MutableIterator<Content> = TODO()
    override fun contains(key: Key?): Boolean = false
    override fun getRawText(key: Key?): String = TODO()
    override fun isWritable(): Boolean = false
    override fun setRawText(key: Key?, rawData: String?) {}
    override fun setAliasKey(alias: Key?, source: Key?) {}
}

fun fakeAddonBook(
    initials: String,
    location: String?,
    plans: List<String> = emptyList(),
    dateBased: String? = null,
    prompts: String? = null,
): Book {
    val single = buildMap {
        dateBased?.let { put("AndBibleReadingPlanDateBased", it) }
        prompts?.let { put("AndBibleProvidesPrompts", it) }
    }
    val multi = if (plans.isEmpty()) emptyMap() else mapOf("AndBibleProvidesReadingPlan" to plans)
    return FakeAddonBook(FakeAddonMetaData(initials, location, multi, single))
}
