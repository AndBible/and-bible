package net.bible.service.format.osistohtml.osishandlers

import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import fi.sykero.kdom.query.descendantElements
import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.common.util.ResourceBundles
import org.crosswire.jsword.passage.OsisParser
import org.crosswire.jsword.versification.system.Versifications

/** Expands link targets rather than trusting labels such as "v. 9". Invalid/non-Bible links keep their label. */
internal class SpeakReferenceText(private val locale: KLocale = LocaleProviderManager.getLocale()) {
    private val parser = OsisParser()

    /** Preserve the existing BVA ordinals: speak a containing link once, at its first anchor. */
    fun forAnchor(anchor: Element): String {
        val reference = generateSequence(anchor.parentElement) { it.parentElement }
            .firstOrNull { it.name == "reference" }
        val expanded = reference?.let { expand(it.getAttributeValue("osisRef")) }
        if (expanded != null) {
            return if (reference.descendantElements().firstOrNull { it.name == "BVA" } === anchor) expanded else ""
        }
        return text(anchor)
    }

    private fun text(element: Element): String {
        if (element.name == "reference") expand(element.getAttributeValue("osisRef"))?.let { return it }
        return element.content.joinToString("") {
            when (it) {
                is Text -> it.text
                is Element -> text(it)
                else -> ""
            }
        }
    }

    fun expand(osisRef: String?): String? {
        if (osisRef.isNullOrBlank()) return null
        return try {
            val names = ResourceBundles.get("BibleNames", locale) ?: return null
            osisRef.trim().split(Regex("\\s+")).map { target ->
                val range = parser.parseOsisRef(Versifications.instance().getVersification("KJVA"), target)
                    ?: return null
                val ids = target.split('-')
                fun endpoint(id: String, includeBook: Boolean): String {
                    val parts = id.split('.')
                    val book = if (includeBook) names.getString("${parts[0]}.Full") ?: return "" else ""
                    val location = parts.drop(1).joinToString(":") { it.substringBefore('!') }
                    return listOf(book, location).filter { it.isNotEmpty() }.joinToString(" ")
                }
                val start = endpoint(ids[0], true).takeIf { it.isNotEmpty() } ?: return null
                if (ids.size == 1) start
                else "$start-${endpoint(ids[1], range.start.book != range.end.book)}"
            }.joinToString("; ")
        } catch (_: Exception) {
            null
        }
    }
}
