package net.bible.test

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Diagnostic only: prints every uncaught coroutine exception with the test class/method that was on
 * a thread when it was delivered. Registered through META-INF/services (kotlinx-coroutines' global
 * handler list, the same list `runTest` uses to raise UncaughtExceptionsBeforeTest), so a leaked
 * exception that later fails an innocent `runTest` is attributed in the XML system-err of the class
 * that was running: grep `LEAKPROBE`. Does not swallow anything.
 */
class LeakProbe : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        val where = Thread.getAllStackTraces().flatMap { (t, st) ->
            st.filter { it.className.startsWith("net.bible") && it.className.endsWith("Test") }.take(1).map { "${t.name}: ${it.className}.${it.methodName}" }
        }
        System.err.println("LEAKPROBE exc=" + exception.javaClass.simpleName + ": " + exception.message?.take(90) + " thread=" + Thread.currentThread().name + " running=" + where + " at=" + exception.stackTrace.filter { it.className.startsWith("net.bible") }.take(4))
    }
}
