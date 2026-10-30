package net.bible.desktop.webview2

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** `S_OK`. */
const val S_OK = 0

/** `E_NOINTERFACE`. */
const val E_NOINTERFACE = 0x80004002.toInt()

/** An HRESULT as `0x%08X`. */
fun hresultString(hr: Int): String = "0x%08X".format(hr)

/** A COM interface ID: 16 bytes in memory layout, parsed from the registry form `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`. */
class Iid(text: String) {
    val bytes: ByteArray = parse(text)
    val memory: Memory = Memory(16).apply { write(0, bytes, 0, 16) }

    /** Whether the 16 bytes at [p] are this IID. */
    fun matches(p: Pointer?): Boolean = p != null && p.getByteArray(0, 16).contentEquals(bytes)

    companion object {
        fun parse(text: String): ByteArray {
            val hex = text.trim('{', '}').split('-')
            require(hex.size == 5 && hex[0].length == 8 && hex[1].length == 4 && hex[2].length == 4 && hex[3].length == 4 && hex[4].length == 12) { "bad IID $text" }
            val out = ByteArray(16)
            val d1 = hex[0].toLong(16)
            for (i in 0..3) out[i] = (d1 shr (8 * i)).toByte()
            val d2 = hex[1].toInt(16)
            out[4] = d2.toByte(); out[5] = (d2 shr 8).toByte()
            val d3 = hex[2].toInt(16)
            out[6] = d3.toByte(); out[7] = (d3 shr 8).toByte()
            val d4 = hex[3] + hex[4]
            for (i in 0..7) out[8 + i] = d4.substring(2 * i, 2 * i + 2).toInt(16).toByte()
            return out
        }

        val IUnknown = Iid("00000000-0000-0000-C000-000000000046")
    }
}

/** A raw COM interface pointer; calls go through its vtable ([index] counts IUnknown's three methods). */
class ComPtr(val ptr: Pointer) {
    private fun function(index: Int): Function = Function.getFunction(ptr.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE))

    /** Calls vtable slot [index] with `this` and [args]; returns the HRESULT. */
    fun call(index: Int, vararg args: Any?): Int = function(index).invokeInt(arrayOf<Any?>(ptr, *args))

    /** Calls slot [index] and throws [ComException] for a failed HRESULT. */
    fun check(index: Int, what: String, vararg args: Any?) {
        val hr = call(index, *args)
        if (hr < 0) throw ComException(what, hr)
    }

    /** Calls a getter that returns an interface pointer through its last argument; null when it fails or returns null. */
    fun getPtr(index: Int, vararg args: Any?): ComPtr? {
        val out = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, null) }
        val hr = call(index, *args, out)
        return if (hr < 0) null else out.getPointer(0)?.let(::ComPtr)
    }

    /** Calls a getter that returns a `CoTaskMemAlloc`ed wide string (freed here); null when it fails or returns null. */
    fun getString(index: Int): String? {
        val out = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, null) }
        if (call(index, out) < 0) return null
        val s = out.getPointer(0) ?: return null
        return try { s.getWideString(0) } finally { Ole32.INSTANCE.CoTaskMemFree(s) }
    }

    /** Calls a getter returning an `INT32`/`BOOL`/enum; null on failure. */
    fun getInt(index: Int): Int? {
        val out = Memory(4).apply { setInt(0, 0) }
        return if (call(index, out) < 0) null else out.getInt(0)
    }

    fun query(iid: Iid): ComPtr? = getPtr(0, iid.memory)

    fun addRef() { call(1) }

    fun release() { call(2) }

    companion object {
        fun wide(s: String?): WString? = s?.let(::WString)
    }
}

class ComException(what: String, val hresult: Int) : RuntimeException("$what failed: ${hresultString(hresult)}")

/** `HRESULT Method(This)` and `ULONG AddRef(This)`. */
private interface ComFn1 : Callback {
    fun invoke(self: Pointer?): Int
}

/** `HRESULT Method(This, a, b)`; integer arguments arrive in the low 32 bits of a register-sized value. */
private interface ComFn3 : Callback {
    fun invoke(self: Pointer?, a: Pointer?, b: Pointer?): Int
}

/**
 * A COM object implemented in Kotlin with one method after IUnknown: every WebView2 completed handler and event handler
 * (`Invoke(a, b)`). The object answers QueryInterface only for IUnknown and [iid] (never IMarshal, so WebView2 calls it on
 * the thread that registered it). It stays reachable from [live] until its reference count drops to zero; the creator owns
 * the first reference and releases it once WebView2 holds its own (see [use]).
 */
class ComCallback(private val iid: Iid, private val onInvoke: (a: Pointer?, b: Pointer?) -> Int) {
    private val refs = AtomicInteger(1)
    private val qi = object : ComFn3 {
        override fun invoke(self: Pointer?, a: Pointer?, b: Pointer?): Int {
            if (b == null) return E_NOINTERFACE
            if (Iid.IUnknown.matches(a) || iid.matches(a)) {
                refs.incrementAndGet()
                b.setPointer(0, self)
                return S_OK
            }
            b.setPointer(0, null)
            return E_NOINTERFACE
        }
    }
    private val addRef = object : ComFn1 {
        override fun invoke(self: Pointer?): Int = refs.incrementAndGet()
    }
    private val release = object : ComFn1 {
        override fun invoke(self: Pointer?): Int = refs.decrementAndGet().also { if (it == 0) live.remove(this@ComCallback) }
    }
    private val invokeFn = object : ComFn3 {
        override fun invoke(self: Pointer?, a: Pointer?, b: Pointer?): Int = try {
            onInvoke(a, b)
        } catch (t: Throwable) {
            System.err.println("webview2: callback failed: $t")
            S_OK
        }
    }
    private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
        listOf<Callback>(qi, addRef, release, invokeFn).forEachIndexed { i, fn ->
            setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(fn))
        }
    }

    /** The COM object: a pointer to [vtable]. */
    val pointer: Memory = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }

    init {
        live.add(this)
    }

    /** Passes the object to [block] (which hands it to WebView2), then releases the creator's reference. */
    fun <T> use(block: (Pointer) -> T): T = try { block(pointer) } finally { release.invoke(pointer) }

    companion object {
        private val live: MutableSet<ComCallback> = Collections.synchronizedSet(HashSet())

        /** Whether [callback] is still kept reachable (its reference count is above zero). */
        internal fun isLive(callback: ComCallback): Boolean = callback in live

        /** An HRESULT passed in a pointer-sized argument. */
        fun hresult(p: Pointer?): Int = Pointer.nativeValue(p).toInt()
    }
}
