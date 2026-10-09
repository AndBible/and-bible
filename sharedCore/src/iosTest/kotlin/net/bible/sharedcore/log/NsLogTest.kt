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
