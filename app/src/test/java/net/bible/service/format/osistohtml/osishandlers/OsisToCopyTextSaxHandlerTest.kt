package net.bible.service.format.osistohtml.osishandlers

import org.junit.Before
import org.junit.Test

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.xml.sax.helpers.AttributesImpl

class OsisToCopyTextSaxHandlerTest {

    lateinit var osisToCopyTextSaxHandlerVerseNumbersOff: OsisToCopyTextSaxHandler
    lateinit var osisToCopyTextSaxHandlerVerseNumbersOn: OsisToCopyTextSaxHandler

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToCopyTextSaxHandlerVerseNumbersOff = OsisToCopyTextSaxHandler(false)
        osisToCopyTextSaxHandlerVerseNumbersOn = OsisToCopyTextSaxHandler(true)

        osisToCopyTextSaxHandlerVerseNumbersOff.startDocument()
        osisToCopyTextSaxHandlerVerseNumbersOn.startDocument()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleVerse() {
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerVerseNumbersOff.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOff.writer.html, equalTo("And we know that all things... "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVerses() {
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "reference", null)
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.29") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("For whom he did foreknow".toCharArray(), 0, 24)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.30") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("Moreover whom he did predestinate".toCharArray(), 0, 33)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOn.writer.html, equalTo("28. And we know that all things... 29. For whom he did foreknow 30. Moreover whom he did predestinate "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVersesWithVerseNumbersOff() {
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerVerseNumbersOff.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "reference", null)
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.29") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("For whom he did foreknow".toCharArray(), 0, 24)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"Rom.8.30") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("Moreover whom he did predestinate".toCharArray(), 0, 33)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOff.writer.html, equalTo("And we know that all things... For whom he did foreknow Moreover whom he did predestinate "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVersesAcrossChapter() {
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"John.3.36") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("  ".toCharArray(), 0, 2)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("He that believeth on the Son hath everlasting life: and".toCharArray(), 0, 55)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("he that believeth not the Son shall not see life; but the wrath of God abideth on him.".toCharArray(), 0, 86)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement(null, null, "verse")
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement(null, null, "chapter")
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "chapter",
            AttributesImpl().apply { addAttribute("","osisID","chapter",null,"John.4") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement(null, null, "verse",
            AttributesImpl().apply { addAttribute("","osisID","verse",null,"John.4.1") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("When therefore the Lord knew...".toCharArray(), 0, 31)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement(null, null, "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOn.writer.html, equalTo("36. He that believeth on the Son hath everlasting life: and he that believeth not the Son shall not see life; but the wrath of God abideth on him. 4:1. When therefore the Lord knew... "))
    }

}
