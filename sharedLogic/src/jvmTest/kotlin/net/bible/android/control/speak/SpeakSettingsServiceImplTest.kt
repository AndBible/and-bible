package net.bible.android.control.speak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.sharedcore.platform.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakSettingsServiceImplTest {
    private class MemorySpeakStore : SpeakSettingsStore {
        var json: String? = null
        override fun load() = json
        override fun save(json: String) { this.json = json }
    }

    private class MemoryAppSettings : AppSettings {
        private val booleans = mutableMapOf<String, Boolean>()
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = booleans[key] ?: default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) { if (value == null) booleans.remove(key) else booleans[key] = value }
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private val main = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "speak-settings-service-main").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val speakStore = MemorySpeakStore()
    private val appSettings = MemoryAppSettings()
    private val originalStore = runCatching { SpeakSettings.store }.getOrNull()
    private val originalSettings = SpeakSettings.currentSettings

    private fun service() = SpeakSettingsServiceImpl(AdvancedSpeakSettings(appSettings))

    @AfterTest
    fun restoreGlobals() {
        try {
            originalStore?.let { SpeakSettings.store = it }
            SpeakSettings.currentSettings = originalSettings
        } finally {
            Dispatchers.resetMain()
            main.close()
        }
    }

    private fun installStore() {
        Dispatchers.setMain(main)
        SpeakSettings.store = speakStore
        SpeakSettings.currentSettings = null
    }

    @Test
    fun playbackPreferencesAreSavedAndReloadedByANewService() {
        installStore()
        val first = service()
        try {
            first.setSpeed(185)
            first.setSpeakChapterChanges(false)
            first.setSpeakTitles(false)
            first.setSpeakFootnotes(true)
        } finally {
            first.close()
        }

        SpeakSettings.currentSettings = null
        val reloaded = service()
        try {
            assertEquals(185, reloaded.playback.value.speedPercent)
            assertFalse(reloaded.playback.value.speakChapterChanges)
            assertFalse(reloaded.playback.value.speakTitles)
            assertTrue(reloaded.playback.value.speakFootnotes)
        } finally {
            reloaded.close()
        }
    }

    @Test
    fun advancedPreferencesAreSavedAndReloadedByANewService() {
        installStore()
        val first = service()
        try {
            first.setSynchronize(false)
            first.setReplaceDivineName(true)
            first.setAutoBookmark(true)
            first.setRestoreSettingsFromBookmarks(true)
        } finally {
            first.close()
        }

        val reloaded = service()
        try {
            assertFalse(reloaded.advanced.value.synchronize)
            assertTrue(reloaded.advanced.value.replaceDivineName)
            assertTrue(reloaded.advanced.value.autoBookmark)
            assertTrue(reloaded.advanced.value.restoreSettingsFromBookmarks)
        } finally {
            reloaded.close()
        }
    }
}
