package net.bible.desktop.wintoast

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import net.bible.desktop.webview2.ComException
import net.bible.desktop.webview2.ComPtr
import net.bible.desktop.webview2.Iid

/** SDK ABI declarations. Slots include IUnknown (0..2) and IInspectable (3..5). */
internal object ToastAbi {
    // windows.ui.notifications.h
    val MANAGER = Iid("50ac103f-d235-4598-bbef-98fe4d1a3ad4")
    val MANAGER_2 = Iid("7ab93c52-0e48-4750-ba9d-1a4113981847")
    val TOAST_FACTORY = Iid("04124b20-82c6-4229-b109-fd9ed4662b53")
    val TOAST_2 = Iid("9dfb9fd1-143a-490e-90bf-b9fba7132de7")
    val TOAST_4 = Iid("15154935-28ea-4727-88e9-c58680e2d118")
    val NOTIFIER_2 = Iid("354389c6-7c01-4bd5-9c20-604340cd2b74")
    val NOTIFICATION_DATA = Iid("9ffd2312-9d6a-4aaf-b6ac-ff17f0c1f280")
    const val CREATE_NOTIFIER = 6
    const val CREATE_NOTIFIER_WITH_ID = 7
    const val GET_HISTORY = 6
    const val CREATE_TOAST = 6
    const val PUT_TAG = 6
    const val PUT_GROUP = 8
    const val PUT_DATA = 7
    const val GET_VALUES = 6
    const val SHOW = 6
    const val UPDATE_WITH_TAG_AND_GROUP = 6
    const val REMOVE_GROUPED_TAG_WITH_ID = 8
    const val REMOVE_GROUPED_TAG = 9
    // windows.data.xml.dom.h
    val XML_DOCUMENT = Iid("f7f3a506-1e87-42d6-bcfb-b8c809fa5494")
    val XML_IO = Iid("6cd0e74e-ee65-4489-9ebf-ca43e87ba637")
    const val LOAD_XML = 6
    // windows.foundation.collections.h: IMap_impl<HSTRING,HSTRING> (Lookup, Size, HasKey, GetView, Insert).
    const val MAP_INSERT = 10
    const val MANAGER_CLASS = "Windows.UI.Notifications.ToastNotificationManager"
    const val TOAST_CLASS = "Windows.UI.Notifications.ToastNotification"
    const val DATA_CLASS = "Windows.UI.Notifications.NotificationData"
    const val XML_CLASS = "Windows.Data.Xml.Dom.XmlDocument"
    const val E_FAIL = 0x80004005.toInt()
}

/** Small ABI boundary lets JVM tests exercise ownership, HRESULTs and argument layouts without loading combase. */
internal interface ToastCom {
    val pointer: Pointer
    fun call(slot: Int, vararg args: Any?): Int
    fun get(slot: Int, vararg args: Any?): ToastCom
    fun query(iid: Iid): ToastCom
    fun release()
}

internal interface WinRtCalls {
    fun initialize(): Int
    fun uninitialize()
    fun createString(value: String): Pointer?
    fun deleteString(value: Pointer?)
    fun factory(name: Pointer?, iid: Iid): ToastCom
    fun activate(name: Pointer?): ToastCom
    fun registerActivator(factory: Pointer): Int
    fun revokeActivator(cookie: Int)
}

private interface Combase : StdCallLibrary {
    fun RoInitialize(type: Int): Int
    fun RoUninitialize()
    fun WindowsCreateString(source: WString, length: Int, out: PointerByReference): Int
    fun WindowsDeleteString(value: Pointer?): Int
    fun RoGetActivationFactory(name: Pointer?, iid: Pointer, out: PointerByReference): Int
    fun RoActivateInstance(name: Pointer?, out: PointerByReference): Int
}

private fun checked(hr: Int, operation: String) {
    if (hr < 0) throw ComException(operation, hr)
}

private class NativeToastCom(override val pointer: Pointer) : ToastCom {
    private val com = ComPtr(pointer)
    override fun call(slot: Int, vararg args: Any?): Int = com.call(slot, *args)
    override fun get(slot: Int, vararg args: Any?): ToastCom {
        val out = PointerByReference()
        checked(call(slot, *args, out), "WinRT slot $slot")
        return NativeToastCom(out.value ?: throw ComException("WinRT null interface", ToastAbi.E_FAIL))
    }
    override fun query(iid: Iid): ToastCom = get(0, iid.memory)
    override fun release() = com.release()
}

private interface ToastOle : StdCallLibrary {
    fun CoRegisterClassObject(clsid: Pointer, factory: Pointer, context: Int, flags: Int, cookie: com.sun.jna.ptr.IntByReference): Int
    fun CoRevokeClassObject(cookie: Int): Int
}

