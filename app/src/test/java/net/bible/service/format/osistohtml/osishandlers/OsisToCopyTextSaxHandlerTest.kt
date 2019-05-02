package net.bible.service.format.osistohtml.osishandlers

import org.junit.Before
import org.junit.Test

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.xml.sax.helpers.AttributesImpl

class OsisToCopyTextSaxHandlerTest {

    private var osisToCopyTextSaxHandlerSingleVerse: OsisToCopyTextSaxHandler? = null
    private var osisToCopyTextSaxHandlerMultipleVerses: OsisToCopyTextSaxHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToCopyTextSaxHandlerSingleVerse = OsisToCopyTextSaxHandler(false)
        osisToCopyTextSaxHandlerMultipleVerses = OsisToCopyTextSaxHandler(true)

        osisToCopyTextSaxHandlerSingleVerse!!.startDocument()
        osisToCopyTextSaxHandlerMultipleVerses!!.startDocument()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleVerse() {
        osisToCopyTextSaxHandlerSingleVerse!!.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerSingleVerse!!.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerSingleVerse!!.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerSingleVerse!!.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerSingleVerse!!.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerSingleVerse!!.writer.html, equalTo("And we know that all things... "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVerses() {
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerMultipleVerses!!.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerMultipleVerses!!.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "reference", null)
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.29") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("For whom he did foreknow".toCharArray(), 0, 24)
        osisToCopyTextSaxHandlerMultipleVerses!!.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.30") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("Moreover whom he did predestinate".toCharArray(), 0, 33)
        osisToCopyTextSaxHandlerMultipleVerses!!.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerMultipleVerses!!.writer.html, equalTo("28. And we know that all things... 29. For whom he did foreknow 30. Moreover whom he did predestinate "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVersesAcrossChapter() {
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"John.3.36") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("  ".toCharArray(), 0, 2)
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("He that believeth on the Son hath everlasting life: and".toCharArray(), 0, 55)
        osisToCopyTextSaxHandlerMultipleVerses!!.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("he that believeth not the Son shall not see life; but the wrath of God abideth on him.".toCharArray(), 0, 86)
        osisToCopyTextSaxHandlerMultipleVerses!!.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerMultipleVerses!!.endElement(null, null, "chapter")
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"John.4") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"John.4.1") }
        )
        osisToCopyTextSaxHandlerMultipleVerses!!.characters("When therefore the Lord knew...".toCharArray(), 0, 31)
        osisToCopyTextSaxHandlerMultipleVerses!!.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerMultipleVerses!!.writer.html, equalTo("36. He that believeth on the Son hath everlasting life: and he that believeth not the Son shall not see life; but the wrath of God abideth on him. 4:1. When therefore the Lord knew... "))
    }

}
