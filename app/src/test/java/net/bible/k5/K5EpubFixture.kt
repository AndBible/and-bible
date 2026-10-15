package net.bible.k5

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Fixed ZIP metadata and insertion order make the source EPUB byte-for-byte reproducible. */
object K5EpubFixture {
    fun create(dir: File): File {
        dir.mkdirs()
        val files = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">k5-fixed</dc:identifier><dc:title>K5 Oracle</dc:title><dc:creator>AndBible tests</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/><item id="svg" href="image.svg" media-type="image/svg+xml"/><item id="png" href="i.png" media-type="image/png"/></manifest><spine toc="ncx"><itemref idref="ch1"/><itemref idref="ch2"/></spine></package>""",
            "toc.ncx" to """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>K5 Oracle</text></docTitle><navMap><navPoint id="one" playOrder="1"><navLabel><text>One</text></navLabel><content src="ch1.xhtml"/><navPoint id="nested" playOrder="2"><navLabel><text>Paragraph</text></navLabel><content src="ch1.xhtml#p1"/></navPoint></navPoint><navPoint id="two" playOrder="3"><navLabel><text>Two</text></navLabel><content src="ch2.xhtml#x"/></navPoint></navMap></ncx>""",
            "nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Navigation</title></head><body><nav epub:type="toc"><ol><li><a href="ch1.xhtml">One</a></li><li><a href="ch2.xhtml#x">Two</a></li></ol></nav></body></html>""",
            "ch1.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:svg="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><head><title>One</title></head><body><p id="p1">Grace and peace. <a href="ch2.xhtml#x">Next</a></p><p id="p2">A second paragraph.<img src="i.png" alt="pixel"/><svg:svg><svg:image xlink:href="i.png"/></svg:svg><note id="namespaced-note">XHTML note</note><note xmlns="" id="plain-note">Plain note</note></p></body></html>""",
            "ch2.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head><body><h1 id="x">Long chapter</h1>""" + (1..1200).joinToString("") { "<p id=\"long-$it\">Short paragraph $it.</p>" } + "</body></html>",
            "image.svg" to """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>""",
        )
        val output = File(dir, "k5.epub")
        ZipOutputStream(output.outputStream()).use { zip ->
            for ((name, text) in files) put(zip, name, text.toByteArray())
            put(zip, "i.png", java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII="))
        }
        return output
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply {
            time = 0L
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = size
            crc = CRC32().apply { update(bytes) }.value
        })
        zip.write(bytes)
        zip.closeEntry()
    }
}