private class NativeWinRtCalls : WinRtCalls {
    private val ole by lazy { Native.load("ole32", ToastOle::class.java) }
    override fun registerActivator(factory: Pointer): Int {
        val cookie = com.sun.jna.ptr.IntByReference()
        // CLSCTX_LOCAL_SERVER, REGCLS_MULTIPLEUSE. The server is the existing running JVM, not a DLL.
        checked(ole.CoRegisterClassObject(Iid(TOAST_ACTIVATOR_CLSID).memory, factory, 4, 1, cookie), "CoRegisterClassObject")
        return cookie.value
    }
    override fun revokeActivator(cookie: Int) { checked(ole.CoRevokeClassObject(cookie), "CoRevokeClassObject") }
    private val library by lazy { Native.load("combase", Combase::class.java) }
    override fun initialize(): Int = library.RoInitialize(0) // RO_INIT_SINGLETHREADED, compatible with the existing pump.
    override fun uninitialize() = library.RoUninitialize()
    override fun createString(value: String): Pointer? {
        val out = PointerByReference()
        checked(library.WindowsCreateString(WString(value), value.length, out), "WindowsCreateString")
        return out.value // The empty HSTRING may legally be null.
    }
    override fun deleteString(value: Pointer?) { checked(library.WindowsDeleteString(value), "WindowsDeleteString") }
    override fun factory(name: Pointer?, iid: Iid): ToastCom {
        val out = PointerByReference()
        checked(library.RoGetActivationFactory(name, iid.memory, out), "RoGetActivationFactory")
        return NativeToastCom(out.value ?: throw ComException("RoGetActivationFactory null", ToastAbi.E_FAIL))
    }
    override fun activate(name: Pointer?): ToastCom {
        val out = PointerByReference()
        checked(library.RoActivateInstance(name, out), "RoActivateInstance")
        return NativeToastCom(out.value ?: throw ComException("RoActivateInstance null", ToastAbi.E_FAIL))
    }
}

/**
 * WinRT toasts owned entirely by the caller's pumped STA. Construction/unused close never load Windows libraries.
 * One registered classic COM activator handles popup and Action Center clicks. Toast interfaces are released after Show.
 */
