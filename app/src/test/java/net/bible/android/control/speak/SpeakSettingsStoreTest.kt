package net.bible.android.control.speak

import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = net.bible.android.TestBibleApplication::class, sdk = [net.bible.android.TEST_SDK])
class SpeakSettingsStoreTest {
    private class MemoryStore(var json: String? = null) : SpeakSettingsStore {
        var writes = 0
        override fun load() = json
        override fun save(json: String) { this.json = json; writes++ }
    }

    private fun withStore(json: String? = null, block: (MemoryStore) -> Unit) {
        val previousStore = SpeakSettings.store
        val previousSettings = SpeakSettings.currentSettings
        try {
            val store = MemoryStore(json)
            SpeakSettings.store = store
            SpeakSettings.currentSettings = null
            block(store)
        } finally {
            SpeakSettings.store = previousStore
            SpeakSettings.currentSettings = previousSettings
        }
    }

    @Test fun saveRoundTripsThroughTheStoreWithoutTheCache() = withStore { store ->
        val settings = SpeakSettings(PlaybackSettings(speed = 135, speakFootnotes = true), sleepTimer = 7, repeat = true, queue = false)
        settings.save()
        assertEquals(1, store.writes)
        assertEquals(settings, SpeakSettings.fromJson(store.json!!))
        SpeakSettings.currentSettings = null
        assertEquals(settings, SpeakSettings.load())
    }

    @Test fun emptyStoreLoadsDefaults() = withStore { assertEquals(SpeakSettings(), SpeakSettings.load()) }

    @Test fun corruptStoreLoadsDefaults() = withStore("{not json") { assertEquals(SpeakSettings(), SpeakSettings.load()) }

    @Test fun unchangedSettingsDoNotWriteAgain() = withStore { store ->
        val settings = SpeakSettings(sleepTimer = 12)
        settings.save()
        settings.makeCopy().save()
        assertEquals(1, store.writes)
    }

    @Test fun cachedSettingsAreCopiedInsteadOfReadingTheStore() = withStore("{not json") {
        val cached = SpeakSettings(PlaybackSettings(speed = 123))
        SpeakSettings.currentSettings = cached
        val loaded = SpeakSettings.load()
        assertEquals(cached, loaded)
        loaded.playbackSettings.speed = 140
        assertEquals(123, cached.playbackSettings.speed)
    }
}
