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
    fun showUsesHstringsMapTagGroupAndTypedActivationThenUnsubscribesOnReplacement() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi("org.andbible.AndBible") { runtime }
        val clicked = mutableListOf<String>()
        api.onActivated { clicked += it }
        assertEquals(0, api.show("<toast/>", "job", "andbible", mapOf("progressValue" to "0.42")))
        assertEquals(listOf("initialize", "factory:Windows.UI.Notifications.ToastNotificationManager"), runtime.events.take(2))
        assertTrue(runtime.events.contains("CreateToastNotifierWithId:org.andbible.AndBible"))
        assertTrue(runtime.events.contains("LoadXml:<toast/>"))
        assertTrue(runtime.events.contains("Tag:job"))
        assertTrue(runtime.events.contains("Group:andbible"))
        assertTrue(runtime.events.contains("Insert:progressValue:0.42"))
        assertTrue(runtime.events.indexOf("put_Data") < runtime.events.indexOf("Show"))
        runtime.activate()
        assertEquals(listOf("job"), clicked)
        assertEquals(0, api.show("<toast/>", "job", "andbible", emptyMap()))
        assertEquals(1, runtime.events.count { it == "remove_Activated:91" })
        api.close()
        assertEquals(2, runtime.events.count { it.startsWith("remove_Activated:") })
        assertEquals("uninitialize", runtime.events.last())
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
        val oldCallback = runtime.callbackPointers().single()
        runtime.fail = "Show"
        assertTrue(api.show("<toast/>", "job", "andbible", emptyMap()) < 0)
        assertEquals(listOf(oldCallback), runtime.callbackPointers(), "failed Show removed the prior toast's callback")
        runtime.activate()
        assertEquals(listOf("job"), clicked)
        runtime.fail = null
        api.close()
        runtime.assertReleased()
    }

    @Test
    fun failingInitializationAndHstringInsertionBalanceOwnership() {
        for (failure in listOf("initialize", "CreateToastNotifier", "LoadXml", "Insert", "add_Activated", "put_Data")) {
            val runtime = RecordingWinRt().apply { fail = failure }
            val api = WinRtToastApi(null) { runtime }
            assertTrue(api.show("<toast/>", "job", "andbible", mapOf("key" to "value")) < 0, failure)
            api.close()
            runtime.assertReleased()
            assertEquals(if (failure == "initialize") 0 else 1, runtime.events.count { it == "uninitialize" })
        }
    }

    @Test
    fun cleanupContinuesAfterUnsubscribeFailure() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        api.show("<toast/>", "one", "andbible", emptyMap())
        api.show("<toast/>", "two", "andbible", emptyMap())
        val failures = mutableListOf<Pair<String, Int>>()
        api.onCleanupFailure { operation, hr -> failures += operation to hr }
        runtime.fail = "remove_Activated"
        api.close()
        assertEquals(listOf("remove_Activated" to 0x80004005.toInt()), failures)
        assertEquals(2, runtime.events.count { it.startsWith("remove_Activated:") })
        assertEquals("uninitialize", runtime.events.last())
        runtime.assertReleased()
    }

    @Test
    fun cleanupFailureDoesNotRemoveSuccessfullyShownFinalToastAtNotifierClose() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        val logs = mutableListOf<String>()
        val notifier = WindowsToastNotifier({ api }, { it.run() }, log = { logs += it })
        notifier.show(NativeNotification("job", "Title", "Working", 1, false, false))
        runtime.fail = "remove_Activated"
        notifier.show(NativeNotification("job", "Title", "Finished", null, false, false))
        notifier.close()
        assertFalse(runtime.events.any { it.startsWith("RemoveGroupedTag") }, "final toast was mistaken for progress")
        assertEquals(2, runtime.events.count { it.startsWith("Show") })
        assertEquals(1, logs.count { "cleanup" in it && "0x80004005" in it })
        assertFalse(logs.any { "show failed" in it })
        runtime.assertReleased()
    }

    @Test
    fun cleanupFailureAfterNotFoundDoesNotCauseEndlessProgressUpdates() {
        val runtime = RecordingWinRt()
        val api = WinRtToastApi(null) { runtime }
        val logs = mutableListOf<String>()
        val notifier = WindowsToastNotifier({ api }, { it.run() }, log = { logs += it })
        val n = NativeNotification("job", "Title", "Working", 1, false, false)
        notifier.show(n)
        runtime.updateResult = 2
        runtime.fail = "remove_Activated"
        repeat(4) { notifier.show(n.copy(progress = it + 2)) }
        assertEquals(1, runtime.events.count { it.startsWith("Update:") })
        assertEquals(1, logs.count { "cleanup" in it && "0x80004005" in it })
        assertFalse(logs.any { "update failed" in it })
        runtime.fail = null
        notifier.close()
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
        private val callbacks = linkedMapOf<Pair<Pointer, Long>, ComPtr>()
        private var nextToken = 91L
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
            override fun release() { check(!released); released = true; check(objects.remove(this)) }
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
                    "toast" -> when (slot) {
                        11 -> {
                            val callback = ComPtr(args[0] as Pointer)
                            if (result("add_Activated") >= 0) {
                                val typed = callback.query(ToastAbi.ACTIVATED)!!
                                typed.release()
                                callback.addRef()
                                val token = nextToken++
                                callbacks[pointer to token] = callback
                                (args[1] as Pointer).setLong(0, token)
                            }
                            "add_Activated"
                        }
                        12 -> {
                            val callback = checkNotNull(callbacks.remove(pointer to (args[0] as Long))) { "wrong toast/token unsubscription" }
                            assertEquals(0, callback.call(2), "callback creator reference leaked")
                            "remove_Activated:${args[0]}"
                        }
                        else -> error("toast $slot")
                    }
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

        fun callbackPointers(): List<Pointer> = callbacks.values.map { it.ptr }
        fun activate() { callbacks.values.last().call(3, null, null) }
        fun assertReleased() {
            assertTrue(objects.isEmpty(), "leaked ${objects.size} COM interfaces")
            assertTrue(strings.isEmpty(), "leaked HSTRINGs")
            assertTrue(callbacks.isEmpty(), "leaked callback subscriptions")
        }
    }
}
