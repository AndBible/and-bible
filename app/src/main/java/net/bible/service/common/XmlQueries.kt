package net.bible.service.common

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.Parent
import org.jdom2.Text
import org.jdom2.filter.Filters

/** Named boundary for the existing jaxen queries; keeps XPath semantics until the axes migration. */
object XmlQueries {
    private fun elements(expression: String, context: Any, vararg namespaces: Namespace): List<Element> =
        useXPathInstance { it.compile(expression, Filters.element(), null, *namespaces).evaluate(context) }

    fun rootfiles(container: Document, ns: Namespace): List<Element> = elements("//ns:rootfile", container, ns)
    fun manifestItems(opf: Document, ns: Namespace): List<Element> = elements("//ns:manifest/ns:item", opf, ns)
    fun tocNcxItems(opf: Document, ns: Namespace): List<Element> = elements("//ns:manifest/ns:item[@media-type='application/x-dtbncx+xml']", opf, ns)
    fun tocIdItems(opf: Document, ns: Namespace): List<Element> = elements("//ns:manifest/ns:item[@id='toc']", opf, ns)
    fun tocNavItems(opf: Document, ns: Namespace): List<Element> = elements("//ns:manifest/ns:item[@properties='nav']", opf, ns)
    fun tocItem(opf: Document, ns: Namespace): Element? = tocNcxItems(opf, ns).firstOrNull()
        ?: tocIdItems(opf, ns).firstOrNull() ?: tocNavItems(opf, ns).firstOrNull()
    fun navPointContents(ncx: Document, ns: Namespace): List<Element> = elements("//ns:navPoint/ns:content", ncx, ns)
    fun navLabelTexts(content: Element, ns: Namespace): List<Element> = elements("../ns:navLabel/ns:text", content, ns)
    fun dcElements(opf: Document, key: String, dc: Namespace): List<Element> = elements("//dc:$key", opf, dc)
    fun spineItemrefs(opf: Document, ns: Namespace): List<Element> = elements("//ns:spine/ns:itemref", opf, ns)
    fun bvas(context: Parent, ns: Namespace): List<Element> = elements("//ns:BVA", context, ns)
    fun bvasBelow(context: Element, ns: Namespace): List<Element> = elements(".//ns:BVA", context, ns)
    fun svgImagesWithHref(context: Element, svg: Namespace, xlink: Namespace): List<Element> = elements("//svg:image[@xlink:href]", context, svg, xlink)
    fun imgs(context: Element, ns: Namespace): List<Element> = elements("//ns:img", context, ns)
    fun links(context: Element, ns: Namespace): List<Element> = elements("//ns:a", context, ns)
    fun body(context: Parent, ns: Namespace): Element? = elements("//ns:body", context, ns).firstOrNull()
    fun elementsWithId(context: Parent): List<Element> = elements("//*[@id]", context)
    fun splitPoints(doc: Document, ordinal: Int, ns: Namespace): List<Element> = elements("//*[descendant::ns:BVA[@ordinal='$ordinal'] and (following-sibling::ns:p or preceding-sibling::ns:p or self::ns:p)]", doc, ns)
    fun splitPoint(doc: Document, ordinal: Int, ns: Namespace): Element? = splitPoints(doc, ordinal, ns).firstOrNull()
    fun textsOutsideNotes(context: Element): List<Text> = useXPathInstance {
        it.compile(".//text()[not(ancestor::note)]", Filters.text()).evaluate(context)
    }
}
