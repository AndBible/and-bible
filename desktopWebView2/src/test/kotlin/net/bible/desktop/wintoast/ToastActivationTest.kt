package net.bible.desktop.wintoast

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import net.bible.desktop.webview2.ComPtr
import net.bible.desktop.webview2.Iid
import kotlin.test.*

class ToastActivationTest {
    @Test fun payloadRoundTripsLongUnicodeIdAndRejectsOtherCommands() {
        val id = "download:<&>ä/" + "x".repeat(100)
        val payload = toastActivationArguments(id)
        assertEquals(id, parseToastActivationArguments(payload))
        for (bad in listOf("", "-Embedding", "job", "andbible-toast:v1:!", "andbible-toast:v1:" + "a".repeat(9000))) {
            assertNull(parseToastActivationArguments(bad))
        }
        for (switch in listOf("-Embedding", "/Embedding", "-ToastActivated", "-embedding")) {
            assertNull(toastLaunchFiles(listOf(switch, "document.epub")))
        }
        assertEquals(listOf("document.epub"), toastLaunchFiles(listOf("document.epub")))
    }

    @Test fun outstandingComReferenceRemainsValidButCannotActivateAfterServerClose() {
        var called = false
        val server = ToastComServer { _, _ -> called = true }
        val out = Memory(Native.POINTER_SIZE.toLong())
        assertEquals(0, ComPtr(server.factoryPointer).call(3, null, Iid("53E31837-6600-4A81-9395-75CFFE746F94").memory, out))
        val activation = ComPtr(out.getPointer(0))
        server.close()
        assertTrue(server.isLive, "COM-held interface freed before native Release")
        assertEquals(0, activation.call(3, null, null, null, 0))
        assertFalse(called)
        assertEquals(0, activation.call(2))
        assertFalse(server.isLive)
    }

    @Test fun factoryCreatesOnlyActivationInterfaceRejectsAggregationAndReleasesReferences() {
        val calls = mutableListOf<Pair<String, String>>()
        val server = ToastComServer { app, args -> calls += app to args }
        val factory = ComPtr(server.factoryPointer)
        val output = Memory(Native.POINTER_SIZE.toLong())
        val activationIid = Iid("53E31837-6600-4A81-9395-75CFFE746F94")
        assertEquals(0x80040110.toInt(), factory.call(3, Pointer(1), activationIid.memory, output))
        assertEquals(0x80004002.toInt(), factory.call(3, null, Iid("11111111-1111-1111-1111-111111111111").memory, output))
        assertNull(output.getPointer(0))
        assertEquals(0, factory.call(3, null, activationIid.memory, output))
        val activator = ComPtr(output.getPointer(0))
        fun wide(s: String) = Memory((s.length + 1L) * 2).apply { s.forEachIndexed { i, c -> setShort(i * 2L, c.code.toShort()) }; setShort(s.length * 2L, 0) }
        val app = wide("org.andbible.AndBible")
        val args = wide("andbible-toast:v1:am9i")
        assertEquals(0, activator.call(3, app, args, null, 0))
        assertEquals(listOf("org.andbible.AndBible" to "andbible-toast:v1:am9i"), calls)
        assertEquals(0, factory.call(4, 1))
        assertEquals(0, factory.call(4, 0))
        activator.release()
        server.close()
        assertFalse(server.isLive)
        server.close()
    }
}
