package net.bible.desktop.wintoast

import com.sun.jna.Pointer
import net.bible.desktop.webview2.ComPtr
import net.bible.desktop.webview2.Iid
import net.bible.sharedcore.platform.NativeNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Exercises the native ABI adapter with owned COM interfaces but without loading any Windows library. */
class WinRtToastApiTest {
    @Test
    fun showUsesHstringsMapAndProcessActivatorWithoutRetainingToasts() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi("org.andbible.AndBible") { runtime }
        val clicked = mutableListOf<String>()
        api.onActivated { clicked += it }
        assertEquals(0, api.show("<toast/>", "job", "andbible", mapOf("progressValue" to "0.42")))
        assertEquals(listOf("initialize", "register"), runtime.events.take(2))
        assertTrue(runtime.events.contains("CreateToastNotifierWithId:org.andbible.AndBible"))
        assertTrue(runtime.events.contains("LoadXml:<toast/>"))
        assertTrue(runtime.events.contains("Tag:job"))
        assertTrue(runtime.events.contains("Group:andbible"))
        assertTrue(runtime.events.contains("Insert:progressValue:0.42"))
        assertTrue(runtime.events.indexOf("put_Data") < runtime.events.indexOf("Show"))
        runtime.activate()
        assertEquals(listOf("andbible-toast:v1:am9i"), clicked)
        assertEquals(0, api.show("<toast/>", "job", "andbible", emptyMap()))
        assertEquals(1, runtime.events.count { it == "register" })
        api.close()
        assertEquals(1, runtime.events.count { it == "revoke:42" })
        assertEquals("uninitialize", runtime.events.last())
        assertTrue(runtime.events.indexOf("register") < runtime.events.indexOf("CreateToastNotifierWithId:org.andbible.AndBible"))
        assertTrue(runtime.events.indexOf("revoke:42") < runtime.events.lastIndexOf("release:notifier"))
        runtime.assertReleased()
    }

    @Test
    fun installingNotifierActivationRegistersHistoryCallbackBeforeAnyNewToast() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(ANDBIBLE_AUMID) { runtime }
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        val clicked = mutableListOf<String>()
        try {
            notifier.setOnActivated { clicked += it }
            assertTrue(runtime.events.contains("register"), "history activator absent until a new notification")
            assertFalse(runtime.events.contains("Show"))
            runtime.activate()
            assertEquals(listOf("job"), clicked)
        } finally { notifier.close() }
        runtime.assertReleased()
    }

    @Test
    fun updateChecksResultAndRemoveUsesExplicitOrPackageIdentity() {
        for (aumid in listOf("org.andbible.AndBible", null)) {
            val runtime = RecordingWinRt()
            val api = WinRtToastApi(aumid) { runtime }
            api.show("<toast/>", "job", "andbible", emptyMap())
            runtime.updateResult = 2
            assertEquals(1, api.update("job", "andbible", mapOf("status" to "Working")))
            runtime.updateResult = 1
            assertTrue(api.update("job", "andbible", emptyMap()) < 0)
            runtime.updateResult = 0
            assertEquals(0, api.update("job", "andbible", emptyMap()))
            assertEquals(0, api.remove("job", "andbible"))
            assertTrue(runtime.events.contains(if (aumid == null) "CreateToastNotifier" else "CreateToastNotifierWithId:$aumid"))
            assertTrue(runtime.events.contains(if (aumid == null) "RemoveGroupedTag:job:andbible" else "RemoveGroupedTagWithId:job:andbible:$aumid"))
            api.close()
            runtime.assertReleased()
        }
    }

    @Test
    fun failedShowReleasesTemporaryObjectsAndCallbackAndPreservesPriorToast() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        val clicked = mutableListOf<String>()
        api.onActivated { clicked += it }
        api.show("<toast/>", "job", "andbible", emptyMap())
        val oldCallback = runtime.activator
        runtime.fail = "Show"
        assertTrue(api.show("<toast/>", "job", "andbible", emptyMap()) < 0)
        assertEquals(oldCallback, runtime.activator, "failed Show replaced process activation")
        runtime.activate()
        assertEquals(listOf("andbible-toast:v1:am9i"), clicked)
        runtime.fail = null
        api.close()
        runtime.assertReleased()
    }

    @Test
    fun failingInitializationAndHstringInsertionBalanceOwnership() {
        for (failure in listOf("initialize", "CreateToastNotifier", "LoadXml", "Insert", "register", "put_Data")) {
            val runtime = RecordingWinRt().apply { fail = failure }
            val api = WinRtToastApi(null) { runtime }
            assertTrue(api.show("<toast/>", "job", "andbible", mapOf("key" to "value")) < 0, failure)
            api.close()
            runtime.assertReleased()
            assertEquals(if (failure == "initialize") 0 else 1, runtime.events.count { it == "uninitialize" })
        }
    }

    @Test
    fun manyDistinctFinalToastsReleaseAllNativeObjectsWhileHistoryClickStillWorks() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        val clicked = mutableListOf<String>()
        api.onActivated { clicked += it }
        repeat(2000) {
            assertEquals(0, api.show("<toast/>", "final-$it", "andbible", emptyMap()))
            assertEquals(4, runtime.objectCount(), "per-toast COM reference leaked")
        }
        runtime.activate()
        assertEquals(listOf("andbible-toast:v1:am9i"), clicked)
        api.close()
        runtime.assertReleased()
    }

    @Test
    fun processActivationRejectsForeignAumidAndMalformedPayloadWithoutHistoryState() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(ANDBIBLE_AUMID) { runtime }
        val clicked = mutableListOf<String>()
        api.onActivated { clicked += it }
        api.show("<toast/>", "job", "andbible", emptyMap())
        runtime.activate("another.app", "andbible-toast:v1:am9i")
        runtime.activate(ANDBIBLE_AUMID, "-Embedding")
        runtime.activate(ANDBIBLE_AUMID, "andbible-toast:v1:!")
        assertTrue(clicked.isEmpty())
        runtime.activate()
        assertEquals(listOf("andbible-toast:v1:am9i"), clicked)
        api.close()
        runtime.assertReleased()
    }

    @Test
    fun failedRevokeStillReleasesFactoryAndRootsAndUninitializes() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        api.show("<toast/>", "job", "andbible", emptyMap())
        runtime.fail = "revoke"
        assertFailsWith<net.bible.desktop.webview2.ComException> { api.close() }
        assertEquals("uninitialize", runtime.events.last())
        runtime.assertReleased()
    }

    @Test
    fun nativeCallsRejectASecondOwnerThreadAndCloseIsIdempotent() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        api.show("<toast/>", "job", "andbible", emptyMap())
        var caught: Throwable? = null
        Thread { caught = runCatching { api.remove("job", "andbible") }.exceptionOrNull() }.apply { start(); join() }
        assertTrue(caught is IllegalStateException)
        api.close()
        api.close()
        assertFailsWith<IllegalStateException> { api.show("<toast/>", "job", "andbible", emptyMap()) }
        runtime.assertReleased()
    }

    private class RecordingWinRt : WinRtCalls {
        val events = mutableListOf<String>()
        var fail: String? = null
        var updateResult = 0
        private var nextPointer = 1L
        private val strings = mutableMapOf<Pointer, String>()
        private val objects = mutableSetOf<ToastCom>()
        var activator: ComPtr? = null
        private var registeredFactory: ComPtr? = null
        override fun registerActivator(factory: Pointer): Int {
            events += "register"
            if (fail == "register") throw net.bible.desktop.webview2.ComException("register", 0x80004005.toInt())
            registeredFactory = ComPtr(factory).also { it.addRef() }
            val output = com.sun.jna.ptr.PointerByReference()
            assertEquals(0, ComPtr(factory).call(3, null, Iid("53E31837-6600-4A81-9395-75CFFE746F94").memory, output))
            activator = ComPtr(output.value)
            return 42
        }
        override fun revokeActivator(cookie: Int) {
            events += "revoke:$cookie"
            assertEquals(1, activator!!.call(2), "creator-owned activator should remain until server close")
            assertEquals(1, registeredFactory!!.call(2), "creator-owned factory should remain until server close")
            activator = null; registeredFactory = null
            if (fail == "revoke") throw net.bible.desktop.webview2.ComException("revoke", 0x80004005.toInt())
        }
        private fun string(value: Any?) = strings[value as Pointer?] ?: ""
        private fun result(name: String): Int = if (fail == name) 0x80004005.toInt() else 0
        override fun initialize(): Int { events += "initialize"; return result("initialize") }
        override fun uninitialize() { events += "uninitialize" }
        override fun createString(value: String): Pointer = Pointer(nextPointer++).also { strings[it] = value }
        override fun deleteString(value: Pointer?) { if (value != null) check(strings.remove(value) != null) }
        override fun factory(name: Pointer?, iid: Iid): ToastCom {
            val nameString = string(name)
            events += "factory:$nameString"
            return obj(if (nameString.endsWith("Manager")) "manager" else "factory")
        }
        // RoActivateInstance only promises IInspectable, not the runtime class's default interface.
        override fun activate(name: Pointer?): ToastCom = obj(if (string(name).endsWith("XmlDocument")) "inspectable-xml" else "inspectable-data")
        private fun obj(kind: String): ToastCom = object : ToastCom {
            override val pointer = Pointer(nextPointer++)
            private var released = false
            override fun release() { check(!released); released = true; check(objects.remove(this)); events += "release:$kind" }
            override fun query(iid: Iid): ToastCom = obj(when {
                kind == "inspectable-xml" -> {
                    assertTrue(iid.bytes.contentEquals(Iid("f7f3a506-1e87-42d6-bcfb-b8c809fa5494").bytes))
                    "xml"
                }
                kind == "inspectable-data" -> {
                    assertTrue(iid.bytes.contentEquals(Iid("9ffd2312-9d6a-4aaf-b6ac-ff17f0c1f280").bytes))
                    "data"
                }
                iid.bytes.contentEquals(ToastAbi.XML_IO.bytes) -> "xmlio"
                iid.bytes.contentEquals(ToastAbi.TOAST_2.bytes) -> "toast2"
                iid.bytes.contentEquals(ToastAbi.TOAST_4.bytes) -> "toast4"
                iid.bytes.contentEquals(ToastAbi.NOTIFIER_2.bytes) -> "notifier2"
                else -> "manager2"
            })
            override fun get(slot: Int, vararg args: Any?): ToastCom {
                val event = when (kind) {
                    "manager" -> if (slot == 6) "CreateToastNotifier" else "CreateToastNotifierWithId:${string(args[0])}"
                    "manager2" -> "get_History"
                    "factory" -> "CreateToastNotification"
                    "data" -> "get_Values"
                    else -> error("unexpected get $kind/$slot")
                }
                events += event
                if (result(event.substringBefore(':')) < 0) throw net.bible.desktop.webview2.ComException(event, result(event.substringBefore(':')))
                return obj(when (kind) { "manager" -> "notifier"; "manager2" -> "history"; "factory" -> "toast"; "data" -> "map"; else -> error(kind) })
            }
            override fun call(slot: Int, vararg args: Any?): Int {
                val event = when (kind) {
                    "xmlio" -> { assertEquals(6, slot); "LoadXml:${string(args[0])}" }
                    "toast2" -> when (slot) { 6 -> "Tag:${string(args[0])}"; 8 -> "Group:${string(args[0])}"; else -> error("toast2 $slot") }
                    "toast4" -> { assertEquals(7, slot); "put_Data" }
                    "map" -> { assertEquals(10, slot); (args[2] as Pointer).setByte(0, 0); "Insert:${string(args[0])}:${string(args[1])}" }
                    "notifier" -> { assertEquals(6, slot); "Show" }
                    "notifier2" -> { assertEquals(6, slot); (args[3] as Pointer).setInt(0, updateResult); "Update:${string(args[1])}:${string(args[2])}" }
                    "history" -> when (slot) {
                        8 -> "RemoveGroupedTagWithId:${string(args[0])}:${string(args[1])}:${string(args[2])}"
                        9 -> "RemoveGroupedTag:${string(args[0])}:${string(args[1])}"
                        else -> error("history $slot")
                    }
                    else -> error("unexpected call $kind/$slot")
                }
                events += event
                return result(event.substringBefore(':'))
            }
        }.also { objects += it }

        fun objectCount() = objects.size
        fun activate(app: String = ANDBIBLE_AUMID, args: String = "andbible-toast:v1:am9i") {
            fun wide(s: String) = com.sun.jna.Memory((s.length + 1L) * 2).apply { s.forEachIndexed { i, c -> setShort(i * 2L, c.code.toShort()) }; setShort(s.length * 2L, 0) }
            activator!!.call(3, wide(app), wide(args), null, 0)
        }
        fun assertReleased() {
            assertTrue(objects.isEmpty(), "leaked ${objects.size} COM interfaces")
            assertTrue(strings.isEmpty(), "leaked HSTRINGs")
            assertEquals(null, activator, "leaked process activator")
        }
    }
}
