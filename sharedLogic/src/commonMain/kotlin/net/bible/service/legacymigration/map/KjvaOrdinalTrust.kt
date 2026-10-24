package net.bible.service.legacymigration.map

import net.bible.android.common.isConvertibleTo
import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.system.Versifications

/**
 * Port of `VersificationMapper.kjvaOrdinalRange`, shared by the bookmark and progress ordinal trust checks: the ordered
 * KJVA ordinals of the source range, or null when either endpoint has no strict JSword mapping. Swift never persists the
 * converter's coordinate fallback, so a verse JSword can only convert by fallback is a miss, and an ordinal past the
 * source canon is a miss too (Swift `JSwordCanon.reference(at:)`; `Verse()` alone would clamp it).
 */
internal fun rederivedKjvaRange(sourceVersification: String, sourceStart: Long, sourceEnd: Long): LongRange? = try {
    val source = Versifications.instance().getVersification(sourceVersification)
    fun kjva(ordinal: Long): Long? {
        if (source == null || ordinal < 0 || ordinal > source.maximumOrdinal()) return null
        if (source.name == KJVA.name) return ordinal
        val verse = Verse(source, ordinal.toInt())
        return if (!verse.isConvertibleTo(KJVA)) null else verse.toV11n(KJVA).ordinal.toLong()
    }
    val a = kjva(sourceStart)
    val b = kjva(sourceEnd)
    if (a == null || b == null) null else minOf(a, b)..maxOf(a, b)
} catch (e: Exception) {
    null
}

/** Whether JSword knows a versification of this (already trimmed) name. */
internal fun isKnownVersification(name: String): Boolean {
    if (name.isBlank()) return false
    return try {
        Versifications.instance().getVersification(name) != null
    } catch (e: Exception) {
        false
    }
}
