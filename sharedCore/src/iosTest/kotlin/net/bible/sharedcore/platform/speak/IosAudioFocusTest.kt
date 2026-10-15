package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class IosAudioFocusTest {
    @Test fun backgroundNotificationCannotReachReplacementGeneration() {
        val center = NSNotificationCenter()
        val session = AVAudioSession.sharedInstance()
        val focus = IosAudioFocus({ session }, center)
        val oldEvents = mutableListOf<Boolean>()
        val newEvents = mutableListOf<Boolean>()
        val posted = platform.Foundation.NSCondition()
        var done = false
        try {
            kotlin.test.assertTrue(focus.requestAudioFocus {
                kotlin.test.assertTrue(platform.Foundation.NSThread.isMainThread)
                oldEvents += it
            })
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(0, 0u)) {
                center.postNotificationName(AVAudioSessionInterruptionNotification, session, mapOf(
                    AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = AVAudioSessionInterruptionTypeBegan),
                ))
                posted.lock(); done = true; posted.signal(); posted.unlock()
            }
            posted.lock()
            while (!done) posted.wait()
            posted.unlock()
            focus.abandonAudioFocus()
            kotlin.test.assertTrue(focus.requestAudioFocus { newEvents += it })
            platform.Foundation.NSRunLoop.mainRunLoop.runUntilDate(
                platform.Foundation.NSDate.dateWithTimeIntervalSinceNow(0.1))
            assertEquals(emptyList(), oldEvents)
            assertEquals(emptyList(), newEvents)
            center.postNotificationName(AVAudioSessionRouteChangeNotification, session, mapOf(
                AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable),
            ))
            assertEquals(listOf(false), newEvents)
        } finally { focus.abandonAudioFocus() }
    }

    @Test fun notificationsTranslateFocusAndAbandonRemovesObservers() {
        val center = NSNotificationCenter()
        val session = AVAudioSession.sharedInstance()
        val focus = IosAudioFocus({ session }, center)
        val events = mutableListOf<Boolean>()
        fun interruption(type: ULong, options: ULong? = null) {
            val info = mutableMapOf<Any?, Any?>(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = type))
            options?.let { info[AVAudioSessionInterruptionOptionKey] = NSNumber(unsignedLong = it) }
            center.postNotificationName(AVAudioSessionInterruptionNotification, session, info)
        }
        try {
            val granted = focus.requestAudioFocus { events += it }
            if (!granted) {
                interruption(AVAudioSessionInterruptionTypeBegan)
                interruption(AVAudioSessionInterruptionTypeEnded, AVAudioSessionInterruptionOptionShouldResume)
                assertEquals(emptyList(), events, "Refused setup must not install observers")
                println("IosAudioFocusTest: audio setup refused; observer absence checked")
                return
            }
            interruption(AVAudioSessionInterruptionTypeBegan)
            assertEquals(listOf(false), events)
            interruption(AVAudioSessionInterruptionTypeEnded, AVAudioSessionInterruptionOptionShouldResume)
            assertEquals(listOf(false, true), events)
            interruption(AVAudioSessionInterruptionTypeEnded)
            interruption(AVAudioSessionInterruptionTypeEnded, 0uL)
            assertEquals(listOf(false, true), events, "No implicit resume without ShouldResume")
            center.postNotificationName(AVAudioSessionRouteChangeNotification, session, mapOf(
                AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable),
            ))
            assertEquals(listOf(false, true, false), events)
            center.postNotificationName(AVAudioSessionRouteChangeNotification, session, mapOf(
                AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonNewDeviceAvailable),
            ))
            center.postNotificationName(AVAudioSessionInterruptionNotification, session, null)
            assertEquals(listOf(false, true, false), events, "Other routes and absent data are not focus changes")
            focus.abandonAudioFocus()
            interruption(AVAudioSessionInterruptionTypeBegan)
            center.postNotificationName(AVAudioSessionRouteChangeNotification, session, mapOf(
                AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable),
            ))
            assertEquals(listOf(false, true, false), events)
            if (focus.requestAudioFocus { events += it }) {
                interruption(AVAudioSessionInterruptionTypeBegan)
                assertEquals(listOf(false, true, false, false), events, "Abandoned focus can be requested again")
            }
        } finally { focus.abandonAudioFocus() }
    }

    @Test fun repeatedRequestReplacesCallbackWithoutDuplicateObservers() {
        val center = NSNotificationCenter()
        val session = AVAudioSession.sharedInstance()
        val focus = IosAudioFocus({ session }, center)
        val oldEvents = mutableListOf<Boolean>()
        val newEvents = mutableListOf<Boolean>()
        fun assertNoCallbacksAfterRefusal(request: String) {
            center.postNotificationName(AVAudioSessionInterruptionNotification, session, mapOf(
                AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = AVAudioSessionInterruptionTypeBegan),
            ))
            center.postNotificationName(AVAudioSessionInterruptionNotification, session, mapOf(
                AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = AVAudioSessionInterruptionTypeEnded),
                AVAudioSessionInterruptionOptionKey to NSNumber(unsignedLong = AVAudioSessionInterruptionOptionShouldResume),
            ))
            center.postNotificationName(AVAudioSessionRouteChangeNotification, session, mapOf(
                AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable),
            ))
            assertEquals(emptyList(), oldEvents, "$request refusal must remove the original callback and observers")
            assertEquals(emptyList(), newEvents, "$request refusal must not retain the replacement callback")
            println("IosAudioFocusTest: $request request refused; both callbacks and observer absence checked")
        }
        try {
            if (!focus.requestAudioFocus { oldEvents += it }) {
                assertNoCallbacksAfterRefusal("First")
                return
            }
            if (!focus.requestAudioFocus { newEvents += it }) {
                assertNoCallbacksAfterRefusal("Second")
                return
            }
            center.postNotificationName(AVAudioSessionInterruptionNotification, session, mapOf(
                AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = AVAudioSessionInterruptionTypeBegan),
            ))
            assertEquals(emptyList(), oldEvents)
            assertEquals(listOf(false), newEvents)
        } finally { focus.abandonAudioFocus() }
    }
}
