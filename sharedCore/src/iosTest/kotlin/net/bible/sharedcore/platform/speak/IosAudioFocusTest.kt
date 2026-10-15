package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class IosAudioFocusTest {
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
        try {
            if (!focus.requestAudioFocus { oldEvents += it }) return
            if (!focus.requestAudioFocus { newEvents += it }) return
            center.postNotificationName(AVAudioSessionInterruptionNotification, session, mapOf(
                AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = AVAudioSessionInterruptionTypeBegan),
            ))
            assertEquals(emptyList(), oldEvents)
            assertEquals(listOf(false), newEvents)
        } finally { focus.abandonAudioFocus() }
    }
}
