package net.bible.service.common

import org.jdom2.Namespace
import org.jdom2.input.SAXBuilder
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class XmlQueriesJaxenParityTest {
    @Test fun tocBranchesAreIndependentAndKeepPriority() {
        val ns = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
        val doc = SAXBuilder().build(StringReader("""<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="nav" properties="nav"/><item id="toc"/><item id="ncx" media-type="application/x-dtbncx+xml"/></manifest></package>"""))
        assertEquals("ncx", XmlQueries.tocItem(doc, ns)?.getAttributeValue("id"))
        assertEquals("toc", XmlQueries.tocIdItems(doc, ns).single().getAttributeValue("id"))
        assertEquals("nav", XmlQueries.tocNavItems(doc, ns).single().getAttributeValue("id"))
        XmlQueries.tocNcxItems(doc, ns).single().detach()
        assertEquals("toc", XmlQueries.tocItem(doc, ns)?.getAttributeValue("id"))
        XmlQueries.tocIdItems(doc, ns).single().detach()
        assertEquals("nav", XmlQueries.tocItem(doc, ns)?.getAttributeValue("id"))
    }

    @Test fun absoluteRelativeAndNoNamespaceNoteSemanticsArePreserved() {
        val ns = Namespace.getNamespace("ns", "http://www.w3.org/1999/xhtml")
        val doc = SAXBuilder().build(StringReader("""<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="one"><BVA ordinal="1">First</BVA><note>Namespaced</note><note xmlns="">Excluded</note></p><p><BVA ordinal="2">Second</BVA></p></body></html>"""))
        val body = XmlQueries.body(doc, ns)!!
        val p = body.children.first()
        assertEquals(listOf("1", "2"), XmlQueries.bvas(p, ns).map { it.getAttributeValue("ordinal") })
        assertEquals(listOf("1"), XmlQueries.bvasBelow(p, ns).map { it.getAttributeValue("ordinal") })
        assertEquals("FirstNamespaced", XmlQueries.textsOutsideNotes(p).joinToString("") { it.text })
        assertEquals("one", XmlQueries.splitPoint(doc, 1, ns)?.getAttributeValue("id"))
        assertTrue(XmlQueries.bvas(body.clone().detach(), ns).isEmpty())
        assertEquals(2, XmlQueries.bvasBelow(body.clone().detach(), ns).size)
    }
}
