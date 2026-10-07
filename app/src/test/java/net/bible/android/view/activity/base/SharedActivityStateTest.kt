package net.bible.android.view.activity.base

import org.junit.Test
import org.junit.Assert.assertEquals

class SharedActivityStateTest {
    @Test fun theFlagIsAlreadyFlippedWhenSubscribersRun() {
        val state = SharedActivityState()
        val seenFlag = mutableListOf<Boolean>()
        val seenPayload = mutableListOf<Boolean>()
        state.fullScreenChanged.subscribe { seenPayload += it; seenFlag += state.isFullScreen }
        state.toggleFullScreen()
        state.toggleFullScreen()
        assertEquals(listOf(true, false), seenPayload)
        assertEquals(listOf(true, false), seenFlag)
    }

    @Test fun resetDropsLeakedSubscribers() {
        val state = SharedActivityState()
        var calls = 0
        state.fullScreenChanged.subscribe { calls++ }
        state.resetSubscribersForTest()
        state.toggleFullScreen()
        assertEquals(0, calls)
    }
}
