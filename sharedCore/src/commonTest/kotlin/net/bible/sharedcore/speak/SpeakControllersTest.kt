package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.*

class SpeakControllersTest {
    private class FakeSpeakSettingsService : SpeakSettingsService {
        val _pb = MutableStateFlow(SpeakPlaybackVd(100, true, true, false, 0, null))
        val _adv = MutableStateFlow(AdvancedSpeakVd(true, false, false, false))
        override val playback: StateFlow<SpeakPlaybackVd> = _pb.asStateFlow()
        override val advanced: StateFlow<AdvancedSpeakVd> = _adv.asStateFlow()
        var speed: Int? = null; var chapter: Boolean? = null; var titles: Boolean? = null
        var footnotes: Boolean? = null
        var sync: Boolean? = null; var divine: Boolean? = null; var autoBm: Boolean? = null; var restore: Boolean? = null
        val sleepTimerWrites: MutableList<Int> = mutableListOf()
        val rangeWrites: MutableList<Pair<String, String>> = mutableListOf()
        var clearCount: Int = 0
        override fun setSpeed(percent: Int) { speed = percent }
        override fun setSpeakChapterChanges(on: Boolean) { chapter = on }
        override fun setSpeakTitles(on: Boolean) { titles = on }
        override fun setSpeakFootnotes(on: Boolean) { footnotes = on }
        override fun clearRepeatRange() { clearCount++ }
        override fun setSleepTimerMinutes(minutes: Int) { sleepTimerWrites.add(minutes) }
        override fun setRepeatRange(startOsisId: String, endOsisId: String) { rangeWrites.add(startOsisId to endOsisId) }
        override fun setSynchronize(on: Boolean) { sync = on }
        override fun setReplaceDivineName(on: Boolean) { divine = on }
        override fun setAutoBookmark(on: Boolean) { autoBm = on }
        override fun setRestoreSettingsFromBookmarks(on: Boolean) { restore = on }
    }

    @Test fun playback_setters_pass_through() {
        val fake = FakeSpeakSettingsService()
        val c = BibleSpeakSettingsController(fake)
        c.setSpeed(175); c.setSpeakChapterChanges(false); c.setSpeakTitles(false); c.setSpeakFootnotes(true)
        assertEquals(175, fake.speed)
        assertEquals(false, fake.chapter); assertEquals(false, fake.titles); assertEquals(true, fake.footnotes)
        assertSame(fake.playback, c.playback)
    }

    @Test fun sleep_timer_minutes_go_straight_to_the_service() {
        val service = FakeSpeakSettingsService()
        val c = BibleSpeakSettingsController(service)
        c.setSleepTimerMinutes(30)
        assertEquals(listOf(30), service.sleepTimerWrites)
        c.setSleepTimerMinutes(0)
        assertEquals(listOf(30, 0), service.sleepTimerWrites)
    }

    @Test fun repeat_range_is_written_as_a_pair_of_osis_ids() {
        val service = FakeSpeakSettingsService()
        val c = BibleSpeakSettingsController(service)
        c.setRepeatRange("Ps.23.1", "Ps.23.6")
        assertEquals(listOf("Ps.23.1" to "Ps.23.6"), service.rangeWrites)
    }

    @Test fun clearing_the_range_uses_the_existing_clear_seam() {
        val service = FakeSpeakSettingsService()
        BibleSpeakSettingsController(service).clearRepeatRange()
        assertEquals(1, service.clearCount)
    }

    @Test fun advanced_setters_pass_through() {
        val fake = FakeSpeakSettingsService()
        val c = AdvancedSpeakSettingsController(fake)
        c.setSynchronize(false); c.setReplaceDivineName(true); c.setAutoBookmark(true); c.setRestoreSettingsFromBookmarks(true)
        assertEquals(false, fake.sync); assertEquals(true, fake.divine); assertEquals(true, fake.autoBm); assertEquals(true, fake.restore)
        assertSame(fake.advanced, c.advanced)
    }
}
