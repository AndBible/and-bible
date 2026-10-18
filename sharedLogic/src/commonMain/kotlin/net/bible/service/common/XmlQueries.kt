package net.bible.service.common

import fi.sykero.kdom.query.*
import fi.sykero.kdom.Document
import fi.sykero.kdom.Element
import fi.sykero.kdom.Namespace
import fi.sykero.kdom.Parent
import fi.sykero.kdom.Text

/** Domain queries with eager results: callers may detach or move nodes while iterating. */
object XmlQueries {
    fun rootfiles(container: Document, ns: Namespace): List<Element> = container.findAll("rootfile", ns)
    fun manifestItems(opf: Document, ns: Namespace): List<Element> = opf.findChildrenOf("manifest", ns, "item", ns)
    fun tocNcxItems(opf: Document, ns: Namespace): List<Element> =
        opf.findChildrenOf("manifest", ns, "item", ns) { it.attributeIs("media-type", "application/x-dtbncx+xml") }
    fun tocIdItems(opf: Document, ns: Namespace): List<Element> =
        opf.findChildrenOf("manifest", ns, "item", ns) { it.attributeIs("id", "toc") }
    fun tocNavItems(opf: Document, ns: Namespace): List<Element> =
        opf.findChildrenOf("manifest", ns, "item", ns) { it.attributeIs("properties", "nav") }
    fun tocItem(opf: Document, ns: Namespace): Element? = tocNcxItems(opf, ns).firstOrNull()
        ?: tocIdItems(opf, ns).firstOrNull() ?: tocNavItems(opf, ns).firstOrNull()
    fun navPointContents(ncx: Document, ns: Namespace): List<Element> = ncx.findChildrenOf("navPoint", ns, "content", ns)

    /** Relative parent/child query remains meaningful even on a detached subtree. */
    fun navLabelTexts(content: Element, ns: Namespace): List<Element> =
        content.parentElement?.childElements()?.filter { it.isNamed("navLabel", ns) }
            ?.flatMap { it.childElements().filter { child -> child.isNamed("text", ns) } }?.toList() ?: emptyList()

    fun dcElements(opf: Document, key: String, dc: Namespace): List<Element> = opf.findAll(key, dc)
    fun spineItemrefs(opf: Document, ns: Namespace): List<Element> = opf.findChildrenOf("spine", ns, "itemref", ns)
    fun bvas(context: Parent, ns: Namespace): List<Element> = context.findAll("BVA", ns)
    fun bvasBelow(context: Element, ns: Namespace): List<Element> =
        context.descendantElements().filter { it.isNamed("BVA", ns) }.toList()
    fun svgImagesWithHref(context: Element, svg: Namespace, xlink: Namespace): List<Element> =
        context.findAll("image", svg).filter { it.getAttribute("href", xlink) != null }
    fun imgs(context: Element, ns: Namespace): List<Element> = context.findAll("img", ns)
    fun links(context: Element, ns: Namespace): List<Element> = context.findAll("a", ns)
    fun body(context: Parent, ns: Namespace): Element? =
        context.xpathRoot()?.allElements()?.firstOrNull { it.isNamed("body", ns) }
    fun elementsWithId(context: Parent): List<Element> =
        context.xpathRoot()?.allElements()?.filter { it.hasAttribute("id") }?.toList() ?: emptyList()
    fun splitPoints(doc: Document, ordinal: Int, ns: Namespace): List<Element> = doc.allElements().filter { e ->
        e.descendantElements().any { it.isNamed("BVA", ns) && it.attributeIs("ordinal", ordinal.toString()) } &&
            (e.isNamed("p", ns) || e.followingSiblingElements().any { it.isNamed("p", ns) } ||
                e.precedingSiblingElements().any { it.isNamed("p", ns) })
    }.toList()
    fun splitPoint(doc: Document, ordinal: Int, ns: Namespace): Element? = splitPoints(doc, ordinal, ns).firstOrNull()

    /** The original unprefixed note test excludes only no-namespace notes, including ancestors above context. */
    fun textsOutsideNotes(context: Element): List<Text> = context.descendantTexts().filter { text ->
        generateSequence(text.parentElement) { it.parentElement }.none { it.isNamed("note", Namespace.NO_NAMESPACE) }
    }.toList()
}
