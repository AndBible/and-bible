package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.*

class SpeakControllersTest {
    private class FakeService : SpeakSettingsService {
        val _pb = MutableStateFlow(SpeakPlaybackVd(100, true, true, false, 0, null))
        val _adv = MutableStateFlow(AdvancedSpeakVd(true, false, false, false))
        override val playback: StateFlow<SpeakPlaybackVd> = _pb.asStateFlow()
        override val advanced: StateFlow<AdvancedSpeakVd> = _adv.asStateFlow()
        var speed: Int? = null; var chapter: Boolean? = null; var titles: Boolean? = null
        var footnotes: Boolean? = null; var clearedRepeat = 0
        var sync: Boolean? = null; var divine: Boolean? = null; var autoBm: Boolean? = null; var restore: Boolean? = null
        override fun setSpeed(percent: Int) { speed = percent }
        override fun setSpeakChapterChanges(on: Boolean) { chapter = on }
        override fun setSpeakTitles(on: Boolean) { titles = on }
        override fun setSpeakFootnotes(on: Boolean) { footnotes = on }
        override fun clearRepeatRange() { clearedRepeat++ }
        override fun setSynchronize(on: Boolean) { sync = on }
        override fun setReplaceDivineName(on: Boolean) { divine = on }
        override fun setAutoBookmark(on: Boolean) { autoBm = on }
        override fun setRestoreSettingsFromBookmarks(on: Boolean) { restore = on }
    }

    @Test fun playback_setters_pass_through() {
        val fake = FakeService()
        val c = BibleSpeakSettingsController(fake, onSleepTimerToggle = {}, onChooseRepeatRange = {})
        c.setSpeed(175); c.setSpeakChapterChanges(false); c.setSpeakTitles(false); c.setSpeakFootnotes(true)
        assertEquals(175, fake.speed)
        assertEquals(false, fake.chapter); assertEquals(false, fake.titles); assertEquals(true, fake.footnotes)
        assertSame(fake.playback, c.playback)
    }

    @Test fun sleep_timer_toggle_forwards_to_host() {
        val fake = FakeService(); var toggled: Boolean? = null
        val c = BibleSpeakSettingsController(fake, onSleepTimerToggle = { toggled = it }, onChooseRepeatRange = {})
        c.setSleepTimerEnabled(true); assertEquals(true, toggled)
        c.setSleepTimerEnabled(false); assertEquals(false, toggled)
    }

    @Test fun toggle_repeat_range_clears_when_set_else_chooses() {
        val fake = FakeService(); var chose = 0
        val c = BibleSpeakSettingsController(fake, onSleepTimerToggle = {}, onChooseRepeatRange = { chose++ })
        // No range set -> host chooses, service NOT cleared.
        c.toggleRepeatRange(); assertEquals(1, chose); assertEquals(0, fake.clearedRepeat)
        // Range set -> service cleared, host NOT invoked.
        fake._pb.value = fake._pb.value.copy(repeatRangeName = "Gen 1:1-5")
        c.toggleRepeatRange(); assertEquals(1, chose); assertEquals(1, fake.clearedRepeat)
    }

    @Test fun advanced_setters_pass_through() {
        val fake = FakeService()
        val c = AdvancedSpeakSettingsController(fake)
        c.setSynchronize(false); c.setReplaceDivineName(true); c.setAutoBookmark(true); c.setRestoreSettingsFromBookmarks(true)
        assertEquals(false, fake.sync); assertEquals(true, fake.divine); assertEquals(true, fake.autoBm); assertEquals(true, fake.restore)
        assertSame(fake.advanced, c.advanced)
    }
}
