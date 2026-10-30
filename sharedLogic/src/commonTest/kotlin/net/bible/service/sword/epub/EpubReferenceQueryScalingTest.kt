package net.bible.service.sword.epub

import fi.sykero.kdom.Content
import fi.sykero.kdom.Document
import fi.sykero.kdom.Element
import fi.sykero.kdom.Namespace
import net.bible.service.common.XmlQueries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #3568: reference discovery must not revisit the whole chapter for each link or image. */
class EpubReferenceQueryScalingTest {
    private class Work {
        var reads = 0
    }

    private class CountedElement(name: String, ns: Namespace, private val work: Work) : Element(name, ns) {
        override fun getContent(index: Int): Content {
            work.reads++
            return super.getContent(index)
        }
    }

    @Test
    fun referenceQueriesVisitChapterContentLinearly() {
        val svg = Namespace.getNamespace("svg", "http://www.w3.org/2000/svg")
        val xlink = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink")
        val foreign = Namespace.getNamespace("foreign", "urn:foreign")
        for (linkCount in listOf(128, 1024, 8192)) {
            val work = Work()
            val root = CountedElement("html", xhtmlNamespace, work)
            Document(root)
            val body = CountedElement("body", xhtmlNamespace, work)
            root.addContent(body)
            repeat(linkCount) {
                val group = CountedElement("div", xhtmlNamespace, work)
                group.addContent(CountedElement("a", xhtmlNamespace, work).setAttribute("href", "next.xhtml#target"))
                group.addContent(CountedElement("img", xhtmlNamespace, work).setAttribute("src", "picture.png"))
                group.addContent(CountedElement("image", svg, work).setAttribute("href", "picture.svg", xlink))
                group.addContent(CountedElement("image", svg, work)) // no xlink:href
                group.addContent(CountedElement("a", foreign, work))
                body.addContent(group)
            }
            work.reads = 0

            assertEquals(linkCount, XmlQueries.svgImagesWithHref(root, svg, xlink).size)
            assertEquals(linkCount, XmlQueries.imgs(root, xhtmlNamespace).size)
            assertEquals(linkCount, XmlQueries.links(root, xhtmlNamespace).size)

            // Three bounded scans; counting real tree reads avoids machine-dependent timing assertions.
            val contentCount = 1 + 6 * linkCount
            assertTrue(work.reads <= 4 * contentCount, "$linkCount links required ${work.reads} content reads")
            assertTrue(work.reads >= contentCount, "the counter must see traversal work")
            println("EPUB reference queries: $linkCount links, ${work.reads} content reads")
        }
    }
}