class WinRtToastApi internal constructor(
    private val aumid: String?,
    private val runtime: () -> WinRtCalls,
) : ToastApi {
    constructor(aumid: String?) : this(aumid, { NativeWinRtCalls() })

    private var owner: Thread? = null
    private var closed = false
    private var state: State? = null
    private var handler: (String) -> Unit = {}

    private class State(val calls: WinRtCalls) {
        val roots = mutableListOf<ToastCom>()
        lateinit var notifier: ToastCom
        lateinit var updates: ToastCom
        lateinit var history: ToastCom
        lateinit var factory: ToastCom
        var server: ToastComServer? = null
        var cookie: Int? = null
        fun own(com: ToastCom): ToastCom = com.also { roots += it }
        fun close() {
            try {
                cleanup(listOf<() -> Unit>({ cookie?.let(calls::revokeActivator) }, { server?.close() }) + roots.asReversed().map { com -> { com.release() } })
            } finally { roots.clear(); server = null; cookie = null; calls.uninitialize() }
        }
    }

    private fun checkOwner() {
        check(!closed) { "WinRtToastApi is closed" }
        val current = Thread.currentThread()
        if (owner == null) owner = current
        check(owner === current) { "WinRtToastApi must be called on its COM owner thread" }
    }

    private fun getState(): State {
        state?.let { return it }
        val calls = runtime()
        checked(calls.initialize(), "RoInitialize") // S_FALSE also needs a matching RoUninitialize.
        val result = State(calls)
        try {
            result.server = ToastComServer { appId, args ->
                if (!closed && (aumid == null || appId == aumid) && parseToastActivationArguments(args) != null) handler(args)
            }
            result.cookie = calls.registerActivator(result.server!!.factoryPointer)
            hstring(calls, ToastAbi.MANAGER_CLASS) { className ->
                use(calls.factory(className, ToastAbi.MANAGER)) { manager ->
                    result.notifier = result.own(if (aumid == null) manager.get(ToastAbi.CREATE_NOTIFIER)
                    else hstring(calls, aumid) { manager.get(ToastAbi.CREATE_NOTIFIER_WITH_ID, it) })
                    result.updates = result.own(result.notifier.query(ToastAbi.NOTIFIER_2))
                    use(manager.query(ToastAbi.MANAGER_2)) { historyFactory ->
                        result.history = result.own(historyFactory.get(ToastAbi.GET_HISTORY))
                    }
                }
            }
            result.factory = result.own(hstring(calls, ToastAbi.TOAST_CLASS) { calls.factory(it, ToastAbi.TOAST_FACTORY) })
            state = result
            return result
        } catch (e: Throwable) {
            runCatching { result.close() }.exceptionOrNull()?.let { e.addSuppressed(it) }
            throw e
        }
    }

    private fun operation(action: (State) -> Int): Int {
        checkOwner()
        return try { action(getState()) } catch (e: ComException) { e.hresult }
    }

    override fun onActivated(handler: (tag: String) -> Unit) {
        checkOwner()
        this.handler = handler
    }

    override fun show(xml: String, tag: String, group: String, data: Map<String, String>): Int = operation { s ->
        val toast = use(hstring(s.calls, ToastAbi.XML_CLASS) { s.calls.activate(it) }) { instance ->
            use(instance.query(ToastAbi.XML_DOCUMENT)) { document ->
                use(document.query(ToastAbi.XML_IO)) { io ->
                    hstring(s.calls, xml) { checked(io.call(ToastAbi.LOAD_XML, it), "LoadXml") }
                }
                s.factory.get(ToastAbi.CREATE_TOAST, document.pointer)
            }
        }
        use(toast) {
            use(toast.query(ToastAbi.TOAST_2)) { options ->
                hstring(s.calls, tag) { checked(options.call(ToastAbi.PUT_TAG, it), "put_Tag") }
                hstring(s.calls, group) { checked(options.call(ToastAbi.PUT_GROUP, it), "put_Group") }
            }
            if (data.isNotEmpty()) use(makeData(s, data)) { notificationData ->
                use(toast.query(ToastAbi.TOAST_4)) { options ->
                    checked(options.call(ToastAbi.PUT_DATA, notificationData.pointer), "put_Data")
                }
            }
            checked(s.notifier.call(ToastAbi.SHOW, toast.pointer), "Show")
            0
        }
    }

    private fun makeData(s: State, values: Map<String, String>): ToastCom {
        val data = use(hstring(s.calls, ToastAbi.DATA_CLASS) { s.calls.activate(it) }) { instance ->
            instance.query(ToastAbi.NOTIFICATION_DATA)
        }
        try {
            use(data.get(ToastAbi.GET_VALUES)) { map ->
                values.forEach { (key, value) ->
                    hstring(s.calls, key) { k -> hstring(s.calls, value) { v ->
                        val replaced = Memory(1) // WinRT boolean is one byte, not Win32 BOOL.
                        checked(map.call(ToastAbi.MAP_INSERT, k, v, replaced), "IMap.Insert")
                    } }
                }
            }
            return data
        } catch (e: Throwable) {
            data.release()
            throw e
        }
    }

    override fun update(tag: String, group: String, data: Map<String, String>): Int = operation { s ->
        use(makeData(s, data)) { notificationData ->
            hstring(s.calls, tag) { t -> hstring(s.calls, group) { g ->
                val result = Memory(4)
                checked(s.updates.call(ToastAbi.UPDATE_WITH_TAG_AND_GROUP, notificationData.pointer, t, g, result), "UpdateWithTagAndGroup")
                when (result.getInt(0)) {
                    0 -> 0 // NotificationUpdateResult_Succeeded
                    2 -> 1 // NotificationNotFound -> S_FALSE, no resurrection.
                    else -> ToastAbi.E_FAIL
                }
            } }
        }
    }

    override fun remove(tag: String, group: String): Int = operation { s ->
        hstring(s.calls, tag) { t -> hstring(s.calls, group) { g ->
                if (aumid == null) s.history.call(ToastAbi.REMOVE_GROUPED_TAG, t, g)
                else hstring(s.calls, aumid) { s.history.call(ToastAbi.REMOVE_GROUPED_TAG_WITH_ID, t, g, it) }
        } }
    }

    override fun close() {
        if (closed) return
        if (owner != null) checkOwner()
        closed = true
        handler = {}
        try {
            state?.close()
        } finally { state = null }
    }

    companion object {
        private inline fun <T> use(com: ToastCom, block: (ToastCom) -> T): T = try { block(com) } finally { com.release() }
        private inline fun <T> hstring(calls: WinRtCalls, value: String, block: (Pointer?) -> T): T {
            val string = calls.createString(value)
            return try { block(string) } finally { calls.deleteString(string) }
        }
        /** Revocation failure must not prevent release of the class server/roots or RoUninitialize. */
        private fun cleanup(actions: List<() -> Unit>) {
            var failure: Throwable? = null
            actions.forEach { action ->
                try { action() } catch (e: Throwable) {
                    if (failure == null) failure = e else failure.addSuppressed(e)
                }
            }
            failure?.let { throw it }
        }
    }
}
