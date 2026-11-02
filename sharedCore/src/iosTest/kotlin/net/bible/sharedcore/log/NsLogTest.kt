package net.bible.sharedcore.log

import kotlin.test.Test

/**
 * NSLog is variadic: a Kotlin String passed for `%@` is not an Objective-C object there, and the process
 * dies with SIGSEGV inside NSLog (the I0 app's crash reports, and-bible CI run 37896455570). These calls only
 * have to return; a crash fails the whole iOS test task.
 */
class NsLogTest {
    @Test fun nsLogDoesNotCrash() {
        nsLog("bridge[w1]: äö 100% %@ %s")
    }

    @Test fun platformLogDoesNotCrash() {
        Log.e("NsLogTest", "äö %@", IllegalStateException("boom"))
    }
}

/**
 * macOS 26 shows an iOS app's NSLog text as `<private>` in Console and `log show`, so the Mac diagnostics
 * (WINDOW-CONTROLS, SHEET-GEOMETRY, ...) were unreadable. The Swift host installs a sink that logs publicly;
 * [nsLog] must hand every line to it, verbatim, instead of NSLog.
 */
class NsLogSinkTest {
    @Test fun nsLogGoesToTheInstalledSinkVerbatim() {
        val seen = mutableListOf<String>()
        val previous = IosLogSink.install { seen += it }
        try {
            nsLog("SHEET-GEOMETRY gapPx=12 äö 100% %@")
            kotlin.test.assertEquals(listOf("SHEET-GEOMETRY gapPx=12 äö 100% %@"), seen)
        } finally {
            IosLogSink.install(previous)
        }
    }

    @Test fun withoutASinkNsLogStillReturns() {
        val previous = IosLogSink.install(null)
        try { nsLog("no sink") } finally { IosLogSink.install(previous) }
    }
}
