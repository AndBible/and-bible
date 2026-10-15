package net.bible.k5

import java.io.File
import java.io.StringReader
import fi.sykero.kdom.Document
import fi.sykero.kdom.Element
import fi.sykero.kdom.Namespace
import fi.sykero.kdom.Text
import fi.sykero.kdom.input.build
import fi.sykero.kdom.input.KdomBuilder
import fi.sykero.kdom.output.Format
import fi.sykero.kdom.output.XMLOutputter
import net.bible.service.common.XmlQueries
import fi.sykero.kdom.Parent
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.xhtmlNamespace

/** Site-labelled snapshots of the Task 11/14 authoritative XPath inventory, including short-circuited fallbacks. */
internal object K5EpubQueries {
    fun capture(dir: File): String = buildString {
        val out = XMLOutputter(Format.getRawFormat())
        val opf = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
        val dc = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
        val ncx = Namespace.getNamespace("ns", "http://www.daisy.org/z3986/2005/ncx/")
        val containerNs = Namespace.getNamespace("ns", "urn:oasis:names:tc:opendocument:xmlns:container")
        val svg = Namespace.getNamespace("svg", "http://www.w3.org/2000/svg")
        val xlink = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink")
        fun row(vararg cells: String) = cells.joinToString("\t") { it.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n") } + "\n"
        fun query(site: String, contextId: String, expression: String, context: Any, vararg ns: Namespace): List<Element> {
            val results = when (site) {
                "backend.container" -> XmlQueries.rootfiles(context as Document, ns[0])
                "backend.manifest" -> XmlQueries.manifestItems(context as Document, ns[0])
                "backend.toc.ncx" -> XmlQueries.tocNcxItems(context as Document, ns[0])
                "backend.toc.id" -> XmlQueries.tocIdItems(context as Document, ns[0])
                "backend.toc.nav" -> XmlQueries.tocNavItems(context as Document, ns[0])
                "backend.spine" -> XmlQueries.spineItemrefs(context as Document, ns[0])
                "optimizer.svg" -> XmlQueries.svgImagesWithHref((context as Document).rootElement, ns[0], ns[1])
                "optimizer.img" -> XmlQueries.imgs((context as Document).rootElement, ns[0])
                "optimizer.link" -> XmlQueries.links((context as Document).rootElement, ns[0])
                "optimizer.body", "optimizer.write.body" -> listOfNotNull(XmlQueries.body(context as Parent, ns[0]))
                "facade.bva" -> XmlQueries.bvasBelow(if (context is Document) context.rootElement else context as Element, ns[0])
                "optimizer.ids" -> XmlQueries.elementsWithId(context as Parent)
                "optimizer.split" -> XmlQueries.splitPoints(context as Document, contextId.substringAfterLast('-').toInt(), ns[0])
                "backend.index.bva", "backend.totalCharacters.bva", "optimizer.ordinalRange" -> XmlQueries.bvas(context as Parent, ns[0])
                else -> when {
                    site.startsWith("backend.metadata.") -> XmlQueries.dcElements(context as Document, site.substringAfterLast('.'), ns[0])
                    site.endsWith(".content") -> XmlQueries.navPointContents(context as Document, ns[0])
                    site.endsWith(".label") -> XmlQueries.navLabelTexts(context as Element, ns[0])
                    else -> error("Unmapped production query site $site")
                }
            }
            val reference = org.jdom2.xpath.XPathFactory.instance().compile(expression, org.jdom2.filter.Filters.element(), null, *ns.map { org.jdom2.Namespace.getNamespace(it.prefix, it.uri) }.toTypedArray()).evaluate(reference(context as Parent))
            org.junit.Assert.assertEquals("Production query $site/$contextId differs from frozen jaxen contract", reference.map { org.jdom2.output.XMLOutputter(org.jdom2.output.Format.getRawFormat()).outputString(it) }, results.map { out.outputString(it) })
            // Retain an explicit empty result, expression, count and ordered complete element values.
            append(row("query", site, contextId, expression, results.size.toString(), results.joinToString("\u001e") { out.outputString(it) }))
            return results
        }
        val container = KdomBuilder(allowDocType = true).build(File(dir, "META-INF/container.xml"))
        query("backend.container", "container", "//ns:rootfile", container, containerNs)
        val content = KdomBuilder(allowDocType = true).build(File(dir, "content.opf"))
        fun opfQueries(contextId: String, doc: Document) {
            query("backend.manifest", contextId, "//ns:manifest/ns:item", doc, opf)
            query("backend.toc.ncx", contextId, "//ns:manifest/ns:item[@media-type='application/x-dtbncx+xml']", doc, opf)
            query("backend.toc.id", contextId, "//ns:manifest/ns:item[@id='toc']", doc, opf)
            query("backend.toc.nav", contextId, "//ns:manifest/ns:item[@properties='nav']", doc, opf)
        }
        opfQueries("opf", content)
        // The main fixture deliberately has id=ncx: separately exercise the id=toc fallback with a matching item.
        val fallback = content.clone()
        fallback.rootElement.getChild("manifest", opf)!!.children.first { it.getAttributeValue("id") == "ncx" }.apply {
            setAttribute("id", "toc"); setAttribute("media-type", "application/octet-stream")
        }
        opfQueries("opf-id-fallback", fallback)
        for (name in listOf("title", "creator", "language")) query("backend.metadata.$name", "opf", "//dc:$name", content, dc)
        query("backend.spine", "opf", "//ns:spine/ns:itemref", content, opf)
        val toc = KdomBuilder(allowDocType = true).build(File(dir, "toc.ncx"))
        for (site in listOf("backend.fileToTitle", "backend.tocKeys")) {
            val contents = query("$site.content", "ncx", "//ns:navPoint/ns:content", toc, ncx)
            for ((index, element) in contents.withIndex()) query("$site.label", "ncx-content-$index", "../ns:navLabel/ns:text", element, ncx)
        }
        for (chapter in 1..2) {
            val original = KdomBuilder(allowDocType = true).build(File(dir, "ch$chapter.xhtml"))
            query("optimizer.svg", "chapter$chapter", "//svg:image[@xlink:href]", original, svg, xlink)
            query("optimizer.img", "chapter$chapter", "//ns:img", original, xhtmlNamespace)
            query("optimizer.link", "chapter$chapter", "//ns:a", original, xhtmlNamespace)
            query("optimizer.body", "chapter$chapter", "//ns:body", original, xhtmlNamespace)
            text("facade.text", "chapter$chapter", original.rootElement, out, ::row, this)
            val body = original.rootElement.getChild("body", xhtmlNamespace)!!
            text("facade.text", "chapter$chapter-sub-element", body.children.first(), out, ::row, this)
            val anchored = original.clone()
            SwordContentFacade.addAnchors(anchored.rootElement.getChild("body", xhtmlNamespace)!!, "en", true)
            val anchoredBody = anchored.rootElement.getChild("body", xhtmlNamespace)!!
            val detached = anchoredBody.clone().detach()
            val contexts = listOf("document" to anchored, "detached-body" to detached, "sub-element" to anchoredBody.children.first())
            for ((contextName, context) in contexts) {
                for (site in listOf("backend.index.bva", "backend.totalCharacters.bva", "optimizer.ordinalRange")) query(site, "$contextName-chapter$chapter", "//ns:BVA", context, xhtmlNamespace)
                query("facade.bva", "$contextName-chapter$chapter", ".//ns:BVA", context, xhtmlNamespace)
                query("optimizer.ids", "$contextName-chapter$chapter", "//*[@id]", context)
                query("optimizer.write.body", "$contextName-chapter$chapter", "//ns:body", context, xhtmlNamespace)
            }
            val ordinals = XmlQueries.bvas(anchored, xhtmlNamespace).map { it.getAttributeValue("ordinal") }.distinct()
            for (ordinal in ordinals) query("optimizer.split", "chapter$chapter-ordinal-$ordinal", "//*[descendant::ns:BVA[@ordinal='$ordinal'] and (following-sibling::ns:p or preceding-sibling::ns:p or self::ns:p)]", anchored, xhtmlNamespace)
        }
    }

    // Independent jaxen sees a JDOM mirror of the entire context, including detached ancestry.
    private fun reference(context: Parent): Any {
        val output = fi.sykero.kdom.output.XMLOutputter()
        val indices = mutableListOf<Int>()
        var top = context
        while (top is Element && top.parent != null) {
            val parent = top.parent!!
            indices.add(parent.indexOf(top))
            top = parent
        }
        val xml = when (top) {
            is Document -> output.outputString(top)
            is Element -> output.outputString(top)
            else -> error("Unexpected context")
        }
        val mirror = org.jdom2.input.SAXBuilder().apply {
            setFeature("http://xml.org/sax/features/external-general-entities", false)
        }.build(StringReader(xml))
        var node: org.jdom2.Parent = if (top is Document) mirror else mirror.rootElement.detach()
        for (index in indices.asReversed()) node = node.getContent(index) as org.jdom2.Parent
        return node
    }

    private fun text(site: String, contextId: String, context: Element, out: XMLOutputter, row: (Array<out String>) -> String, target: StringBuilder) {
        val expression = ".//text()[not(ancestor::note)]"
        val results: List<Text> = XmlQueries.textsOutsideNotes(context)
        val reference = org.jdom2.xpath.XPathFactory.instance().compile(expression, org.jdom2.filter.Filters.text()).evaluate(reference(context))
        org.junit.Assert.assertEquals("Production text query $contextId", reference.map { org.jdom2.output.XMLOutputter(org.jdom2.output.Format.getRawFormat()).outputString(it) }, results.map { out.outputString(it) })
        target.append(row(arrayOf("query", site, contextId, expression, results.size.toString(), results.joinToString("\u001e") { out.outputString(it) })))
    }
}
