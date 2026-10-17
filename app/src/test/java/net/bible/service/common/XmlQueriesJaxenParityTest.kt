package net.bible.service.common

import okio.Path.Companion.toOkioPath
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.k5.K5EpubFixture
import net.bible.service.sword.SwordContentFacade
import fi.sykero.kdom.Content
import fi.sykero.kdom.Document
import fi.sykero.kdom.Element
import fi.sykero.kdom.Namespace
import fi.sykero.kdom.Parent
import org.jdom2.filter.Filters
import fi.sykero.kdom.query.*
import fi.sykero.kdom.input.build
import org.jdom2.xpath.XPathFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class XmlQueriesJaxenParityTest {
    private val html = Namespace.getNamespace("ns", "http://www.w3.org/1999/xhtml")
    private val opf = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
    private val ncx = Namespace.getNamespace("ns", "http://www.daisy.org/z3986/2005/ncx/")
    private val dc = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
    private val container = Namespace.getNamespace("ns", "urn:oasis:names:tc:opendocument:xmlns:container")
    private val svg = Namespace.getNamespace("svg", "http://www.w3.org/2000/svg")
    private val xlink = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink")

    private fun parse(xml: String): Document = useKdomBuilder { it.build(StringReader(xml)) }

    // Include content positions (not just names/values), so duplicate siblings and result order remain observable.
    private fun pathKey(node: Content): String {
        val parent = node.parent
        val step = "${node.javaClass.simpleName}[${parent?.indexOf(node) ?: 0}]:${node.value}"
        return if (parent is Element) pathKey(parent) + "/" + step else step
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

    private fun referencePathKey(node: org.jdom2.Content): String {
        val parent = node.parent
        val step = "${node.javaClass.simpleName}[${parent?.indexOf(node) ?: 0}]:${node.value}"
        return if (parent is org.jdom2.Element) referencePathKey(parent) + "/" + step else step
    }

    private fun parity(expression: String, context: Any, actual: List<Element>, vararg ns: Namespace) {
        val expected = XPathFactory.instance().compile(expression, Filters.element(), null, *ns.map { org.jdom2.Namespace.getNamespace(it.prefix, it.uri) }.toTypedArray()).evaluate(reference(context as Parent))
        assertEquals("$expression from ${context.javaClass.simpleName}", expected.map(::referencePathKey), actual.map(::pathKey))
        assertEquals("No duplicate node identities", actual.size, actual.toSet().size)
    }

    private fun textParity(context: Element) {
        val expected = XPathFactory.instance().compile(".//text()[not(ancestor::note)]", Filters.text()).evaluate(reference(context))
        assertEquals(expected.map(::referencePathKey), XmlQueries.textsOutsideNotes(context).map(::pathKey))
    }

    private fun documentParity(doc: Document) {
        parity("//ns:rootfile", doc, XmlQueries.rootfiles(doc, container), container)
        parity("//ns:manifest/ns:item", doc, XmlQueries.manifestItems(doc, opf), opf)
        val ncxItems = XmlQueries.tocNcxItems(doc, opf)
        val idItems = XmlQueries.tocIdItems(doc, opf)
        val navItems = XmlQueries.tocNavItems(doc, opf)
        parity("//ns:manifest/ns:item[@media-type='application/x-dtbncx+xml']", doc, ncxItems, opf)
        parity("//ns:manifest/ns:item[@id='toc']", doc, idItems, opf)
        parity("//ns:manifest/ns:item[@properties='nav']", doc, navItems, opf)
        assertSame((ncxItems + idItems + navItems).firstOrNull(), XmlQueries.tocItem(doc, opf))
        parity("//ns:navPoint/ns:content", doc, XmlQueries.navPointContents(doc, ncx), ncx)
        for (key in listOf("identifier", "title", "creator", "language", "missing")) {
            parity("//dc:$key", doc, XmlQueries.dcElements(doc, key, dc), dc)
        }
        parity("//ns:spine/ns:itemref", doc, XmlQueries.spineItemrefs(doc, opf), opf)
    }

    private fun contextParity(context: Parent) {
        parity("//ns:BVA", context, XmlQueries.bvas(context, html), html)
        parity("//ns:body", context, listOfNotNull(XmlQueries.body(context, html)), html)
        parity("//*[@id]", context, XmlQueries.elementsWithId(context))
        if (context is Element) {
            parity(".//ns:BVA", context, XmlQueries.bvasBelow(context, html), html)
            parity("//svg:image[@xlink:href]", context, XmlQueries.svgImagesWithHref(context, svg, xlink), svg, xlink)
            parity("//ns:img", context, XmlQueries.imgs(context, html), html)
            parity("//ns:a", context, XmlQueries.links(context, html), html)
            parity("../ns:navLabel/ns:text", context, XmlQueries.navLabelTexts(context, ncx), ncx)
            textParity(context)
        }
    }

    private fun splitParity(doc: Document, ordinal: Int) {
        val expression = "//*[descendant::ns:BVA[@ordinal='$ordinal'] and (following-sibling::ns:p or preceding-sibling::ns:p or self::ns:p)]"
        parity(expression, doc, XmlQueries.splitPoints(doc, ordinal, html), html)
        val expected = XPathFactory.instance().compile(expression, Filters.element(), null, org.jdom2.Namespace.getNamespace(html.prefix, html.uri)).evaluateFirst(reference(doc))
        assertEquals(expected?.let(::referencePathKey), XmlQueries.splitPoint(doc, ordinal, html)?.let(::pathKey))
    }

    @Test fun allQueriesMatchOriginalExpressionsOnDeterministicFixture() {
        val tmp = kotlin.io.path.createTempDirectory("xml-query-parity").toFile()
        try {
            ZipFile(K5EpubFixture.create(tmp)).use { zip ->
                for (name in listOf("META-INF/container.xml", "content.opf", "toc.ncx", "nav.xhtml", "ch1.xhtml", "ch2.xhtml", "image.svg")) {
                    val doc = useKdomBuilder { builder -> zip.getInputStream(zip.getEntry(name)).use { builder.build(it) } }
                    documentParity(doc)
                    // Relative queries work on detached trees; absolute queries must NOT search a detached root.
                    val body = doc.rootElement.getChild("body", html) ?: doc.rootElement
                    for (context in listOf(doc, doc.rootElement, body, body.children.firstOrNull() ?: body,
                        body.clone().detach(), (body.children.firstOrNull() ?: body).clone().detach())) {
                        contextParity(context)
                    }
                    XmlQueries.navPointContents(doc, ncx).forEach { content ->
                        parity("../ns:navLabel/ns:text", content, XmlQueries.navLabelTexts(content, ncx), ncx)
                        val detachedParent = content.parentElement!!.clone().detach()
                        val detachedContent = detachedParent.getChild("content", ncx)
                        parity("../ns:navLabel/ns:text", detachedContent!!, XmlQueries.navLabelTexts(detachedContent!!, ncx), ncx)
                    }
                    if (name.startsWith("ch")) {
                        SwordContentFacade.addAnchors(body, "en", true)
                        for (context in listOf(doc, body, body.children.first(), body.clone().detach())) contextParity(context)
                        val ordinals = XmlQueries.bvas(doc, html).map { it.getAttributeValue("ordinal")!!.toInt() }
                        assertTrue("Anchored fixture must exercise nonempty BVA queries", ordinals.isNotEmpty())
                        for (ordinal in listOf(ordinals.first(), ordinals[ordinals.size / 2], ordinals.last(), -1)) splitParity(doc, ordinal)
                    }
                }
            }
        } finally { tmp.deleteRecursively() }
    }

    @Test fun splitPointQueriesMatchOnDocumentsProducedByRealSplitIntoN() {
        val tmp = kotlin.io.path.createTempDirectory("xml-split-parity").toFile()
        val sourceDir = java.io.File(tmp, "source")
        val moduleDir = java.io.File(tmp, "XmlQueriesSplitFixture")
        var state: net.bible.service.sword.epub.EpubBackendState? = null
        try {
            val source = K5EpubFixture.create(sourceDir)
            ArchiveUtils.unzipFile(source, moduleDir)
            state = net.bible.service.sword.epub.EpubBackendState(
                moduleDir.toOkioPath(), net.bible.test.testCoreStrings(),
                net.bible.android.platform.AndroidAppEnvironment(net.bible.android.BibleApplication.application),
            )
            val fragments = java.io.File(moduleDir, "optimized").listFiles()!!
                .filter { it.name.endsWith(".xhtml.gz") }
            assertTrue("Long fixture must reach splitIntoN, not just unsplit chapters", fragments.size > 2)
            var checkedLongFragments = 0
            for (file in fragments) {
                val doc = useKdomBuilder { builder ->
                    java.util.zip.GZIPInputStream(file.inputStream()).use { builder.build(it) }
                }
                val ordinals = XmlQueries.bvas(doc, html).map { it.getAttributeValue("ordinal")!!.toInt() }
                assertTrue(ordinals.isNotEmpty())
                for (ordinal in listOf(ordinals.first(), ordinals[ordinals.size / 2], ordinals.last(), -1)) {
                    splitParity(doc, ordinal)
                }
                if (XmlQueries.elementsWithId(doc).any { it.getAttributeValue("id")!!.startsWith("long-") }) {
                    checkedLongFragments++
                }
                contextParity(doc)
                // writeFragment renames the cropped body to div before serialization.
                contextParity(doc.rootElement.clone().detach())
            }
            assertTrue("Must query at least two cloned/cropped long-chapter documents", checkedLongFragments >= 2)
        } finally {
            state?.delete()
            tmp.deleteRecursively()
        }
    }

    @Test fun nestedParentsKeepDocumentOrderForEveryTwoStepQuery() {
        for ((parent, child, ns, query) in listOf(
            Quad("manifest", "item", opf, { d: Document -> XmlQueries.manifestItems(d, opf) }),
            Quad("spine", "itemref", opf, { d: Document -> XmlQueries.spineItemrefs(d, opf) }),
            Quad("navPoint", "content", ncx, { d: Document -> XmlQueries.navPointContents(d, ncx) }),
        )) {
            val doc = parse("""<root xmlns="${ns.uri}"><$parent><$child id="a"/><$parent><$child id="b"/></$parent><$child id="c"/></$parent></root>""")
            parity("//ns:$parent/ns:$child", doc, query(doc), ns)
            assertEquals(listOf("a", "b", "c"), query(doc).map { it.getAttributeValue("id") })
            assertEquals(listOf("a", "b", "c"), doc.findChildrenOf(parent, ns, child, ns).map { it.getAttributeValue("id") })
        }
        val doc = parse("""<package xmlns="${opf.uri}"><manifest><item id="a" properties="nav" media-type="application/x-dtbncx+xml"/><manifest><item id="toc" properties="nav" media-type="application/x-dtbncx+xml"/></manifest><item id="c" properties="nav" media-type="application/x-dtbncx+xml"/></manifest></package>""")
        documentParity(doc)
    }

    private data class Quad(val parent: String, val child: String, val ns: Namespace, val query: (Document) -> List<Element>)

    @Test fun axesMatchKdomContractIncludingDetachedRootsAndReverseSiblings() {
        val doc = parse("""<r><p id="a"/>text<![CDATA[cdata]]><p id="b"><BVA/></p><!-- gap --><p id="c"/></r>""")
        val root = doc.rootElement
        val middle = root.children[1]
        assertSame(doc, middle.xpathRoot())
        assertEquals(listOf("r", "p", "p", "BVA", "p"), doc.allElements().map { it.name }.toList())
        assertEquals(listOf("p", "p", "BVA", "p"), root.descendantElements().map { it.name }.toList())
        assertEquals(listOf("text", "cdata"), root.descendantTexts().map { it.text }.toList())
        assertEquals(listOf(root), middle.ancestorElements().toList())
        assertEquals(listOf(root.children.last()), middle.followingSiblingElements().toList())
        assertEquals(listOf(root.children.first()), middle.precedingSiblingElements().toList())
        assertEquals(listOf("b", "a"), root.children.last().precedingSiblingElements().map { it.getAttributeValue("id") }.toList())
        assertEquals(root.children, root.childElements().toList())
        assertTrue(middle.isNamed("p", Namespace.NO_NAMESPACE))
        assertTrue(middle.hasAttribute("id"))
        assertTrue(middle.attributeIs("id", "b"))
        assertFalse(middle.attributeIs("id", "B"))
        assertFalse(root.hasAttribute("id"))
        assertEquals(3, middle.findAll("p", Namespace.NO_NAMESPACE).size)
        root.detach()
        assertNull(root.xpathRoot())
        assertNull(middle.xpathRoot())
        assertTrue(middle.findAll("p", Namespace.NO_NAMESPACE).isEmpty())
        assertTrue(root.findChildrenOf("r", Namespace.NO_NAMESPACE, "p", Namespace.NO_NAMESPACE).isEmpty())
        assertTrue(doc.allElements().toList().isEmpty())
    }

    @Test fun tocBranchesAreIndependentAndKeepPriority() {
        val doc = parse("""<package xmlns="${opf.uri}"><manifest><item id="nav" properties="nav"/><item id="toc"/><item id="ncx" media-type="application/x-dtbncx+xml"/></manifest></package>""")
        documentParity(doc)
        assertEquals("ncx", XmlQueries.tocItem(doc, opf)?.getAttributeValue("id"))
        XmlQueries.tocNcxItems(doc, opf).single().detach()
        documentParity(doc)
        assertEquals("toc", XmlQueries.tocItem(doc, opf)?.getAttributeValue("id"))
        XmlQueries.tocIdItems(doc, opf).single().detach()
        documentParity(doc)
        assertEquals("nav", XmlQueries.tocItem(doc, opf)?.getAttributeValue("id"))
        XmlQueries.tocNavItems(doc, opf).single().detach()
        assertNull(XmlQueries.tocItem(doc, opf))
    }

    @Test fun absoluteRelativeAndNoNamespaceNoteSemanticsArePreserved() {
        val doc = parse("""<html xmlns="${html.uri}"><body><p id="one"><BVA ordinal="1">First</BVA><note>Namespaced<![CDATA[ included]]></note><note xmlns="">Excluded<span>nested</span></note></p><p><BVA ordinal="2">Second</BVA></p></body></html>""")
        val body = XmlQueries.body(doc, html)!!
        val p = body.children.first()
        assertEquals(listOf("1", "2"), XmlQueries.bvas(p, html).map { it.getAttributeValue("ordinal") })
        assertEquals(listOf("1"), XmlQueries.bvasBelow(p, html).map { it.getAttributeValue("ordinal") })
        assertEquals("FirstNamespaced included", XmlQueries.textsOutsideNotes(p).joinToString("") { it.text })
        assertTrue(XmlQueries.textsOutsideNotes(p.getChild("note", Namespace.NO_NAMESPACE)!!).isEmpty())
        textParity(p)
        splitParity(doc, 1)
        contextParity(body.clone().detach())
        assertTrue(XmlQueries.bvas(body.clone().detach(), html).isEmpty())
        assertEquals(2, XmlQueries.bvasBelow(body.clone().detach(), html).size)
        assertTrue(XmlQueries.bvasBelow(p.children.first(), html).isEmpty()) // excludes self
    }

    @Test fun namespaceUrisAndUnqualifiedAttributesNotPrefixesControlMatches() {
        val doc = parse("""<h:html xmlns:h="${html.uri}" xmlns:s="${svg.uri}" xmlns:x="${xlink.uri}"><h:body><h:img id="one"/><img/><s:image x:href="a"/><s:image href="b"/><h:a h:id="namespaced"/><h:a id="plain"/><h:p><h:BVA ordinal="01"/></h:p><h:p><h:BVA ordinal="1"/></h:p></h:body></h:html>""")
        contextParity(doc.rootElement)
        assertEquals(1, XmlQueries.imgs(doc.rootElement, html).size)
        assertEquals(1, XmlQueries.svgImagesWithHref(doc.rootElement, svg, xlink).size)
        assertEquals(listOf("one", "plain"), XmlQueries.elementsWithId(doc).map { it.getAttributeValue("id") })
        splitParity(doc, 1) // equality is lexical, not numeric
        assertEquals(1, XmlQueries.splitPoints(doc, 1, html).size)
        splitParity(doc, 0)
        // kdom's axes explicitly allow an empty Document; jaxen instead throws for this context.
        assertTrue(Document().allElements().toList().isEmpty())
    }

    @Test fun queryListsAreEagerSnapshotsBeforeCallersDetachOrMoveNodes() {
        val doc = parse("""<html xmlns="${html.uri}"><body><p id="a"><BVA ordinal="1">one</BVA><a/><img/></p><p id="b"><BVA ordinal="2">two</BVA><a/><img/></p><p id="c"><BVA ordinal="3">three</BVA><a/><img/></p></body></html>""")
        val body = XmlQueries.body(doc, html)!!
        val ids = XmlQueries.elementsWithId(doc)
        val bvas = XmlQueries.bvas(doc, html)
        val below = XmlQueries.bvasBelow(body, html)
        val texts = XmlQueries.textsOutsideNotes(body)
        val links = XmlQueries.links(body, html)
        val imgs = XmlQueries.imgs(body, html)
        val splits = XmlQueries.splitPoints(doc, 2, html)
        ids.forEach { it.detach() }
        assertEquals(listOf("a", "b", "c"), ids.map { it.getAttributeValue("id") })
        assertEquals(listOf("1", "2", "3"), bvas.map { it.getAttributeValue("ordinal") })
        assertEquals(bvas, below)
        assertEquals(listOf("one", "two", "three"), texts.map { it.text })
        assertEquals(3, links.size)
        assertEquals(3, imgs.size)
        assertEquals(listOf("b"), splits.map { it.getAttributeValue("id") })
        val target = Element("target")
        bvas.forEach { target.addContent(it.detach()) }
        assertEquals(3, target.children.size)
        assertTrue(XmlQueries.bvas(doc, html).isEmpty())
    }
}
