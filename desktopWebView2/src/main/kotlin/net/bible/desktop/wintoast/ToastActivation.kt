package net.bible.desktop.wintoast

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import net.bible.desktop.webview2.Iid
import java.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

const val TOAST_ACTIVATOR_CLSID = "C8B03B6B-3F3A-4F8E-9A57-24A610C03A13"
// Packaged COM is visible outside the package; sharing the MSI class would shadow its running server.
const val MSIX_TOAST_ACTIVATOR_CLSID = "AD705591-F554-40C0-9EE2-0DE1C1E5C695"
private const val PREFIX = "andbible-toast:v1:"
private const val MAX_ARGUMENTS = 8192

fun toastActivationArguments(id: String): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
fun parseToastActivationArguments(args: String): String? {
    if (!args.startsWith(PREFIX) || args.length > MAX_ARGUMENTS) return null
    val encoded = args.removePrefix(PREFIX)
    if (encoded.isEmpty() || !encoded.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }) return null
    return runCatching {
        val id = Base64.getUrlDecoder().decode(encoded).toString(Charsets.UTF_8)
        id.takeIf { it.isNotEmpty() && '\u0000' !in it && toastActivationArguments(it) == args }
    }.getOrNull()
}

/** Start the normal, single-instance app for a COM activation, but never import Shell switches as files. */
fun toastLaunchFiles(args: List<String>): List<String> = if (args.any {
    it.equals("-Embedding", true) || it.equals("/Embedding", true) || it.equals("-ToastActivated", true)
}) emptyList() else args

private interface RefFn : Callback { fun invoke(self: Pointer?): Int }
private interface QueryFn : Callback { fun invoke(self: Pointer?, iid: Pointer?, out: Pointer?): Int }
private interface CreateFn : Callback { fun invoke(self: Pointer?, outer: Pointer?, iid: Pointer?, out: Pointer?): Int }
private interface LockFn : Callback { fun invoke(self: Pointer?, lock: Int): Int }
private interface ActivateFn : Callback { fun invoke(self: Pointer?, app: Pointer?, args: Pointer?, data: Pointer?, count: Int): Int }

/** Classic COM objects are kept reachable until COM releases its last reference, including after revoke. */
private class ClassicObject(val iid: Iid, methods: List<Callback>) {
    private val refs = AtomicInteger(1)
    val query: QueryFn = object : QueryFn {
        override fun invoke(self: Pointer?, iid: Pointer?, out: Pointer?): Int {
            if (out == null) return 0x80004003.toInt()
            out.setPointer(0, null)
            if (!this@ClassicObject.iid.matches(iid) && !Iid.IUnknown.matches(iid)) return 0x80004002.toInt()
            refs.incrementAndGet(); out.setPointer(0, pointer); return 0
        }
    }
    private val add = object : RefFn { override fun invoke(self: Pointer?) = refs.incrementAndGet() }
    private val release = object : RefFn { override fun invoke(self: Pointer?) = refs.decrementAndGet().also { if (it == 0) live.remove(this@ClassicObject) } }
    private val callbacks = listOf<Callback>(query, add, release) + methods
    private val table = Memory(callbacks.size.toLong() * Native.POINTER_SIZE).apply {
        callbacks.forEachIndexed { i, c -> setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(c)) }
    }
    val pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, table) }
    init { live.add(this) }
    fun close() { release.invoke(pointer) }
    val isLive get() = this in live
    companion object { val live = Collections.synchronizedSet(mutableSetOf<ClassicObject>()) }
}

/** NotificationActivationCallback.h + Unknwn.h: IUnknown/Activate and IUnknown/CreateInstance/LockServer. */
internal class ToastComServer(private var handler: (String, String) -> Unit) : AutoCloseable {
    @Volatile private var closed = false
    private val activation = ClassicObject(Iid("53E31837-6600-4A81-9395-75CFFE746F94"), listOf(object : ActivateFn {
        override fun invoke(self: Pointer?, app: Pointer?, args: Pointer?, data: Pointer?, count: Int): Int {
            if (!closed) runCatching { handler(readUtf16(app, 1024), readUtf16(args, MAX_ARGUMENTS)) }
            return 0
        }
    }))
    private val factory = ClassicObject(Iid("00000001-0000-0000-C000-000000000046"), listOf(
        object : CreateFn {
            override fun invoke(self: Pointer?, outer: Pointer?, iid: Pointer?, out: Pointer?): Int {
                out?.setPointer(0, null)
                if (outer != null) return 0x80040110.toInt() // CLASS_E_NOAGGREGATION
                if (closed) return 0x80040111.toInt()
                return activation.query.invoke(activation.pointer, iid, out)
            }
        },
        object : LockFn { override fun invoke(self: Pointer?, lock: Int): Int = 0 },
    ))
    val factoryPointer: Pointer get() = factory.pointer
    val isLive: Boolean get() = factory.isLive || activation.isLive
    override fun close() {
        if (closed) return
        closed = true; handler = { _, _ -> }
        factory.close(); activation.close()
    }
    companion object {
        private fun readUtf16(pointer: Pointer?, limit: Int): String {
            if (pointer == null) return ""
            return buildString {
                for (i in 0..limit) {
                    val c = pointer.getShort(i * 2L).toInt().and(0xffff).toChar()
                    if (c == '\u0000') return@buildString
                    require(i < limit) { "oversized COM activation argument" }
                    append(c)
                }
            }
        }
    }
}
