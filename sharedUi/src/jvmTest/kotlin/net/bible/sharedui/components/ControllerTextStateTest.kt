package net.bible.sharedui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerTextStateTest {
    @Test fun lateEchoesOfOwnEditsNeverOverwriteNewerInput() {
        val s = ControllerTextState("")
        val received = mutableListOf<String>()
        s.edit("n", received::add)
        s.edit("no", received::add)
        s.controllerChanged("n")
        assertEquals("no", s.text)
        s.controllerChanged("no")
        assertEquals("no", s.text)
        assertEquals(listOf("n", "no"), received)
    }

    @Test fun skippedIntermediateEchoesAreTolerated() {
        val s = ControllerTextState("")
        s.edit("a", {}); s.edit("ab", {}); s.edit("abc", {})
        s.controllerChanged("abc")
        assertEquals("abc", s.text)
        s.edit("abcd", {})
        s.controllerChanged("abcd")
        assertEquals("abcd", s.text)
    }

    @Test fun externalWritesAreTakenOver() {
        val s = ControllerTextState("typed")
        s.controllerChanged("https://pasted")
        assertEquals("https://pasted", s.text)
        s.edit("https://pasted/x", {})
        s.controllerChanged("/validated")
        assertEquals("/validated", s.text)
    }
}
